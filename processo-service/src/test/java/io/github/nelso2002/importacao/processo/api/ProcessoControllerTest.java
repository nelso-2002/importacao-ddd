package io.github.nelso2002.importacao.processo.api;

import io.github.nelso2002.importacao.processo.application.AbrirProcesso;
import io.github.nelso2002.importacao.processo.application.AdicionarItem;
import io.github.nelso2002.importacao.processo.application.ProcessoNaoEncontradoException;
import io.github.nelso2002.importacao.processo.domain.Cnpj;
import io.github.nelso2002.importacao.processo.domain.Dinheiro;
import io.github.nelso2002.importacao.processo.domain.ItemProcesso;
import io.github.nelso2002.importacao.processo.domain.Ncm;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.RegraDeNegocioException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;

import static io.github.nelso2002.importacao.processo.api.Tokens.consulta;
import static io.github.nelso2002.importacao.processo.api.Tokens.operador;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Só a camada web: o controller, o ApiExceptionHandler e o Jackson, com os casos de uso trocados por mocks.
 * Sem banco, sem Kafka, sem Docker; testa a tradução HTTP ↔ comandos e exceções ↔ status.
 * Os testes de ponta a ponta (ProcessoApiTest, HistoricoApiTest) continuam cobrindo a integração real.
 */
@WebMvcTest(ProcessoController.class)
@Import(SegurancaConfig.class)
class ProcessoControllerTest {

    private static final ProcessoId ID = ProcessoId.de("123e4567-e89b-12d3-a456-426614174000");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AbrirProcesso abrirProcesso;

    @MockitoBean
    private AdicionarItem adicionarItem;

    // nunca é chamado (o jwt() dos testes já entrega o token decodificado), mas a cadeia de segurança precisa dele
    @MockitoBean
    private JwtDecoder jwtDecoder;

    private MockMvcTester.MockMvcRequestBuilder post(String uri, String json) {
        return mvc.post().with(operador()).uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    // Segurança: quem não tem token não entra; quem tem o papel errado entra, mas não pode.

    @Test
    void semTokenDevolve401SemChamarOCasoDeUso() {
        var resposta = mvc.post().uri("/processos").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"importador": "11222333000181", "referencia": "IMP-1"}
                        """)
                .exchange();

        assertThat(resposta).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(resposta.getResponse().getHeader("WWW-Authenticate")).startsWith("Bearer");
        verifyNoInteractions(abrirProcesso);
    }

    @Test
    void papelConsultaNaoPodeEnviarComandos() {
        var resposta = mvc.post().with(consulta()).uri("/processos").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"importador": "11222333000181", "referencia": "IMP-1"}
                        """)
                .exchange();

        assertThat(resposta).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(abrirProcesso);
    }

    @Test
    void tokenSemPapeisDoKeycloakNaoPodeEnviarComandos() {
        // autenticado, mas sem realm_access.roles: a conversão não dá papel nenhum
        var resposta = mvc.post().with(Tokens.usuario("sem-papel")).uri("/processos/" + ID.valor() + "/itens")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"ncm": "90308410", "quantidade": 1, "valor": 10, "moeda": "USD"}
                        """)
                .exchange();

        assertThat(resposta).hasStatus(HttpStatus.FORBIDDEN);
        verifyNoInteractions(adicionarItem);
    }

    // CORS: antes de um POST com Authorization, o navegador pergunta (preflight OPTIONS, sem token) se pode.

    @Test
    void preflightDaTelaEAutorizado() {
        var resposta = mvc.options().uri("/processos")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .exchange();

        assertThat(resposta).hasStatus(HttpStatus.OK);
        assertThat(resposta.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:4200");
        assertThat(resposta.getResponse().getHeader("Access-Control-Allow-Methods")).contains("POST");
    }

    @Test
    void preflightDeOutroSiteERecusado() {
        var resposta = mvc.options().uri("/processos")
                .header("Origin", "http://site-malicioso.test")
                .header("Access-Control-Request-Method", "POST")
                .exchange();

        assertThat(resposta).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(resposta.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @Test
    void abreProcessoComOsValueObjectsConvertidosNaBorda() {
        given(abrirProcesso.executar(any())).willReturn(ID);

        var resposta = post("/processos", """
                {"importador": "12ABC34501DE35", "referencia": "IMP-2026/0001"}
                """).exchange();

        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        assertThat(resposta.getResponse().getHeader("Location")).endsWith("/processos/" + ID.valor());
        assertThat(resposta).bodyJson().extractingPath("$.id").isEqualTo(ID.valor().toString());
        var comando = ArgumentCaptor.forClass(AbrirProcesso.Comando.class);
        then(abrirProcesso).should().executar(comando.capture());
        assertThat(comando.getValue()).isEqualTo(new AbrirProcesso.Comando(new Cnpj("12ABC34501DE35"), "IMP-2026/0001"));
    }

    @Test
    void cnpjInvalidoParaNaBordaSemChamarOCasoDeUso() {
        var resposta = post("/processos", """
                {"importador": "11222333000182", "referencia": "IMP-1"}
                """).exchange();

        assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resposta).bodyJson().extractingPath("$.title").isEqualTo("Dado inválido");
        verifyNoInteractions(abrirProcesso);
    }

    @Test
    void campoAusenteListaOsCamposSemChamarOCasoDeUso() {
        var resposta = post("/processos", """
                {"importador": "11222333000181"}
                """).exchange();

        assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(resposta).bodyJson().extractingPath("$.erros.referencia").isNotNull();
        verifyNoInteractions(abrirProcesso);
    }

    @Test
    void adicionaItemConvertendoNcmEDinheiro() {
        var item = new ItemProcesso(1, new Ncm("90308410"), BigDecimal.ONE, new Dinheiro("100.00", "USD"));
        given(adicionarItem.executar(any())).willReturn(item);

        var resposta = post("/processos/" + ID.valor() + "/itens", """
                {"ncm": "90308410", "quantidade": 1, "valor": 100.00, "moeda": "USD"}
                """).exchange();

        assertThat(resposta).hasStatus(HttpStatus.CREATED);
        assertThat(resposta.getResponse().getHeader("Location")).endsWith("/processos/" + ID.valor() + "/itens/1");
        var comando = ArgumentCaptor.forClass(AdicionarItem.Comando.class);
        then(adicionarItem).should().executar(comando.capture());
        assertThat(comando.getValue().processoId()).isEqualTo(ID);
        assertThat(comando.getValue().ncm()).isEqualTo(new Ncm("90308410"));
        assertThat(comando.getValue().valorTotal()).isEqualTo(new Dinheiro("100.00", "USD"));
    }

    @Test
    void idMalformadoNaUrlParaNaBorda() {
        var resposta = post("/processos/1-1-1-1-1/itens", """
                {"ncm": "90308410", "quantidade": 1, "valor": 10, "moeda": "USD"}
                """).exchange();

        assertThat(resposta).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(adicionarItem);
    }

    // As três exceções que o caso de uso pode lançar, cada uma com o seu status e título.

    @Test
    void processoNaoEncontradoVira404() {
        willThrow(new ProcessoNaoEncontradoException(ID)).given(adicionarItem).executar(any());

        var resposta = post("/processos/" + ID.valor() + "/itens", """
                {"ncm": "90308410", "quantidade": 1, "valor": 10, "moeda": "USD"}
                """).exchange();

        assertThat(resposta).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(resposta).bodyJson().extractingPath("$.title").isEqualTo("Processo não encontrado");
    }

    @Test
    void regraDeNegocioVira409() {
        willThrow(new RegraDeNegocioException("Não é possível adicionar item com o processo CANCELADO"))
                .given(adicionarItem).executar(any());

        var resposta = post("/processos/" + ID.valor() + "/itens", """
                {"ncm": "90308410", "quantidade": 1, "valor": 10, "moeda": "USD"}
                """).exchange();

        assertThat(resposta).hasStatus(HttpStatus.CONFLICT);
        assertThat(resposta).bodyJson().extractingPath("$.title").isEqualTo("Regra de negócio violada");
        assertThat(resposta).bodyJson().extractingPath("$.detail").asString().contains("CANCELADO");
    }

    @Test
    void gravacaoConcorrenteVira409ComTituloProprio() {
        willThrow(new OptimisticLockingFailureException("versão 2 já gravada")).given(adicionarItem).executar(any());

        var resposta = post("/processos/" + ID.valor() + "/itens", """
                {"ncm": "90308410", "quantidade": 1, "valor": 10, "moeda": "USD"}
                """).exchange();

        assertThat(resposta).hasStatus(HttpStatus.CONFLICT);
        assertThat(resposta).bodyJson().extractingPath("$.title").isEqualTo("Gravação concorrente");
    }
}
