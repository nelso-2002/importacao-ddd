package io.github.nelso2002.importacao.processo.domain;

public enum StatusProcesso {

    ABERTO,
    REGISTRADO,
    DESEMBARACADO,
    CANCELADO;

    public StatusProcesso transicionarPara(StatusProcesso destino) {
        // sem default: um status novo sem regra aqui não compila
        var permitida = switch (this) {
            case ABERTO -> destino == REGISTRADO || destino == CANCELADO;
            case REGISTRADO -> destino == DESEMBARACADO || destino == CANCELADO;
            case DESEMBARACADO, CANCELADO -> false;
        };
        if (!permitida) {
            throw new RegraDeNegocioException("Transição inválida: " + this + " -> " + destino);
        }
        return destino;
    }
}
