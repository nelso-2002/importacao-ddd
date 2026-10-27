package io.github.nelso2002.importacao.processo.domain;

import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.DeclaracaoRegistrada;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ItemAdicionado;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ItemRemovido;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ProcessoAberto;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ProcessoCancelado;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ProcessoDesembaracado;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static io.github.nelso2002.importacao.processo.domain.StatusProcesso.ABERTO;
import static io.github.nelso2002.importacao.processo.domain.StatusProcesso.CANCELADO;
import static io.github.nelso2002.importacao.processo.domain.StatusProcesso.DESEMBARACADO;
import static io.github.nelso2002.importacao.processo.domain.StatusProcesso.REGISTRADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

class ProcessoImportacaoTest {

    private static final Cnpj IMPORTADOR = new Cnpj("11222333000181");
    private static final Ncm NCM = new Ncm("90308410");
    private static final Dinheiro CEM_DOLARES = new Dinheiro("100.00", "USD");

    // Monta um processo em cada status pelo caminho real, chamando os métodos de negócio.
    private static ProcessoImportacao emStatus(StatusProcesso status) {
        var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-2026/0001");
        processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);
        switch (status) {
            case ABERTO -> { }
            case REGISTRADO -> processo.registrarDeclaracao();
            case DESEMBARACADO -> {
                processo.registrarDeclaracao();
                processo.desembaracar();
            }
            case CANCELADO -> processo.cancelar("desistência do importador");
        }
        processo.retirarEventos(); // cada teste olha só os eventos da ação que ele executa
        return processo;
    }

    @Nested
    class Abertura {

        @Test
        void nasceAbertoSemItens() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-2026/0001");

            assertThat(processo.status()).isEqualTo(ABERTO);
            assertThat(processo.itens()).isEmpty();
            assertThat(processo.importador()).isEqualTo(IMPORTADOR);
            assertThat(processo.referencia()).isEqualTo("IMP-2026/0001");
        }

        @Test
        void registraEventoProcessoAberto() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-2026/0001");

            assertThat(processo.retirarEventos())
                    .containsExactly(new ProcessoAberto(processo.id(), IMPORTADOR, "IMP-2026/0001"));
        }

        @Test
        void cadaProcessoTemIdProprio() {
            var primeiro = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");
            var segundo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");

            assertThat(primeiro.id()).isNotEqualTo(segundo.id());
        }

        @Test
        void removeEspacosDaReferencia() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "  IMP-2026/0001 ");

            assertThat(processo.referencia()).isEqualTo("IMP-2026/0001");
        }

        @Test
        void rejeitaImportadorNulo() {
            assertThatThrownBy(() -> ProcessoImportacao.abrir(null, "IMP-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("importador");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void rejeitaReferenciaVazia(String referencia) {
            assertThatThrownBy(() -> ProcessoImportacao.abrir(IMPORTADOR, referencia))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("referência");
        }
    }

    @Nested
    class Itens {

        @Test
        void numeraItensEmSequencia() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");

            var primeiro = processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);
            var segundo = processo.adicionarItem(NCM, BigDecimal.TWO, CEM_DOLARES);

            assertThat(primeiro.numero()).isEqualTo(1);
            assertThat(segundo.numero()).isEqualTo(2);
            assertThat(processo.itens()).containsExactly(primeiro, segundo);
        }

        @Test
        void permiteMesmoNcmEmItensDiferentes() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");

            processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);
            processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);

            assertThat(processo.itens()).hasSize(2);
        }

        @Test
        void permiteMoedasDiferentesEntreItens() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");

            processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);
            processo.adicionarItem(NCM, BigDecimal.ONE, new Dinheiro("80.00", "EUR"));

            assertThat(processo.itens()).hasSize(2);
        }

        @Test
        void registraEventoItemAdicionado() {
            var processo = emStatus(ABERTO);

            var item = processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);

            assertThat(processo.retirarEventos()).containsExactly(new ItemAdicionado(processo.id(), item));
        }

        @Test
        void naoReaproveitaNumeroDeItemRemovido() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");
            processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);
            processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);

            processo.removerItem(2);
            var novo = processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);

            assertThat(novo.numero()).isEqualTo(3);
        }

        @Test
        void itemInvalidoNaoConsomeNumero() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");

            assertThatThrownBy(() -> processo.adicionarItem(NCM, BigDecimal.ZERO, CEM_DOLARES))
                    .isInstanceOf(IllegalArgumentException.class);
            var valido = processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);

            assertThat(valido.numero()).isEqualTo(1);
            assertThat(processo.retirarEventos()).hasSize(2); // ProcessoAberto + um ItemAdicionado
        }

        @Test
        void removeItemERegistraEvento() {
            var processo = emStatus(ABERTO);

            processo.removerItem(1);

            assertThat(processo.itens()).isEmpty();
            assertThat(processo.retirarEventos()).containsExactly(new ItemRemovido(processo.id(), 1));
        }

        @Test
        void rejeitaRemoverItemInexistente() {
            var processo = emStatus(ABERTO);

            assertThatThrownBy(() -> processo.removerItem(99))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Item 99 não existe");
            assertThat(processo.retirarEventos()).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = StatusProcesso.class, names = "ABERTO", mode = EXCLUDE)
        void naoAdicionaItemForaDeAberto(StatusProcesso status) {
            var processo = emStatus(status);

            assertThatThrownBy(() -> processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("adicionar item com o processo " + status);
            assertThat(processo.itens()).hasSize(1);
        }

        @ParameterizedTest
        @EnumSource(value = StatusProcesso.class, names = "ABERTO", mode = EXCLUDE)
        void naoRemoveItemForaDeAberto(StatusProcesso status) {
            var processo = emStatus(status);

            assertThatThrownBy(() -> processo.removerItem(1))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("remover item com o processo " + status);
            assertThat(processo.itens()).hasSize(1);
        }

        @Test
        void listaDeItensNaoAlteraOProcesso() {
            var processo = emStatus(ABERTO);
            var itens = processo.itens();

            assertThatThrownBy(() -> itens.clear()).isInstanceOf(UnsupportedOperationException.class);
            assertThat(processo.itens()).hasSize(1);
        }
    }

    @Nested
    class RegistroDaDeclaracao {

        @Test
        void registraProcessoComItens() {
            var processo = emStatus(ABERTO);

            processo.registrarDeclaracao();

            assertThat(processo.status()).isEqualTo(REGISTRADO);
            assertThat(processo.retirarEventos()).containsExactly(new DeclaracaoRegistrada(processo.id()));
        }

        @Test
        void rejeitaRegistrarSemItens() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");

            assertThatThrownBy(processo::registrarDeclaracao)
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("sem itens");
            assertThat(processo.status()).isEqualTo(ABERTO);
        }

        @Test
        void processoQueFicouSemItensNaoPodeSerRegistrado() {
            var processo = emStatus(ABERTO);
            processo.removerItem(1);

            assertThatThrownBy(processo::registrarDeclaracao)
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("sem itens");
        }

        @ParameterizedTest
        @EnumSource(value = StatusProcesso.class, names = "ABERTO", mode = EXCLUDE)
        void rejeitaRegistrarForaDeAberto(StatusProcesso status) {
            var processo = emStatus(status);

            assertThatThrownBy(processo::registrarDeclaracao)
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Transição inválida");
            assertThat(processo.status()).isEqualTo(status);
        }

        @Test
        void processoCanceladoSemItensReclamaDoStatusEnaoDosItens() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");
            processo.cancelar("aberto por engano");

            assertThatThrownBy(processo::registrarDeclaracao)
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Transição inválida: CANCELADO -> REGISTRADO");
        }
    }

    @Nested
    class Desembaraco {

        @Test
        void desembaracaProcessoRegistrado() {
            var processo = emStatus(REGISTRADO);

            processo.desembaracar();

            assertThat(processo.status()).isEqualTo(DESEMBARACADO);
            assertThat(processo.retirarEventos()).containsExactly(new ProcessoDesembaracado(processo.id()));
        }

        @ParameterizedTest
        @EnumSource(value = StatusProcesso.class, names = "REGISTRADO", mode = EXCLUDE)
        void rejeitaDesembaracarForaDeRegistrado(StatusProcesso status) {
            var processo = emStatus(status);

            assertThatThrownBy(processo::desembaracar)
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Transição inválida");
            assertThat(processo.status()).isEqualTo(status);
        }
    }

    @Nested
    class Cancelamento {

        @ParameterizedTest
        @EnumSource(value = StatusProcesso.class, names = {"ABERTO", "REGISTRADO"})
        void cancelaProcessoAbertoOuRegistrado(StatusProcesso status) {
            var processo = emStatus(status);

            processo.cancelar(" mercadoria avariada ");

            assertThat(processo.status()).isEqualTo(CANCELADO);
            assertThat(processo.motivoCancelamento()).isEqualTo("mercadoria avariada");
            assertThat(processo.retirarEventos())
                    .containsExactly(new ProcessoCancelado(processo.id(), "mercadoria avariada"));
        }

        @ParameterizedTest
        @EnumSource(value = StatusProcesso.class, names = {"DESEMBARACADO", "CANCELADO"})
        void rejeitaCancelarProcessoFinalizado(StatusProcesso status) {
            var processo = emStatus(status);

            assertThatThrownBy(() -> processo.cancelar("tarde demais"))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("Transição inválida");
            assertThat(processo.status()).isEqualTo(status);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void rejeitaCancelarSemMotivo(String motivo) {
            var processo = emStatus(ABERTO);

            assertThatThrownBy(() -> processo.cancelar(motivo))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("motivo");
            assertThat(processo.status()).isEqualTo(ABERTO);
        }
    }

    @Nested
    class Eventos {

        @Test
        void cicloCompletoGeraEventosNaOrdem() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");
            var item = processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);
            processo.registrarDeclaracao();
            processo.desembaracar();

            assertThat(processo.retirarEventos()).containsExactly(
                    new ProcessoAberto(processo.id(), IMPORTADOR, "IMP-1"),
                    new ItemAdicionado(processo.id(), item),
                    new DeclaracaoRegistrada(processo.id()),
                    new ProcessoDesembaracado(processo.id()));
        }

        @Test
        void retirarEventosEsvaziaALista() {
            var processo = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");

            processo.retirarEventos();

            assertThat(processo.retirarEventos()).isEmpty();
        }

        @Test
        void operacaoRejeitadaNaoGeraEvento() {
            var processo = emStatus(REGISTRADO);

            assertThatThrownBy(() -> processo.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES))
                    .isInstanceOf(RegraDeNegocioException.class);

            assertThat(processo.retirarEventos()).isEmpty();
        }
    }

    @Nested
    class Reconstituicao {

        @Test
        void reaplicaOHistoricoEChegaAoMesmoEstado() {
            var original = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");
            original.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);
            original.adicionarItem(NCM, BigDecimal.TWO, CEM_DOLARES);
            original.removerItem(1);
            original.cancelar("desistência do importador");
            var historico = original.retirarEventos();

            var reconstituido = ProcessoImportacao.reconstituir(historico);

            assertThat(reconstituido.id()).isEqualTo(original.id());
            assertThat(reconstituido.importador()).isEqualTo(IMPORTADOR);
            assertThat(reconstituido.referencia()).isEqualTo("IMP-1");
            assertThat(reconstituido.status()).isEqualTo(CANCELADO);
            assertThat(reconstituido.motivoCancelamento()).isEqualTo("desistência do importador");
            assertThat(reconstituido.itens()).isEqualTo(original.itens());
            assertThat(reconstituido.versao()).isEqualTo(5);
        }

        @Test
        void reconstituirNaoGeraEventosNovos() {
            var original = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");

            var reconstituido = ProcessoImportacao.reconstituir(original.retirarEventos());

            assertThat(reconstituido.retirarEventos()).isEmpty();
        }

        @Test
        void numeroDeItemRemovidoNaoVoltaDepoisDeReconstituir() {
            var original = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");
            original.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);
            original.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);
            original.removerItem(2);
            var reconstituido = ProcessoImportacao.reconstituir(original.retirarEventos());

            var novo = reconstituido.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);

            // o contador vem do ItemAdicionado(2), que continua no histórico mesmo depois da remoção
            assertThat(novo.numero()).isEqualTo(3);
        }

        @Test
        void regrasValemDepoisDeReconstituir() {
            var original = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");
            original.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);
            original.registrarDeclaracao();
            var reconstituido = ProcessoImportacao.reconstituir(original.retirarEventos());

            assertThatThrownBy(() -> reconstituido.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES))
                    .isInstanceOf(RegraDeNegocioException.class);
        }

        @Test
        void versaoContaOsEventosGravadosEOsNovos() {
            var original = ProcessoImportacao.abrir(IMPORTADOR, "IMP-1");
            original.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);
            assertThat(original.versao()).isEqualTo(2);
            var reconstituido = ProcessoImportacao.reconstituir(original.retirarEventos());

            reconstituido.adicionarItem(NCM, BigDecimal.ONE, CEM_DOLARES);

            assertThat(reconstituido.versao()).isEqualTo(3);
            assertThat(reconstituido.retirarEventos()).hasSize(1);
        }

        @Test
        void rejeitaHistoricoQueNaoComecaComAbertura() {
            var semAbertura = List.<ProcessoEvento>of(
                    new ItemAdicionado(ProcessoId.novo(), new ItemProcesso(1, NCM, BigDecimal.ONE, CEM_DOLARES)));

            assertThatThrownBy(() -> ProcessoImportacao.reconstituir(semAbertura))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Histórico inválido");
            assertThatThrownBy(() -> ProcessoImportacao.reconstituir(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Histórico inválido");
        }
    }
}
