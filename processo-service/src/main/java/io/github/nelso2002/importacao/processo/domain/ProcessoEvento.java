package io.github.nelso2002.importacao.processo.domain;

/**
 * Fatos que já aconteceram com um processo (por isso os nomes no passado).
 * Sealed: a lista de eventos é fechada, e um switch sobre ProcessoEvento sem default
 * deixa de compilar quando surgir um evento novo sem tratamento.
 * Os records ficam aninhados aqui, então o compilador infere o "permits".
 */
public sealed interface ProcessoEvento {

    ProcessoId processoId();

    record ProcessoAberto(ProcessoId processoId, Cnpj importador, String referencia) implements ProcessoEvento {}

    record ItemAdicionado(ProcessoId processoId, ItemProcesso item) implements ProcessoEvento {}

    record ItemRemovido(ProcessoId processoId, int numeroItem) implements ProcessoEvento {}

    record DeclaracaoRegistrada(ProcessoId processoId) implements ProcessoEvento {}

    record ProcessoDesembaracado(ProcessoId processoId) implements ProcessoEvento {}

    record ProcessoCancelado(ProcessoId processoId, String motivo) implements ProcessoEvento {}
}
