package io.github.nelso2002.importacao.processo.application;

import io.github.nelso2002.importacao.processo.domain.Dinheiro;
import io.github.nelso2002.importacao.processo.domain.ItemProcesso;
import io.github.nelso2002.importacao.processo.domain.Ncm;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.ProcessoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AdicionarItem {

    public record Comando(ProcessoId processoId, Ncm ncm, BigDecimal quantidade, Dinheiro valorTotal) {}

    private final ProcessoRepository repositorio;

    public AdicionarItem(ProcessoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public ItemProcesso executar(Comando comando) {
        var processo = repositorio.buscarPorId(comando.processoId())
                .orElseThrow(() -> new ProcessoNaoEncontradoException(comando.processoId()));
        var item = processo.adicionarItem(comando.ncm(), comando.quantidade(), comando.valorTotal());
        repositorio.salvar(processo);
        return item;
    }
}
