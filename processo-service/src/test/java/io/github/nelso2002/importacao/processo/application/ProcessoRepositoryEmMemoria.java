package io.github.nelso2002.importacao.processo.application;

import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.ProcessoImportacao;
import io.github.nelso2002.importacao.processo.domain.ProcessoRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dublê de teste da porta: deixa os testes dos casos de uso sem banco e sem Spring.
 * Guarda a própria instância do agregado, então não detecta um caso de uso que esqueça de chamar salvar().
 */
class ProcessoRepositoryEmMemoria implements ProcessoRepository {

    private final Map<ProcessoId, ProcessoImportacao> processos = new ConcurrentHashMap<>();

    @Override
    public void salvar(ProcessoImportacao processo) {
        processos.put(processo.id(), processo);
    }

    @Override
    public Optional<ProcessoImportacao> buscarPorId(ProcessoId id) {
        return Optional.ofNullable(processos.get(id));
    }
}
