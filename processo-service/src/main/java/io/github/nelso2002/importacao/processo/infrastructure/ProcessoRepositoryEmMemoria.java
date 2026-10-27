package io.github.nelso2002.importacao.processo.infrastructure;

import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.ProcessoImportacao;
import io.github.nelso2002.importacao.processo.domain.ProcessoRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adaptador provisório até o JPA do Passo 7: os dados somem quando a aplicação para.
 * Guarda a própria instância do agregado, então não detecta um caso de uso que esqueça de chamar salvar().
 */
@Repository
public class ProcessoRepositoryEmMemoria implements ProcessoRepository {

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
