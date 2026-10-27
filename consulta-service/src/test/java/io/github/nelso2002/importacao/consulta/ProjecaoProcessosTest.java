package io.github.nelso2002.importacao.consulta;

import io.github.nelso2002.importacao.consulta.ProjecaoProcessos.EventoRecebido;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Projeção a partir dos eventos: pelo tópico (ponta a ponta) e chamando projetar() direto (idempotência). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProjecaoProcessosTest {

    @Autowired
    private ProjecaoProcessos projecao;

    @Autowired
    private ConsultaProcessos consultas;

    @Autowired
    private KafkaTemplate<String, String> kafka;

    @Autowired
    private JsonMapper json;

    // Mensagens no mesmo formato que o PublicadorOutbox do processo-service publica.
    private static String aberto(UUID id, String referencia) {
        return """
                {"aggregateId": "%s", "versao": 1, "tipo": "ProcessoAberto", "ocorridoEm": "2026-10-27T10:00:00Z",
                 "dados": {"importador": {"numero": "11222333000181"}, "referencia": "%s"}}
                """.formatted(id, referencia);
    }

    private static String itemAdicionado(UUID id, int versao, int numero) {
        return """
                {"aggregateId": "%s", "versao": %d, "tipo": "ItemAdicionado", "ocorridoEm": "2026-10-27T10:0%d:00Z",
                 "dados": {"item": {"numero": %d, "ncm": {"codigo": "90308410"}, "quantidade": 2.50000,
                                    "valorTotal": {"valor": 100.00, "moeda": "USD"}}}}
                """.formatted(id, versao, versao, numero);
    }

    private static String cancelado(UUID id, int versao) {
        return """
                {"aggregateId": "%s", "versao": %d, "tipo": "ProcessoCancelado", "ocorridoEm": "2026-10-27T11:00:00Z",
                 "dados": {"motivo": "desistência"}}
                """.formatted(id, versao);
    }

    private long projetar(String mensagem) {
        return projecao.projetar(json.readValue(mensagem, EventoRecebido.class)).block();
    }

    @Test
    void consumidorProjetaOsEventosDoTopico() {
        var id = UUID.randomUUID();

        kafka.send("processo-eventos", id.toString(), aberto(id, "IMP-KAFKA"));
        kafka.send("processo-eventos", id.toString(), itemAdicionado(id, 2, 1));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var detalhe = consultas.buscar(id).block();
            assertThat(detalhe).isNotNull();
            assertThat(detalhe.referencia()).isEqualTo("IMP-KAFKA");
            assertThat(detalhe.itens()).hasSize(1);
            // a data vem do evento, não do relógio do consumidor
            assertThat(detalhe.atualizadoEm()).isEqualTo(OffsetDateTime.parse("2026-10-27T10:02:00Z"));
        });
    }

    @Test
    void mensagemRepetidaNaoAplicaDuasVezes() {
        var id = UUID.randomUUID();
        projetar(aberto(id, "IMP-REPETIDA"));

        assertThat(projetar(itemAdicionado(id, 2, 1))).isEqualTo(1);
        assertThat(projetar(itemAdicionado(id, 2, 1))).isZero();   // o outbox é at-least-once: chega de novo
        assertThat(projetar(aberto(id, "IMP-REPETIDA"))).isZero();  // abertura repetida também não duplica

        assertThat(consultas.buscar(id).block().itens()).hasSize(1);
    }

    @Test
    void eventoAntigoNaoDesfazOEstadoAtual() {
        var id = UUID.randomUUID();
        projetar(aberto(id, "IMP-ANTIGO"));
        projetar(itemAdicionado(id, 2, 1));
        projetar(cancelado(id, 3));

        // reentrega de um evento anterior (ex.: offset reprocessado) chega depois do cancelamento
        assertThat(projetar(itemAdicionado(id, 2, 1))).isZero();

        var detalhe = consultas.buscar(id).block();
        assertThat(detalhe.status()).isEqualTo("CANCELADO");
        assertThat(detalhe.itens()).hasSize(1);
    }

    @Test
    void tipoDesconhecidoEIgnoradoSemDerrubarOConsumidor() {
        var id = UUID.randomUUID();
        var novo = """
                {"aggregateId": "%s", "versao": 2, "tipo": "ProcessoSuspenso", "ocorridoEm": "2026-10-27T10:00:00Z", "dados": {}}
                """.formatted(id);

        assertThat(projetar(novo)).isZero();
    }
}
