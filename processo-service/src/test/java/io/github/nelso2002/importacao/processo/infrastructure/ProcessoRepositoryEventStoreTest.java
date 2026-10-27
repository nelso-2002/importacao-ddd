package io.github.nelso2002.importacao.processo.infrastructure;

import io.github.nelso2002.importacao.processo.TestcontainersConfiguration;
import io.github.nelso2002.importacao.processo.application.AbrirProcesso;
import io.github.nelso2002.importacao.processo.application.AdicionarItem;
import io.github.nelso2002.importacao.processo.application.UsuarioAtual;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integração com Postgres real (Testcontainers): os eventos vão para a tabela event_store em JSON
 * e o processo é reconstituído reaplicando-os. Cada busca relê do banco (não há cache como no JPA).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProcessoRepositoryEventStoreTest {

    private static final Cnpj IMPORTADOR = new Cnpj("11222333000181");
    private static final Ncm NCM = new Ncm("90308410");

    @Autowired
    private AbrirProcesso abrirProcesso;

    @Autowired
    private AdicionarItem adicionarItem;

    @Autowired
    private ProcessoRepository repositorio;

    @Autowired
    private JdbcClient jdbc;

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
        assertThat(recuperado.versao()).isEqualTo(3);
        var primeiro = recuperado.itens().getFirst();
        assertThat(primeiro.numero()).isEqualTo(1);
        assertThat(primeiro.quantidade()).isEqualTo(new BigDecimal("2.50000"));
        assertThat(primeiro.valorTotal()).isEqualTo(new Dinheiro("100.00", "USD"));
        assertThat(recuperado.itens().getLast().valorTotal()).isEqualTo(new Dinheiro("80.00", "EUR"));
    }

    @Test
    void gravaUmaLinhaPorEventoEmOrdemComPayloadJson() {
        var id = abrir();
        adicionar(id, "1", "10", "USD");

        var tipos = jdbc.sql("select type from event_store where aggregate_id = ? order by version")
                .param(id.valor()).query(String.class).list();
        // payload é jsonb: dá para consultar campos do evento direto no SQL
        var referencia = jdbc.sql("select payload ->> 'referencia' from event_store where aggregate_id = ? and version = 1")
                .param(id.valor()).query(String.class).single();

        assertThat(tipos).containsExactly("ProcessoAberto", "ItemAdicionado");
        assertThat(referencia).isEqualTo("IMP-2026/0001");
    }

    @Test
    void gravaOUsuarioLigadoAoScopedValueNoEventoENaOutbox() throws Exception {
        var id = ScopedValue.where(UsuarioAtual.USUARIO, "fulano").call(this::abrir);

        assertThat(jdbc.sql("select usuario from event_store where aggregate_id = ?").param(id.valor())
                .query(String.class).single()).isEqualTo("fulano");
        assertThat(jdbc.sql("select usuario from outbox where aggregate_id = ?").param(id.valor())
                .query(String.class).single()).isEqualTo("fulano");
    }

    @Test
    void foraDeUmaRequisicaoOUsuarioESistema() {
        var id = abrir();

        assertThat(jdbc.sql("select usuario from event_store where aggregate_id = ?").param(id.valor())
                .query(String.class).single()).isEqualTo("sistema");
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
        var processo = repositorio.buscarPorId(id).orElseThrow();
        processo.removerItem(2);
        repositorio.salvar(processo);

        adicionar(id, "1", "10", "USD");

        var numeros = repositorio.buscarPorId(id).orElseThrow().itens().stream().map(item -> item.numero()).toList();
        assertThat(numeros).containsExactly(1, 3);
    }

    @Test
    void gravaStatusEMotivoDoCancelamento() {
        var id = abrir();
        var processo = repositorio.buscarPorId(id).orElseThrow();
        processo.cancelar("aberto por engano");
        repositorio.salvar(processo);

        var recuperado = repositorio.buscarPorId(id).orElseThrow();

        assertThat(recuperado.status()).isEqualTo(StatusProcesso.CANCELADO);
        assertThat(recuperado.motivoCancelamento()).isEqualTo("aberto por engano");
    }

    @Test
    void salvarSemEventosNovosNaoGravaNada() {
        var id = abrir();
        var processo = repositorio.buscarPorId(id).orElseThrow();

        repositorio.salvar(processo);

        assertThat(jdbc.sql("select count(*) from event_store where aggregate_id = ?")
                .param(id.valor()).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void duasGravacoesSobreAMesmaVersaoSoAPrimeiraEntra() {
        var id = abrir();
        // duas requisições carregam o processo na versão 1
        var primeira = repositorio.buscarPorId(id).orElseThrow();
        var segunda = repositorio.buscarPorId(id).orElseThrow();

        primeira.adicionarItem(NCM, BigDecimal.ONE, new Dinheiro("10", "USD"));
        repositorio.salvar(primeira);                // grava a versão 2
        segunda.adicionarItem(NCM, BigDecimal.TWO, new Dinheiro("20", "USD"));

        assertThatThrownBy(() -> repositorio.salvar(segunda))   // também tenta gravar a versão 2
                .isInstanceOf(OptimisticLockingFailureException.class)
                .hasMessageContaining("versão 2");

        var itens = repositorio.buscarPorId(id).orElseThrow().itens();
        assertThat(itens).hasSize(1);
        assertThat(itens.getFirst().quantidade()).isEqualByComparingTo("1");
    }
}
