package io.github.nelso2002.importacao.processo.domain;

import java.util.Optional;

/**
 * Porta de saída: o domínio diz o que precisa ("guardar e achar processos")
 * e a infraestrutura decide como (memória, JPA, event store...).
 */
public interface ProcessoRepository {

    void salvar(ProcessoImportacao processo);

    Optional<ProcessoImportacao> buscarPorId(ProcessoId id);
}
