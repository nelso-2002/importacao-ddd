package io.github.nelso2002.importacao.processo.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public record Dinheiro(BigDecimal valor, Currency moeda) {

    public Dinheiro {
        if (valor == null || moeda == null) {
            throw new IllegalArgumentException("Dinheiro inválido: " + valor + " " + moeda);
        }
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("Dinheiro inválido: valor negativo " + valor);
        }
        var casas = moeda.getDefaultFractionDigits();
        if (casas < 0) {
            throw new IllegalArgumentException("Dinheiro inválido: moeda sem casas decimais definidas " + moeda);
        }
        // Normaliza a escala (10.5 -> 10.50) para que equals funcione; UNNECESSARY recusa arredondar.
        try {
            valor = valor.setScale(casas, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException _) {
            throw new IllegalArgumentException("Dinheiro inválido: " + valor + " tem mais casas decimais que " + moeda);
        }
    }

    // Construtor flexível (Java 25): converte e valida antes de chamar this(...).
    public Dinheiro(String valor, String moeda) {
        BigDecimal numero;
        Currency currency;
        try {
            numero = new BigDecimal(valor);
            currency = Currency.getInstance(moeda);
        } catch (NullPointerException | IllegalArgumentException _) {
            throw new IllegalArgumentException("Dinheiro inválido: " + valor + " " + moeda);
        }
        this(numero, currency);
    }

    public Dinheiro somar(Dinheiro outro) {
        if (!moeda.equals(outro.moeda)) {
            throw new IllegalArgumentException("Dinheiro: moedas diferentes " + moeda + " e " + outro.moeda);
        }
        return new Dinheiro(valor.add(outro.valor), moeda);
    }
}
