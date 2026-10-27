package io.github.nelso2002.importacao.consulta;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * StepVerifier: assina o Flux/Mono e verifica, em ordem, cada sinal emitido (onNext, onComplete, onError).
 * Sem ele, testar um Flux exigiria block() e perderia justamente o comportamento assíncrono.
 */
@SpringBootTest(properties = {"spring.sql.init.mode=always", "consulta.intervalo-polling=200ms"})
@Import(TestcontainersConfiguration.class)
class ConsultaProcessosTest {

    @Autowired
    private ConsultaProcessos consultas;

    @Autowired
    private MonitorDeMudancas monitor;

    @Autowired
    private DatabaseClient db;

    @BeforeEach
    void limpar() {
        db.sql("delete from processo_resumo").then().block();
    }

    private void inserir(String referencia, String status) {
        db.sql("""
                        insert into processo_resumo (id, importador, referencia, status, atualizado_em)
                        values (:id, '11222333000181', :referencia, :status, now())
                        """)
                .bind("id", UUID.randomUUID()).bind("referencia", referencia).bind("status", status)
                .then().block();
    }

    @Test
    void fluxEmiteCadaLinhaEDepoisCompleta() {
        inserir("IMP-1", "ABERTO");
        inserir("IMP-2", "REGISTRADO");

        StepVerifier.create(consultas.listar("REGISTRADO", null))
                .expectNextMatches(resumo -> resumo.referencia().equals("IMP-2") && resumo.quantidadeItens() == 0)
                .verifyComplete();
    }

    @Test
    void monoVazioQuandoNaoExiste() {
        StepVerifier.create(consultas.buscar(UUID.randomUUID()))
                .verifyComplete();   // nenhum onNext: o controller transforma o vazio em 404
    }

    @Test
    void alteradosDepoisDeIgnoraOQueJaFoiVisto() {
        inserir("IMP-VELHO", "ABERTO");
        var depoisDoVelho = OffsetDateTime.now().plusSeconds(1);

        StepVerifier.create(consultas.alteradosDepoisDe(depoisDoVelho))
                .verifyComplete();
    }

    @Test
    void doisAssinantesRecebemAMesmaMudanca() {
        var primeiro = monitor.mudancas().filter(resumo -> resumo.referencia().equals("IMP-MULTICAST"));
        var segundo = monitor.mudancas().filter(resumo -> resumo.referencia().equals("IMP-MULTICAST"));

        StepVerifier.create(primeiro.zipWith(segundo))
                .then(() -> inserir("IMP-MULTICAST", "ABERTO"))
                .expectNextMatches(par -> par.getT1().id().equals(par.getT2().id()))
                .thenCancel()
                .verify(Duration.ofSeconds(10));
    }
}
