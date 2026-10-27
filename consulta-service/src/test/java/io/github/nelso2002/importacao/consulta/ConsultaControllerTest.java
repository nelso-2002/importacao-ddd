package io.github.nelso2002.importacao.consulta;

import io.github.nelso2002.importacao.consulta.ConsultaProcessos.ProcessoResumo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HTTP de ponta a ponta com Postgres real: as linhas da projeção são inseridas direto no banco,
 * e o stream é alimentado por mensagens publicadas no Kafka, como faria o processo-service.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@Import(TestcontainersConfiguration.class)
class ConsultaControllerTest {

    @Autowired
    private WebTestClient http;

    @Autowired
    private DatabaseClient db;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @BeforeEach
    void limpar() {
        db.sql("delete from processo_resumo").then().block();
    }

    private UUID inserir(String referencia, String status, String itens, String atualizadoHa) {
        var id = UUID.randomUUID();
        db.sql("""
                        insert into processo_resumo (id, importador, referencia, status, itens, atualizado_em)
                        values (:id, '11222333000181', :referencia, :status, cast(:itens as jsonb), now() - cast(:ha as interval))
                        """)
                .bind("id", id).bind("referencia", referencia).bind("status", status)
                .bind("itens", itens).bind("ha", atualizadoHa)
                .then().block();
        return id;
    }

    @Test
    void listaDoMaisRecenteParaOMaisAntigo() {
        inserir("IMP-ANTIGO", "ABERTO", "[]", "2 hours");
        inserir("IMP-RECENTE", "ABERTO", "[]", "1 minute");

        http.get().uri("/consultas/processos").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[*].referencia").isEqualTo(List.of("IMP-RECENTE", "IMP-ANTIGO"));
    }

    @Test
    void filtraPorStatus() {
        inserir("IMP-ABERTO", "ABERTO", "[]", "1 minute");
        inserir("IMP-CANCELADO", "CANCELADO", "[]", "1 minute");

        http.get().uri("/consultas/processos?status=CANCELADO").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].referencia").isEqualTo("IMP-CANCELADO");
    }

    @Test
    void buscaPorTrechoDaReferenciaSemDiferenciarMaiusculas() {
        inserir("IMP-2026/0042", "ABERTO", "[]", "1 minute");
        inserir("IMP-2026/0099", "ABERTO", "[]", "1 minute");

        http.get().uri("/consultas/processos?busca=0042").exchange()
                .expectBody().jsonPath("$[*].referencia").isEqualTo(List.of("IMP-2026/0042"));
        http.get().uri("/consultas/processos?busca=imp-2026").exchange()
                .expectBody().jsonPath("$.length()").isEqualTo(2);
    }

    @Test
    void detalheTrazOsItens() {
        var id = inserir("IMP-DETALHE", "ABERTO", """
                [{"numero": 1, "ncm": "90308410", "quantidade": 2.5, "valor": 100.00, "moeda": "USD"}]
                """, "1 minute");

        http.get().uri("/consultas/processos/{id}", id).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.referencia").isEqualTo("IMP-DETALHE")
                .jsonPath("$.itens[0].ncm").isEqualTo("90308410")
                .jsonPath("$.itens[0].valor").isEqualTo(100.00);
    }

    @Test
    void detalheInexistenteDevolve404ComProblemDetail() {
        http.get().uri("/consultas/processos/{id}", UUID.randomUUID()).exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.detail").value(detalhe -> ((String) detalhe).startsWith("Processo não encontrado"));
    }

    @Test
    void idMalformadoDevolve400() {
        http.get().uri("/consultas/processos/{id}", "abc").exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void streamEmiteAMudancaAssimQueEleAparece() {
        var eventos = http.get().uri("/consultas/processos/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(new ParameterizedTypeReference<ServerSentEvent<ProcessoResumo>>() {})
                .getResponseBody()
                .filter(evento -> evento.data() != null);   // ignora os comentários de keep-alive

        var id = UUID.randomUUID();
        var mensagem = """
                {"aggregateId": "%s", "versao": 1, "tipo": "ProcessoAberto", "ocorridoEm": "2026-10-27T10:00:00Z",
                 "dados": {"importador": {"numero": "11222333000181"}, "referencia": "IMP-AO-VIVO"}}
                """.formatted(id);

        // assina o stream, só então publica no Kafka; o consumidor projeta e repassa ao SSE
        StepVerifier.create(eventos)
                .then(() -> kafka.send("processo-eventos", id.toString(), mensagem))
                .assertNext(evento -> {
                    assertThat(evento.event()).isEqualTo("processo");
                    assertThat(evento.data().referencia()).isEqualTo("IMP-AO-VIVO");
                })
                .thenCancel()
                .verify(Duration.ofSeconds(10));
    }
}
