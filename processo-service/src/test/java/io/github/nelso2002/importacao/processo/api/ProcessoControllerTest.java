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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;

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
class ProcessoControllerTest {

    private static final ProcessoId ID = ProcessoId.de("123e4567-e89b-12d3-a456-426614174000");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AbrirProcesso abrirProcesso;

    @MockitoBean
    private AdicionarItem adicionarItem;

    private MockMvcTester.MockMvcRequestBuilder post(String uri, String json) {
        return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json);
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
