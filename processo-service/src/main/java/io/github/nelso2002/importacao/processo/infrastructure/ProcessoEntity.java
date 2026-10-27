package io.github.nelso2002.importacao.processo.infrastructure;

import io.github.nelso2002.importacao.processo.domain.StatusProcesso;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Formato de gravação do processo. Só a infraestrutura conhece esta classe;
 * o domínio continua sem anotações e o adaptador converte entre os dois.
 */
@Entity
@Table(name = "processo")
class ProcessoEntity {

    @Id
    UUID id;

    String importador;

    String referencia;

    @Enumerated(EnumType.STRING)
    StatusProcesso status;

    int ultimoNumeroItem;

    String motivoCancelamento;

    // Controle de concorrência otimista: o UPDATE leva "where versao = ?" e falha se outra transação gravou antes.
    @Version
    Long versao;

    // EAGER: o agregado é sempre carregado inteiro, nunca o processo sem os itens.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "item_processo", joinColumns = @JoinColumn(name = "processo_id"))
    @OrderBy("numero")
    List<ItemEntity> itens = new ArrayList<>();

    protected ProcessoEntity() {
        // exigido pelo JPA
    }

    ProcessoEntity(UUID id) {
        this.id = id;
    }

    @Embeddable
    static class ItemEntity {

        int numero;

        String ncm;

        @Column(precision = 19, scale = 5)
        BigDecimal quantidade;

        @Column(precision = 19, scale = 4)
        BigDecimal valor;

        String moeda;

        protected ItemEntity() {
            // exigido pelo JPA
        }

        ItemEntity(int numero, String ncm, BigDecimal quantidade, BigDecimal valor, String moeda) {
            this.numero = numero;
            this.ncm = ncm;
            this.quantidade = quantidade;
            this.valor = valor;
            this.moeda = moeda;
        }
    }
}
