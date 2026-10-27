package io.github.nelso2002.importacao.processo.api;

import io.github.nelso2002.importacao.processo.TestcontainersConfiguration;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.ProcessoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Histórico de ponta a ponta: comandos pela API, leitura direto do event store. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HistoricoApiTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ProcessoRepository repositorio;

    @Autowired
    private TransactionTemplate transacao;

    private String abrirProcesso(String referencia) {
        var resposta = mvc.post().uri("/processos").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"importador": "11222333000181", "referencia": "%s"}
                        """.formatted(referencia))
                .exchange();
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return resposta.getResponse().getHeader("Location").replaceAll(".*/processos/", "");
    }

    @Test
    void historicoDevolveOsEventosEmOrdem() {
        var id = abrirProcesso("IMP-HISTORICO");
        assertThat(mvc.post().uri("/processos/{id}/itens", id).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"ncm": "90308410", "quantidade": 1, "valor": 10, "moeda": "USD"}
                        """))
                .hasStatus(HttpStatus.CREATED);
        // cancelar ainda não tem endpoint; usa o agregado direto, como faria um caso de uso
        transacao.executeWithoutResult(_ -> {
            var processo = repositorio.buscarPorId(ProcessoId.de(id)).orElseThrow();
            processo.cancelar("teste");
            repositorio.salvar(processo);
        });

        var resposta = mvc.get().uri("/processos/{id}/historico", id).exchange();

        assertThat(resposta).hasStatus(HttpStatus.OK);
        assertThat(resposta).bodyJson().extractingPath("$[*].tipo")
                .isEqualTo(List.of("ProcessoAberto", "ItemAdicionado", "ProcessoCancelado"));
        assertThat(resposta).bodyJson().extractingPath("$[*].versao").isEqualTo(List.of(1, 2, 3));
        assertThat(resposta).bodyJson().extractingPath("$[0].dados.referencia").isEqualTo("IMP-HISTORICO");
        assertThat(resposta).bodyJson().extractingPath("$[0].ocorridoEm").isNotNull();
    }

    @Test
    void processoInexistenteDevolve404() {
        assertThat(mvc.get().uri("/processos/{id}/historico", ProcessoId.novo().valor()).exchange())
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void idMalformadoDevolve400() {
        assertThat(mvc.get().uri("/processos/{id}/historico", "abc").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listaEDetalheNaoSaoMaisDesteServico() {
        // agora em GET /consultas/processos, no consulta-service
        assertThat(mvc.get().uri("/processos").exchange()).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
    }
}
