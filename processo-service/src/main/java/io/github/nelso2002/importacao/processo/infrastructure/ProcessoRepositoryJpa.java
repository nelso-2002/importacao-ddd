package io.github.nelso2002.importacao.processo.infrastructure;

import io.github.nelso2002.importacao.processo.domain.Cnpj;
import io.github.nelso2002.importacao.processo.domain.Dinheiro;
import io.github.nelso2002.importacao.processo.domain.ItemProcesso;
import io.github.nelso2002.importacao.processo.domain.Ncm;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.ProcessoImportacao;
import io.github.nelso2002.importacao.processo.domain.ProcessoRepository;
import io.github.nelso2002.importacao.processo.infrastructure.ProcessoEntity.ItemEntity;
import org.springframework.stereotype.Repository;

import java.util.Currency;
import java.util.Optional;

/** Adaptador da porta ProcessoRepository com JPA: converte entre o agregado e ProcessoEntity. */
@Repository
public class ProcessoRepositoryJpa implements ProcessoRepository {

    private final ProcessoJpaRepository jpa;

    public ProcessoRepositoryJpa(ProcessoJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(ProcessoImportacao processo) {
        // Dentro da transação do caso de uso, findById devolve a mesma entidade carregada em buscarPorId,
        // com a versao lida naquele momento; é isso que faz o @Version detectar gravações concorrentes.
        var entity = jpa.findById(processo.id().valor())
                .orElseGet(() -> new ProcessoEntity(processo.id().valor()));
        entity.importador = processo.importador().numero();
        entity.referencia = processo.referencia();
        entity.status = processo.status();
        entity.ultimoNumeroItem = processo.ultimoNumeroItem();
        entity.motivoCancelamento = processo.motivoCancelamento();
        entity.itens.clear();
        processo.itens().forEach(item -> entity.itens.add(new ItemEntity(
                item.numero(),
                item.ncm().codigo(),
                item.quantidade(),
                item.valorTotal().valor(),
                item.valorTotal().moeda().getCurrencyCode())));
        jpa.save(entity);
    }

    @Override
    public Optional<ProcessoImportacao> buscarPorId(ProcessoId id) {
        return jpa.findById(id.valor()).map(ProcessoRepositoryJpa::paraDominio);
    }

    private static ProcessoImportacao paraDominio(ProcessoEntity entity) {
        var itens = entity.itens.stream()
                .map(item -> new ItemProcesso(
                        item.numero,
                        new Ncm(item.ncm),
                        item.quantidade,
                        new Dinheiro(item.valor, Currency.getInstance(item.moeda))))
                .toList();
        return ProcessoImportacao.reconstituir(
                new ProcessoId(entity.id),
                new Cnpj(entity.importador),
                entity.referencia,
                entity.status,
                itens,
                entity.ultimoNumeroItem,
                entity.motivoCancelamento);
    }
}
