package io.github.nelso2002.importacao.processo.consulta;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Única leitura que ficou no processo-service: o histórico, que é o próprio event_store.
 * Lista e detalhe são do consulta-service, a partir dos eventos publicados no Kafka.
 */
@Component
public class ConsultaProcessos {

    public record EventoHistorico(int versao, String tipo, OffsetDateTime ocorridoEm, String usuario, JsonNode dados) {}

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ConsultaProcessos(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // Imutável e já em ordem: não precisa de projeção.
    public List<EventoHistorico> historico(UUID id) {
        return jdbc.sql("select version, type, occurred_at, usuario, payload from event_store where aggregate_id = ? order by version")
                .param(id)
                .query((linha, _) -> new EventoHistorico(
                        linha.getInt("version"),
                        linha.getString("type"),
                        linha.getObject("occurred_at", OffsetDateTime.class),
                        linha.getString("usuario"),
                        json.readTree(linha.getString("payload"))))
                .list();
    }
}
