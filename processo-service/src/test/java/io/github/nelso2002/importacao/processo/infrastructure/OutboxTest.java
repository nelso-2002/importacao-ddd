package io.github.nelso2002.importacao.processo.infrastructure;

import io.github.nelso2002.importacao.processo.TestcontainersConfiguration;
import io.github.nelso2002.importacao.processo.application.AbrirProcesso;
import io.github.nelso2002.importacao.processo.application.AdicionarItem;
import io.github.nelso2002.importacao.processo.domain.Cnpj;
import io.github.nelso2002.importacao.processo.domain.Dinheiro;
import io.github.nelso2002.importacao.processo.domain.Ncm;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.ProcessoRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** Outbox de ponta a ponta com Postgres e Kafka reais: do comando até a mensagem no tópico. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OutboxTest {

    private static final Ncm NCM = new Ncm("90308410");

    @Autowired
    private AbrirProcesso abrirProcesso;

    @Autowired
    private AdicionarItem adicionarItem;

    @Autowired
    private ProcessoRepository repositorio;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private KafkaConnectionDetails kafkaConexao;

    private KafkaConsumer<String, String> consumidor;
    private final List<ConsumerRecord<String, String>> recebidos = new ArrayList<>();

    @BeforeEach
    void assinarTopico() {
        consumidor = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafkaConexao.getBootstrapServers()),
                ConsumerConfig.GROUP_ID_CONFIG, "teste-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
        consumidor.subscribe(List.of(PublicadorOutbox.TOPICO));
    }

    @AfterEach
    void fechar() {
        consumidor.close();
    }

    private ProcessoId abrir() {
        return abrirProcesso.executar(new AbrirProcesso.Comando(new Cnpj("11222333000181"), "IMP-OUTBOX"));
    }

    /** Lê o tópico até chegarem as mensagens esperadas deste processo (o publicador é assíncrono). */
    private List<ConsumerRecord<String, String>> mensagensDo(ProcessoId id, int quantidade) {
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            consumidor.poll(Duration.ofMillis(200)).forEach(recebidos::add);
            assertThat(recebidos.stream().filter(r -> r.key().equals(id.valor().toString()))).hasSize(quantidade);
        });
        return recebidos.stream().filter(r -> r.key().equals(id.valor().toString())).toList();
    }

    private int contar(String tabela, ProcessoId id) {
        return jdbc.sql("select count(*) from " + tabela + " where aggregate_id = ?")
                .param(id.valor()).query(Integer.class).single();
    }

    @Test
    void publicaOsEventosEmOrdemComAChaveDoProcesso() {
        var id = abrir();
        adicionarItem.executar(new AdicionarItem.Comando(id, NCM, BigDecimal.ONE, new Dinheiro("10", "USD")));

        var mensagens = mensagensDo(id, 2);

        var eventos = mensagens.stream().map(r -> json.readValue(r.value(), PublicadorOutbox.EventoPublicado.class)).toList();
        assertThat(eventos).extracting(PublicadorOutbox.EventoPublicado::tipo).containsExactly("ProcessoAberto", "ItemAdicionado");
        assertThat(eventos).extracting(PublicadorOutbox.EventoPublicado::versao).containsExactly(1, 2);
        assertThat(eventos.getFirst().dados().get("referencia").asString()).isEqualTo("IMP-OUTBOX");
        // mesma chave → mesma partição → ordem garantida
        assertThat(mensagens).extracting(ConsumerRecord::partition).containsOnly(mensagens.getFirst().partition());
    }

    @Test
    void marcaComoPublicadoDepoisDoEnvio() {
        var id = abrir();
        mensagensDo(id, 1);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.sql("select count(*) from outbox where aggregate_id = ? and published_at is null")
                        .param(id.valor()).query(Integer.class).single()).isZero());
    }

    @Test
    void outboxEEventStoreSaoGravadosJuntos() {
        var id = abrir();
        adicionarItem.executar(new AdicionarItem.Comando(id, NCM, BigDecimal.ONE, new Dinheiro("10", "USD")));

        assertThat(contar("event_store", id)).isEqualTo(2);
        assertThat(contar("outbox", id)).isEqualTo(2);
    }

    @Test
    void gravacaoRecusadaNaoEntraNaOutbox() {
        var id = abrir();
        var primeira = repositorio.buscarPorId(id).orElseThrow();
        var segunda = repositorio.buscarPorId(id).orElseThrow();
        primeira.adicionarItem(NCM, BigDecimal.ONE, new Dinheiro("10", "USD"));
        repositorio.salvar(primeira);
        segunda.adicionarItem(NCM, BigDecimal.TWO, new Dinheiro("20", "USD"));

        assertThatThrownBy(() -> repositorio.salvar(segunda)).isInstanceOf(OptimisticLockingFailureException.class);

        // o conflito desfez a transação inteira: nada da segunda gravação foi para a fila de publicação
        assertThat(contar("outbox", id)).isEqualTo(2);
        assertThat(mensagensDo(id, 2)).hasSize(2);
    }
}
