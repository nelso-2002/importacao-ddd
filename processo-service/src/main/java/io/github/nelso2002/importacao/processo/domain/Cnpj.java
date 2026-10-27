package io.github.nelso2002.importacao.processo.domain;

/**
 * CNPJ numérico ou alfanumérico (Receita Federal, a partir de julho/2026):
 * 12 posições de [0-9A-Z] seguidas de 2 dígitos verificadores numéricos, sem máscara.
 */
public record Cnpj(String numero) {

    public Cnpj {
        if (numero == null
                || !numero.matches("[0-9A-Z]{12}[0-9]{2}")
                || numero.chars().distinct().count() == 1
                || !digitosVerificadoresConferem(numero)) {
            throw new IllegalArgumentException("CNPJ inválido: " + numero);
        }
    }

    private static boolean digitosVerificadoresConferem(String numero) {
        var base = numero.substring(0, 12);
        var primeiro = calcularDigito(base);
        var segundo = calcularDigito(base + primeiro);
        return numero.endsWith("" + primeiro + segundo);
    }

    // Módulo 11 com pesos 2..9 da direita para a esquerda; cada caractere vale (código ASCII - 48),
    // então '0'..'9' continuam valendo 0..9 e 'A' vale 17, 'B' 18, ...
    private static int calcularDigito(String texto) {
        var soma = 0;
        var peso = 2;
        for (var i = texto.length() - 1; i >= 0; i--) {
            soma += (texto.charAt(i) - '0') * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        var resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
