package io.github.nelso2002.importacao.processo.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lê a outbox em ordem e publica no Kafka. Garantia: pelo menos uma vez (at-least-once).
 * Se o envio der certo e a marcação falhar (ou o serviço cair entre os dois), o evento é publicado de novo
 * na próxima rodada; por isso o consumidor precisa ser idempotente (Etapa 4, passo 2).
 */
@Component
public class PublicadorOutbox {

    public static final String TOPICO = "processo-eventos";

    private static final Logger log = LoggerFactory.getLogger(PublicadorOutbox.class);

    /** Mensagem publicada: o evento com o que o consumidor precisa para ordenar e deduplicar. */
    public record EventoPublicado(UUID aggregateId, int versao, String tipo, OffsetDateTime ocorridoEm, String usuario,
                                  JsonNode dados) {}

    private record Pendente(long id, EventoPublicado evento) {}

    private final JdbcClient jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    public PublicadorOutbox(JdbcClient jdbc, KafkaTemplate<String, String> kafka, JsonMapper json) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.json = json;
    }

    @Scheduled(fixedDelayString = "${outbox.intervalo:500ms}")
    @Transactional
    public void publicarPendentes() {
        // skip locked: com mais de uma instância do serviço, cada uma pega um lote diferente em vez de esperar
        var pendentes = jdbc.sql("""
                        select id, aggregate_id, version, type, occurred_at, usuario, payload
                        from outbox
                        where published_at is null
                        order by id
                        limit 100
                        for update skip locked
                        """)
                .query((linha, _) -> new Pendente(linha.getLong("id"), new EventoPublicado(
                        linha.getObject("aggregate_id", UUID.class),
                        linha.getInt("version"),
                        linha.getString("type"),
                        linha.getObject("occurred_at", OffsetDateTime.class),
                        linha.getString("usuario"),
                        json.readTree(linha.getString("payload")))))
                .list();

        for (var pendente : pendentes) {
            var evento = pendente.evento();
            try {
                // chave = id do processo: todos os eventos de um processo caem na mesma partição, em ordem.
                // join() espera a confirmação do broker antes de marcar como publicado.
                kafka.send(TOPICO, evento.aggregateId().toString(), json.writeValueAsString(evento)).join();
            } catch (RuntimeException e) {
                // Para no primeiro erro: publicar os seguintes antes deste quebraria a ordem de um mesmo processo.
                // Os já enviados neste lote são marcados no commit; o resto fica para a próxima rodada.
                log.warn("Kafka indisponível; {} evento(s) aguardando na outbox: {}", pendentes.size(), e.getMessage());
                return;
            }
            jdbc.sql("update outbox set published_at = now() where id = ?").param(pendente.id()).update();
        }
    }
}
