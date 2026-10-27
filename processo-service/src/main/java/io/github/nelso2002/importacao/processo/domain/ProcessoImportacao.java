package io.github.nelso2002.importacao.processo.domain;

import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.DeclaracaoRegistrada;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ItemAdicionado;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ItemRemovido;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ProcessoAberto;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ProcessoCancelado;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ProcessoDesembaracado;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static io.github.nelso2002.importacao.processo.domain.StatusProcesso.ABERTO;
import static io.github.nelso2002.importacao.processo.domain.StatusProcesso.CANCELADO;
import static io.github.nelso2002.importacao.processo.domain.StatusProcesso.DESEMBARACADO;
import static io.github.nelso2002.importacao.processo.domain.StatusProcesso.REGISTRADO;

/**
 * Raiz do agregado: toda alteração no processo e nos seus itens passa por aqui.
 * Métodos de negócio só validam e registram eventos; quem muda o estado é aplicar(evento).
 * Assim o estado é sempre o resultado de reaplicar o histórico, seja um evento novo ou um gravado.
 */
public class ProcessoImportacao {

    private ProcessoId id;
    private Cnpj importador;
    private String referencia;
    private final List<ItemProcesso> itens = new ArrayList<>();
    private StatusProcesso status;
    private int ultimoNumeroItem;
    private String motivoCancelamento;

    // quantos eventos já foram aplicados (gravados + pendentes); o event store usa para detectar concorrência
    private int versao;

    private final List<ProcessoEvento> eventos = new ArrayList<>();

    private ProcessoImportacao() {
        // só via abrir() ou reconstituir()
    }

    public static ProcessoImportacao abrir(Cnpj importador, String referencia) {
        if (importador == null) {
            throw new IllegalArgumentException("Processo inválido: importador é obrigatório");
        }
        if (referencia == null || referencia.isBlank()) {
            throw new IllegalArgumentException("Processo inválido: referência é obrigatória");
        }
        var processo = new ProcessoImportacao();
        processo.registrar(new ProcessoAberto(ProcessoId.novo(), importador, referencia.strip()));
        return processo;
    }

    /** Recria o processo reaplicando o histórico gravado, sem gerar eventos novos. */
    public static ProcessoImportacao reconstituir(List<ProcessoEvento> historico) {
        if (historico == null || historico.isEmpty() || !(historico.getFirst() instanceof ProcessoAberto)) {
            throw new IllegalArgumentException("Histórico inválido: deve começar com ProcessoAberto");
        }
        var processo = new ProcessoImportacao();
        historico.forEach(processo::aplicar);
        return processo;
    }

    /**
     * Recria a partir do estado gravado pela persistência da Etapa 1 (tabelas processo/item_processo).
     * Sai junto com o adaptador JPA, quando o event store o substituir.
     */
    public static ProcessoImportacao reconstituir(ProcessoId id, Cnpj importador, String referencia,
                                                  StatusProcesso status, List<ItemProcesso> itens,
                                                  int ultimoNumeroItem, String motivoCancelamento) {
        var processo = new ProcessoImportacao();
        processo.id = id;
        processo.importador = importador;
        processo.referencia = referencia;
        processo.status = status;
        processo.itens.addAll(itens);
        processo.ultimoNumeroItem = ultimoNumeroItem;
        processo.motivoCancelamento = motivoCancelamento;
        return processo;
    }

    public ItemProcesso adicionarItem(Ncm ncm, BigDecimal quantidade, Dinheiro valorTotal) {
        exigirAberto("adicionar item");
        // cria (e valida) antes de registrar: item inválido não vira evento nem consome número
        var item = new ItemProcesso(ultimoNumeroItem + 1, ncm, quantidade, valorTotal);
        registrar(new ItemAdicionado(id, item));
        return item;
    }

    public void removerItem(int numero) {
        exigirAberto("remover item");
        if (itens.stream().noneMatch(item -> item.numero() == numero)) {
            throw new RegraDeNegocioException("Item " + numero + " não existe no processo " + id.valor());
        }
        registrar(new ItemRemovido(id, numero));
    }

    public void registrarDeclaracao() {
        // a transição é checada primeiro: num processo CANCELADO o erro relevante é o status
        status.transicionarPara(REGISTRADO);
        if (itens.isEmpty()) {
            throw new RegraDeNegocioException("Não é possível registrar a declaração sem itens");
        }
        registrar(new DeclaracaoRegistrada(id));
    }

    public void desembaracar() {
        status.transicionarPara(DESEMBARACADO);
        registrar(new ProcessoDesembaracado(id));
    }

    public void cancelar(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Cancelamento inválido: motivo é obrigatório");
        }
        status.transicionarPara(CANCELADO);
        registrar(new ProcessoCancelado(id, motivo.strip()));
    }

    /** Devolve os eventos registrados desde a última chamada e esvazia a lista. */
    public List<ProcessoEvento> retirarEventos() {
        var pendentes = List.copyOf(eventos);
        eventos.clear();
        return pendentes;
    }

    private void registrar(ProcessoEvento evento) {
        aplicar(evento);
        eventos.add(evento);
    }

    // Único lugar que altera o estado. Não valida nada: um evento é um fato que já aconteceu.
    // Sem default: um evento novo na sealed interface sem um case aqui não compila.
    private void aplicar(ProcessoEvento evento) {
        switch (evento) {
            case ProcessoAberto(var processoId, var cnpj, var ref) -> {
                id = processoId;
                importador = cnpj;
                referencia = ref;
                status = ABERTO;
            }
            case ItemAdicionado(_, var item) -> {
                itens.add(item);
                ultimoNumeroItem = item.numero();
            }
            case ItemRemovido(_, var numero) -> itens.removeIf(item -> item.numero() == numero);
            case DeclaracaoRegistrada _ -> status = REGISTRADO;
            case ProcessoDesembaracado _ -> status = DESEMBARACADO;
            case ProcessoCancelado(_, var motivo) -> {
                status = CANCELADO;
                motivoCancelamento = motivo;
            }
        }
        versao++;
    }

    private void exigirAberto(String acao) {
        if (status != ABERTO) {
            throw new RegraDeNegocioException("Não é possível " + acao + " com o processo " + status);
        }
    }

    public ProcessoId id() {
        return id;
    }

    public Cnpj importador() {
        return importador;
    }

    public String referencia() {
        return referencia;
    }

    public StatusProcesso status() {
        return status;
    }

    /** Cópia imutável: alterar itens só pelos métodos do processo. */
    public List<ItemProcesso> itens() {
        return List.copyOf(itens);
    }

    public String motivoCancelamento() {
        return motivoCancelamento;
    }

    /** Usado pela persistência da Etapa 1; no event store o contador é derivado dos eventos. */
    public int ultimoNumeroItem() {
        return ultimoNumeroItem;
    }

    /** Número de eventos aplicados até agora, incluindo os ainda não retirados. */
    public int versao() {
        return versao;
    }
}
