package io.github.nelso2002.importacao.processo.application;

import io.github.nelso2002.importacao.processo.domain.Cnpj;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.ProcessoImportacao;
import io.github.nelso2002.importacao.processo.domain.ProcessoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AbrirProcesso {

    public record Comando(Cnpj importador, String referencia) {}

    private final ProcessoRepository repositorio;

    public AbrirProcesso(ProcessoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public ProcessoId executar(Comando comando) {
        var processo = ProcessoImportacao.abrir(comando.importador(), comando.referencia());
        repositorio.salvar(processo);
        return processo.id();
    }
}
