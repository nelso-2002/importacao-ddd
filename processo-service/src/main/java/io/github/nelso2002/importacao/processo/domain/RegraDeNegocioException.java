package io.github.nelso2002.importacao.processo.domain;

/**
 * Operação válida no formato, mas proibida pelo estado atual do processo
 * (ex.: desembaraçar um processo ainda ABERTO).
 * Dado malformado continua sendo IllegalArgumentException, lançada pelos value objects.
 */
public class RegraDeNegocioException extends RuntimeException {

    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
