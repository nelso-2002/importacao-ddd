package io.github.nelso2002.importacao.processo.application;

import io.github.nelso2002.importacao.processo.domain.ProcessoId;

/** O id é válido, mas não existe processo com ele (vira 404 na API). */
public class ProcessoNaoEncontradoException extends RuntimeException {

    public ProcessoNaoEncontradoException(ProcessoId id) {
        super("Processo não encontrado: " + id.valor());
    }
}
