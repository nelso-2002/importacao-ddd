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
 * Cada método de negócio valida a regra, muda o estado e registra um evento.
 */
public class ProcessoImportacao {

    private final ProcessoId id;
    private final Cnpj importador;
    private final String referencia;
    private final List<ItemProcesso> itens = new ArrayList<>();
    private StatusProcesso status = ABERTO;
    private int ultimoNumeroItem;
    private String motivoCancelamento;

    private final List<ProcessoEvento> eventos = new ArrayList<>();

    private ProcessoImportacao(ProcessoId id, Cnpj importador, String referencia) {
        this.id = id;
        this.importador = importador;
        this.referencia = referencia;
    }

    public static ProcessoImportacao abrir(Cnpj importador, String referencia) {
        if (importador == null) {
            throw new IllegalArgumentException("Processo inválido: importador é obrigatório");
        }
        if (referencia == null || referencia.isBlank()) {
            throw new IllegalArgumentException("Processo inválido: referência é obrigatória");
        }
        var processo = new ProcessoImportacao(ProcessoId.novo(), importador, referencia.strip());
        processo.eventos.add(new ProcessoAberto(processo.id, importador, processo.referencia));
        return processo;
    }

    /**
     * Recria um processo a partir do que foi persistido. Não valida regras nem gera eventos:
     * os dados já passaram pelas regras quando foram gravados.
     */
    public static ProcessoImportacao reconstituir(ProcessoId id, Cnpj importador, String referencia,
                                                  StatusProcesso status, List<ItemProcesso> itens,
                                                  int ultimoNumeroItem, String motivoCancelamento) {
        var processo = new ProcessoImportacao(id, importador, referencia);
        processo.status = status;
        processo.itens.addAll(itens);
        processo.ultimoNumeroItem = ultimoNumeroItem;
        processo.motivoCancelamento = motivoCancelamento;
        return processo;
    }

    public ItemProcesso adicionarItem(Ncm ncm, BigDecimal quantidade, Dinheiro valorTotal) {
        exigirAberto("adicionar item");
        // cria (e valida) antes de avançar o contador: item inválido não consome número
        var item = new ItemProcesso(ultimoNumeroItem + 1, ncm, quantidade, valorTotal);
        ultimoNumeroItem = item.numero();
        itens.add(item);
        eventos.add(new ItemAdicionado(id, item));
        return item;
    }

    public void removerItem(int numero) {
        exigirAberto("remover item");
        if (!itens.removeIf(item -> item.numero() == numero)) {
            throw new RegraDeNegocioException("Item " + numero + " não existe no processo " + id.valor());
        }
        eventos.add(new ItemRemovido(id, numero));
    }

    public void registrarDeclaracao() {
        // a transição é checada primeiro: num processo CANCELADO o erro relevante é o status
        var novoStatus = status.transicionarPara(REGISTRADO);
        if (itens.isEmpty()) {
            throw new RegraDeNegocioException("Não é possível registrar a declaração sem itens");
        }
        status = novoStatus;
        eventos.add(new DeclaracaoRegistrada(id));
    }

    public void desembaracar() {
        status = status.transicionarPara(DESEMBARACADO);
        eventos.add(new ProcessoDesembaracado(id));
    }

    public void cancelar(String motivo) {
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Cancelamento inválido: motivo é obrigatório");
        }
        status = status.transicionarPara(CANCELADO);
        motivoCancelamento = motivo.strip();
        eventos.add(new ProcessoCancelado(id, motivoCancelamento));
    }

    /** Devolve os eventos registrados desde a última chamada e esvazia a lista. */
    public List<ProcessoEvento> retirarEventos() {
        var pendentes = List.copyOf(eventos);
        eventos.clear();
        return pendentes;
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

    /** Precisa ser persistido: derivar do maior número atual reaproveitaria números removidos. */
    public int ultimoNumeroItem() {
        return ultimoNumeroItem;
    }
}
