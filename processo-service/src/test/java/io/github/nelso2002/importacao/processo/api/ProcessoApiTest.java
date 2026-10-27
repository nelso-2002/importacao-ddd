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

import static io.github.nelso2002.importacao.processo.api.Tokens.operador;
import static org.assertj.core.api.Assertions.assertThat;

/** A API de ponta a ponta: HTTP → controller → caso de uso → JPA → Postgres (Testcontainers). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProcessoApiTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ProcessoRepository repositorio;

    @Autowired
    private TransactionTemplate transacao;

    private String abrirProcesso() {
        var resposta = mvc.post().with(operador()).uri("/processos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"importador": "11222333000181", "referencia": "IMP-2026/0001"}
                        """)
                .exchange();
        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        return resposta.getResponse().getHeader("Location").replaceAll(".*/processos/", "");
    }

    private MockMvcTester.MockMvcRequestBuilder adicionarItem(String id, String json) {
        return mvc.post().with(operador()).uri("/processos/{id}/itens", id).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static final String ITEM_VALIDO = """
            {"ncm": "90308410", "quantidade": 2.5, "valor": 100.00, "moeda": "USD"}
            """;

    @Test
    void abreProcessoEDevolveLocation() {
        var resposta = mvc.post().with(operador()).uri("/processos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"importador": "12ABC34501DE35", "referencia": "IMP-2026/0001"}
                        """)
                .exchange();

        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        var id = resposta.getResponse().getHeader("Location").replaceAll(".*/processos/", "");
        assertThat(resposta).bodyJson().extractingPath("$.id").isEqualTo(id);
        assertThat(repositorio.buscarPorId(ProcessoId.de(id))).isPresent();
    }

    @Test
    void cnpjInvalidoDevolve400ComMensagemDoDominio() {
        var resposta = mvc.post().with(operador()).uri("/processos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"importador": "11222333000182", "referencia": "IMP-1"}
                        """)
                .exchange();

        assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resposta).bodyJson().extractingPath("$.detail").asString().contains("CNPJ inválido");
    }

    @Test
    void campoAusenteDevolve400ListandoOsCampos() {
        var resposta = mvc.post().with(operador()).uri("/processos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"importador": "11222333000181"}
                        """)
                .exchange();

        assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resposta).bodyJson().extractingPath("$.erros.referencia").isNotNull();
    }

    @Test
    void jsonMalformadoDevolve400() {
        var resposta = mvc.post().with(operador()).uri("/processos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"importador\": ")
                .exchange();

        assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void adicionaItemEDevolveLocationDoItem() {
        var id = abrirProcesso();

        var resposta = adicionarItem(id, ITEM_VALIDO).exchange();

        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        assertThat(resposta.getResponse().getHeader("Location")).endsWith("/processos/" + id + "/itens/1");
        assertThat(resposta).bodyJson().isLenientlyEqualTo("""
                {"numero": 1, "ncm": "90308410", "quantidade": 2.5, "valor": 100.00, "moeda": "USD"}
                """);
    }

    @Test
    void ncmComPontosDevolve400() {
        var id = abrirProcesso();

        var resposta = adicionarItem(id, """
                {"ncm": "9030.84.10", "quantidade": 1, "valor": 10, "moeda": "USD"}
                """).exchange();

        assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resposta).bodyJson().extractingPath("$.detail").asString().contains("ncm");
    }

    @Test
    void processoInexistenteDevolve404() {
        var resposta = adicionarItem(ProcessoId.novo().valor().toString(), ITEM_VALIDO).exchange();

        assertThat(resposta).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void idMalformadoNaUrlDevolve400() {
        var resposta = adicionarItem("1-1-1-1-1", ITEM_VALIDO).exchange();

        assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void itemEmProcessoCanceladoDevolve409() {
        var id = abrirProcesso();
        // ainda não existe endpoint de cancelamento; cancela direto pelo repositório
        transacao.executeWithoutResult(_ -> {
            var processo = repositorio.buscarPorId(ProcessoId.de(id)).orElseThrow();
            processo.cancelar("aberto por engano");
            repositorio.salvar(processo);
        });

        var resposta = adicionarItem(id, ITEM_VALIDO).exchange();

        assertThat(resposta).hasStatus(HttpStatus.CONFLICT);
        assertThat(resposta).bodyJson().extractingPath("$.title").isEqualTo("Regra de negócio violada");
    }
}
