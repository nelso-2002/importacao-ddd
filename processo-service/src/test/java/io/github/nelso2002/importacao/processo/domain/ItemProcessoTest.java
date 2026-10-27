package io.github.nelso2002.importacao.processo.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemProcessoTest {

    private static final Ncm NCM = new Ncm("90308410");
    private static final Dinheiro CEM_DOLARES = new Dinheiro("100.00", "USD");

    @Test
    void criaItemValido() {
        var item = new ItemProcesso(1, NCM, new BigDecimal("2.5"), CEM_DOLARES);

        assertThat(item.numero()).isEqualTo(1);
        assertThat(item.ncm()).isEqualTo(NCM);
        assertThat(item.quantidade()).isEqualTo(new BigDecimal("2.50000"));
        assertThat(item.valorTotal()).isEqualTo(CEM_DOLARES);
    }

    @Test
    void quantidadesIguaisComEscalasDiferentesGeramItensIguais() {
        var comInteiro = new ItemProcesso(1, NCM, new BigDecimal("10"), CEM_DOLARES);
        var comDecimal = new ItemProcesso(1, NCM, new BigDecimal("10.0"), CEM_DOLARES);

        assertThat(comInteiro).isEqualTo(comDecimal);
    }

    @Test
    void aceitaValorZero() {
        // importação sem cobertura cambial (amostra, doação) tem valor zero
        var item = new ItemProcesso(1, NCM, BigDecimal.ONE, new Dinheiro("0", "USD"));

        assertThat(item.valorTotal().valor()).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejeitaNumeroNaoPositivo(int numero) {
        assertThatThrownBy(() -> new ItemProcesso(numero, NCM, BigDecimal.ONE, CEM_DOLARES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item inválido");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0",          // zero
            "-1",         // negativa
            "1.000001"    // 6 casas decimais
    })
    void rejeitaQuantidadeInvalida(String quantidade) {
        assertThatThrownBy(() -> new ItemProcesso(1, NCM, new BigDecimal(quantidade), CEM_DOLARES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item inválido");
    }

    @Test
    void rejeitaCamposObrigatoriosNulos() {
        assertThatThrownBy(() -> new ItemProcesso(1, null, BigDecimal.ONE, CEM_DOLARES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item inválido");
        assertThatThrownBy(() -> new ItemProcesso(1, NCM, null, CEM_DOLARES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item inválido");
        assertThatThrownBy(() -> new ItemProcesso(1, NCM, BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Item inválido");
    }
}
