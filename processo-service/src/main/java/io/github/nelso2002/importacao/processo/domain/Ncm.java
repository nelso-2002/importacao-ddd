package io.github.nelso2002.importacao.processo.domain;

public record Ncm(String codigo) {

    public Ncm {
        if (codigo == null || !codigo.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("código da ncm inválido: " + codigo);
        }
    }
}
