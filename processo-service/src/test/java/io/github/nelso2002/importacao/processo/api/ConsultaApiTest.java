package io.github.nelso2002.importacao.processo.api;

import io.github.nelso2002.importacao.processo.TestcontainersConfiguration;
import io.github.nelso2002.importacao.processo.domain.Dinheiro;
import io.github.nelso2002.importacao.processo.domain.Ncm;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.ProcessoImportacao;
import io.github.nelso2002.importacao.processo.domain.ProcessoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lado de leitura de ponta a ponta: comandos pela API, leitura pela projeção e pelo event store. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConsultaApiTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ProcessoRepository repositorio;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private JdbcClient jdbc;

    private String abrirProcesso(String referencia) {
        var resposta = mvc.post().uri("/processos").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"importador": "11222333000181", "referencia": "%s"}
                        """.formatted(referencia))
                .exchange();
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return resposta.getResponse().getHeader("Location").replaceAll(".*/processos/", "");
    }

    private void adicionarItem(String id, String ncm, String valor) {
        assertThat(mvc.post().uri("/processos/{id}/itens", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"ncm": "%s", "quantidade": 1, "valor": %s, "moeda": "USD"}
                        """.formatted(ncm, valor)))
                .hasStatus(HttpStatus.CREATED);
    }

    // remover e cancelar ainda não têm endpoint; usa o agregado direto, como faria um caso de uso
    private void alterar(String id, Consumer<ProcessoImportacao> alteracao) {
        transacao.executeWithoutResult(_ -> {
            var processo = repositorio.buscarPorId(ProcessoId.de(id)).orElseThrow();
            alteracao.accept(processo);
            repositorio.salvar(processo);
        });
    }

    @Test
    void detalheRefleteOsComandosLogoAposOPost() {
        var id = abrirProcesso("IMP-DETALHE");
        adicionarItem(id, "90308410", "100.00");
        adicionarItem(id, "84713012", "50.00");

        var resposta = mvc.get().uri("/processos/{id}", id).exchange();

        assertThat(resposta).hasStatus(HttpStatus.OK);
        assertThat(resposta).bodyJson().isLenientlyEqualTo("""
                {"id": "%s", "referencia": "IMP-DETALHE", "importador": "11222333000181", "status": "ABERTO",
                 "itens": [{"numero": 1, "ncm": "90308410", "valor": 100.00, "moeda": "USD"},
                           {"numero": 2, "ncm": "84713012", "valor": 50.00, "moeda": "USD"}]}
                """.formatted(id));
    }

    @Test
    void listaTrazOResumoComQuantidadeDeItens() {
        var id = abrirProcesso("IMP-LISTA");
        adicionarItem(id, "90308410", "10");

        var resposta = mvc.get().uri("/processos").exchange();

        assertThat(resposta).hasStatus(HttpStatus.OK);
        assertThat(resposta).bodyJson().extractingPath("$[?(@.id == '%s')].quantidadeItens".formatted(id))
                .isEqualTo(List.of(1));
    }

    @Test
    void remocaoECancelamentoChegamAProjecao() {
        var id = abrirProcesso("IMP-CANCELADO");
        adicionarItem(id, "90308410", "10");
        adicionarItem(id, "84713012", "20");
        adicionarItem(id, "85176277", "30");
        alterar(id, processo -> processo.removerItem(2));
        alterar(id, processo -> processo.cancelar("desistência do importador"));

        var resposta = mvc.get().uri("/processos/{id}", id).exchange();

        assertThat(resposta).bodyJson().extractingPath("$.status").isEqualTo("CANCELADO");
        assertThat(resposta).bodyJson().extractingPath("$.motivoCancelamento").isEqualTo("desistência do importador");
        assertThat(resposta).bodyJson().extractingPath("$.itens[*].numero").isEqualTo(List.of(1, 3));
    }

    @Test
    void historicoDevolveOsEventosEmOrdem() {
        var id = abrirProcesso("IMP-HISTORICO");
        adicionarItem(id, "90308410", "10");
        alterar(id, processo -> processo.cancelar("teste"));

        var resposta = mvc.get().uri("/processos/{id}/historico", id).exchange();

        assertThat(resposta).hasStatus(HttpStatus.OK);
        assertThat(resposta).bodyJson().extractingPath("$[*].tipo")
                .isEqualTo(List.of("ProcessoAberto", "ItemAdicionado", "ProcessoCancelado"));
        assertThat(resposta).bodyJson().extractingPath("$[*].versao").isEqualTo(List.of(1, 2, 3));
        assertThat(resposta).bodyJson().extractingPath("$[0].dados.referencia").isEqualTo("IMP-HISTORICO");
        assertThat(resposta).bodyJson().extractingPath("$[0].ocorridoEm").isNotNull();
    }

    @Test
    void comandoRecusadoNaoMexeNaProjecao() {
        var id = abrirProcesso("IMP-RECUSADO");
        alterar(id, processo -> processo.cancelar("teste"));
        var antes = jdbc.sql("select atualizado_em from processo_resumo where id = ?::uuid").param(id).query(String.class).single();

        adicionarItem409(id);

        var depois = jdbc.sql("select atualizado_em from processo_resumo where id = ?::uuid").param(id).query(String.class).single();
        assertThat(depois).isEqualTo(antes);
    }

    private void adicionarItem409(String id) {
        assertThat(mvc.post().uri("/processos/{id}/itens", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"ncm": "90308410", "quantidade": 1, "valor": 10, "moeda": "USD"}
                        """))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void falhaNaProjecaoDesfazOEventoGravado() {
        var id = abrirProcesso("IMP-ROLLBACK");
        // sabota a projeção deste processo: o UPDATE do ItemAdicionado vai falhar
        jdbc.sql("alter table processo_resumo add constraint sabotagem check (id <> '%s'::uuid or jsonb_array_length(itens) = 0)".formatted(id)).update();
        try {
            assertThatThrownBy(() -> transacao.executeWithoutResult(_ -> {
                var processo = repositorio.buscarPorId(ProcessoId.de(id)).orElseThrow();
                processo.adicionarItem(new Ncm("90308410"), BigDecimal.ONE, new Dinheiro("10", "USD"));
                repositorio.salvar(processo);
            })).isInstanceOf(RuntimeException.class);
        } finally {
            jdbc.sql("alter table processo_resumo drop constraint sabotagem").update();
        }

        // mesma transação: o evento não ficou gravado sem a projeção correspondente
        var eventos = jdbc.sql("select count(*) from event_store where aggregate_id = ?::uuid").param(id).query(Integer.class).single();
        assertThat(eventos).isEqualTo(1);
    }

    @Test
    void processoInexistenteDevolve404NoDetalheENoHistorico() {
        var id = ProcessoId.novo().valor();

        assertThat(mvc.get().uri("/processos/{id}", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/processos/{id}/historico", id).exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void idMalformadoDevolve400() {
        assertThat(mvc.get().uri("/processos/{id}", "abc").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
