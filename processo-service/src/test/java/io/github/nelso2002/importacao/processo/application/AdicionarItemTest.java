package io.github.nelso2002.importacao.processo.application;

import io.github.nelso2002.importacao.processo.domain.Cnpj;
import io.github.nelso2002.importacao.processo.domain.Dinheiro;
import io.github.nelso2002.importacao.processo.domain.Ncm;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.ProcessoImportacao;
import io.github.nelso2002.importacao.processo.domain.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdicionarItemTest {

    private static final Ncm NCM = new Ncm("90308410");
    private static final Dinheiro CEM_DOLARES = new Dinheiro("100.00", "USD");

    private final ProcessoRepositoryEmMemoria repositorio = new ProcessoRepositoryEmMemoria();
    private final AdicionarItem adicionarItem = new AdicionarItem(repositorio);

    private ProcessoImportacao processoSalvo() {
        var processo = ProcessoImportacao.abrir(new Cnpj("11222333000181"), "IMP-1");
        repositorio.salvar(processo);
        return processo;
    }

    @Test
    void adicionaItemAoProcessoSalvo() {
        var processo = processoSalvo();

        var item = adicionarItem.executar(new AdicionarItem.Comando(processo.id(), NCM, BigDecimal.ONE, CEM_DOLARES));

        assertThat(item.numero()).isEqualTo(1);
        assertThat(repositorio.buscarPorId(processo.id()).orElseThrow().itens()).containsExactly(item);
    }

    @Test
    void rejeitaProcessoInexistente() {
        var idQualquer = ProcessoId.novo();

        assertThatThrownBy(() -> adicionarItem.executar(new AdicionarItem.Comando(idQualquer, NCM, BigDecimal.ONE, CEM_DOLARES)))
                .isInstanceOf(ProcessoNaoEncontradoException.class)
                .hasMessageContaining(idQualquer.valor().toString());
    }

    @Test
    void regraDoDominioChegaAteQuemChamou() {
        var processo = processoSalvo();
        processo.cancelar("aberto por engano");

        assertThatThrownBy(() -> adicionarItem.executar(new AdicionarItem.Comando(processo.id(), NCM, BigDecimal.ONE, CEM_DOLARES)))
                .isInstanceOf(RegraDeNegocioException.class);
    }
}
