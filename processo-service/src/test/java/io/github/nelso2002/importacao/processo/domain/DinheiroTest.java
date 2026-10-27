package io.github.nelso2002.importacao.processo.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DinheiroTest {

    private static final Currency BRL = Currency.getInstance("BRL");
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void normalizaParaAsCasasDecimaisDaMoeda() {
        var dinheiro = new Dinheiro(new BigDecimal("10.5"), BRL);

        assertThat(dinheiro.valor()).isEqualTo(new BigDecimal("10.50"));
    }

    @Test
    void valoresIguaisComEscalasDiferentesSaoIguais() {
        // sem a normalização, BigDecimal("10.5").equals(BigDecimal("10.50")) é false
        assertThat(new Dinheiro(new BigDecimal("10.5"), BRL))
                .isEqualTo(new Dinheiro(new BigDecimal("10.50"), BRL));
    }

    @Test
    void respeitaMoedaSemCentavos() {
        var iene = new Dinheiro("100.0", "JPY");

        assertThat(iene.valor()).isEqualTo(new BigDecimal("100"));
    }

    @Test
    void aceitaZero() {
        var zero = new Dinheiro("0", "BRL");

        assertThat(zero.valor()).isEqualTo(new BigDecimal("0.00"));
    }

    @Test
    void criaAPartirDeTexto() {
        assertThat(new Dinheiro("10.5", "BRL"))
                .isEqualTo(new Dinheiro(new BigDecimal("10.50"), BRL));
    }

    @ParameterizedTest
    @CsvSource({
            "10.555, BRL",   // BRL tem 2 casas: arredondar perderia dinheiro
            "100.5, JPY"     // JPY tem 0 casas
    })
    void rejeitaMaisCasasDecimaisQueAMoeda(String valor, String moeda) {
        assertThatThrownBy(() -> new Dinheiro(valor, moeda))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dinheiro inválido");
    }

    @Test
    void rejeitaValorNegativo() {
        assertThatThrownBy(() -> new Dinheiro("-0.01", "BRL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dinheiro inválido");
    }

    @Test
    void rejeitaMoedaSemCasasDecimaisDefinidas() {
        // XAU (ouro) é um código ISO válido, mas não tem casas decimais definidas
        assertThatThrownBy(() -> new Dinheiro("1", "XAU"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dinheiro inválido");
    }

    @Test
    void rejeitaNulosNoConstrutorCanonico() {
        assertThatThrownBy(() -> new Dinheiro(null, BRL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dinheiro inválido");
        assertThatThrownBy(() -> new Dinheiro(BigDecimal.TEN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dinheiro inválido");
    }

    @ParameterizedTest
    @CsvSource({
            "abc, BRL",      // valor não numérico
            "10, XYZ",       // moeda inexistente
            "10, brl",       // moeda em minúsculas (ISO 4217 é maiúsculo)
            ", BRL",         // valor nulo
            "10, "           // moeda nula
    })
    void rejeitaTextoInvalido(String valor, String moeda) {
        assertThatThrownBy(() -> new Dinheiro(valor, moeda))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dinheiro inválido");
    }

    @Test
    void somaValoresDaMesmaMoeda() {
        var total = new Dinheiro("10.50", "BRL").somar(new Dinheiro("0.25", "BRL"));

        assertThat(total).isEqualTo(new Dinheiro("10.75", "BRL"));
    }

    @Test
    void naoSomaMoedasDiferentes() {
        var reais = new Dinheiro("10", "BRL");
        var dolares = new Dinheiro("10", "USD");

        assertThatThrownBy(() -> reais.somar(dolares))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("moedas diferentes");
    }

    @Test
    void somarNaoAlteraOsOriginais() {
        var dez = new Dinheiro("10", "BRL");

        dez.somar(new Dinheiro("5", "BRL"));

        assertThat(dez).isEqualTo(new Dinheiro("10", "BRL"));
    }
}
