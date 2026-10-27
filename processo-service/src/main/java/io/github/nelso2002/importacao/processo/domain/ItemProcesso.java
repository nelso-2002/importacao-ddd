package io.github.nelso2002.importacao.processo.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Item de um processo. É uma entidade local ao agregado: o {@code numero} identifica o item
 * dentro do seu processo e é atribuído pelo ProcessoImportacao (sequencial, nunca reaproveitado).
 * Fora do agregado o item não tem significado, por isso não tem repositório próprio.
 */
public record ItemProcesso(int numero, Ncm ncm, BigDecimal quantidade, Dinheiro valorTotal) {

    // Mesma precisão da quantidade estatística no Siscomex; ajuste se a operação usar outra.
    private static final int CASAS_QUANTIDADE = 5;

    public ItemProcesso {
        if (numero < 1) {
            throw new IllegalArgumentException("Item inválido: número deve ser positivo, recebido " + numero);
        }
        if (ncm == null || quantidade == null || valorTotal == null) {
            throw new IllegalArgumentException("Item inválido: ncm, quantidade e valorTotal são obrigatórios");
        }
        if (quantidade.signum() <= 0) {
            throw new IllegalArgumentException("Item inválido: quantidade deve ser maior que zero, recebido " + quantidade);
        }
        // Mesmo motivo do Dinheiro: 10 e 10.0 precisam ser iguais no equals do record.
        try {
            quantidade = quantidade.setScale(CASAS_QUANTIDADE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException _) {
            throw new IllegalArgumentException("Item inválido: quantidade com mais de " + CASAS_QUANTIDADE + " casas decimais, recebido " + quantidade);
        }
    }
}
