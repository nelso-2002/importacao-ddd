package io.github.nelso2002.importacao.processo.infrastructure;

import io.github.nelso2002.importacao.processo.TestcontainersConfiguration;
import io.github.nelso2002.importacao.processo.application.AbrirProcesso;
import io.github.nelso2002.importacao.processo.application.AdicionarItem;
import io.github.nelso2002.importacao.processo.domain.Cnpj;
import io.github.nelso2002.importacao.processo.domain.Dinheiro;
import io.github.nelso2002.importacao.processo.domain.Ncm;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.ProcessoRepository;
import io.github.nelso2002.importacao.processo.domain.StatusProcesso;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integração com Postgres real (Testcontainers): Flyway cria o schema e o Hibernate o valida.
 * Cada leitura acontece numa transação nova, então os dados vêm mesmo do banco, não da memória.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProcessoRepositoryJpaTest {

    private static final Cnpj IMPORTADOR = new Cnpj("11222333000181");
    private static final Ncm NCM = new Ncm("90308410");

    @Autowired
    private AbrirProcesso abrirProcesso;

    @Autowired
    private AdicionarItem adicionarItem;

    @Autowired
    private ProcessoRepository repositorio;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ProcessoId abrir() {
        return abrirProcesso.executar(new AbrirProcesso.Comando(IMPORTADOR, "IMP-2026/0001"));
    }

    private void adicionar(ProcessoId id, String quantidade, String valor, String moeda) {
        adicionarItem.executar(new AdicionarItem.Comando(id, NCM, new BigDecimal(quantidade), new Dinheiro(valor, moeda)));
    }

    @Test
    void gravaERecuperaProcessoComItens() {
        var id = abrir();
        adicionar(id, "2.5", "100.00", "USD");
        adicionar(id, "1", "80", "EUR");

        var recuperado = repositorio.buscarPorId(id).orElseThrow();

        assertThat(recuperado.importador()).isEqualTo(IMPORTADOR);
        assertThat(recuperado.referencia()).isEqualTo("IMP-2026/0001");
        assertThat(recuperado.status()).isEqualTo(StatusProcesso.ABERTO);
        assertThat(recuperado.itens()).hasSize(2);
        // o banco guarda valor com 4 casas; o Dinheiro normaliza de volta e o equals funciona
        var primeiro = recuperado.itens().getFirst();
        assertThat(primeiro.numero()).isEqualTo(1);
        assertThat(primeiro.quantidade()).isEqualTo(new BigDecimal("2.50000"));
        assertThat(primeiro.valorTotal()).isEqualTo(new Dinheiro("100.00", "USD"));
        assertThat(recuperado.itens().getLast().valorTotal()).isEqualTo(new Dinheiro("80.00", "EUR"));
    }

    @Test
    void buscarIdInexistenteDevolveVazio() {
        assertThat(repositorio.buscarPorId(ProcessoId.novo())).isEmpty();
    }

    @Test
    void numeroDeItemRemovidoNaoVoltaDepoisDeRecarregar() {
        var id = abrir();
        adicionar(id, "1", "10", "USD");
        adicionar(id, "1", "10", "USD");
        emTransacao(() -> {
            var processo = repositorio.buscarPorId(id).orElseThrow();
            processo.removerItem(2);
            repositorio.salvar(processo);
        });

        adicionar(id, "1", "10", "USD");

        var numeros = repositorio.buscarPorId(id).orElseThrow().itens().stream().map(item -> item.numero()).toList();
        assertThat(numeros).containsExactly(1, 3);
    }

    @Test
    void gravaStatusEMotivoDoCancelamento() {
        var id = abrir();
        emTransacao(() -> {
            var processo = repositorio.buscarPorId(id).orElseThrow();
            processo.cancelar("aberto por engano");
            repositorio.salvar(processo);
        });

        var recuperado = repositorio.buscarPorId(id).orElseThrow();

        assertThat(recuperado.status()).isEqualTo(StatusProcesso.CANCELADO);
        assertThat(recuperado.motivoCancelamento()).isEqualTo("aberto por engano");
    }

    @Test
    void gravacaoConcorrenteNoMesmoProcessoFalha() {
        var id = abrir();
        var outraTransacao = new TransactionTemplate(transactionManager);
        outraTransacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> emTransacao(() -> {
            var nossaCopia = repositorio.buscarPorId(id).orElseThrow();

            // enquanto "a nossa" transação está aberta, outra lê o mesmo processo, adiciona o item 1 e confirma
            outraTransacao.executeWithoutResult(_ -> {
                var copiaDaOutra = repositorio.buscarPorId(id).orElseThrow();
                copiaDaOutra.adicionarItem(NCM, BigDecimal.ONE, new Dinheiro("10", "USD"));
                repositorio.salvar(copiaDaOutra);
            });

            // a nossa cópia ainda acha que não há itens e também cria o item 1
            nossaCopia.adicionarItem(NCM, BigDecimal.TWO, new Dinheiro("20", "USD"));
            repositorio.salvar(nossaCopia);
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        var itens = repositorio.buscarPorId(id).orElseThrow().itens();
        assertThat(itens).hasSize(1);
        assertThat(itens.getFirst().quantidade()).isEqualByComparingTo("1");
    }

    private void emTransacao(Runnable acao) {
        new TransactionTemplate(transactionManager).executeWithoutResult(_ -> acao.run());
    }
}
