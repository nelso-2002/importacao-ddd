package io.github.nelso2002.importacao.processo.consulta;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado de leitura: SELECTs diretos em tabelas prontas para a tela, sem passar pelo agregado.
 * Os modelos são dados puros (status como texto), independentes do domínio.
 */
@Component
public class ConsultaProcessos {

    public record ProcessoResumo(UUID id, String referencia, String importador, String status,
                                 int quantidadeItens, OffsetDateTime atualizadoEm) {}

    public record ProcessoDetalhe(UUID id, String referencia, String importador, String status,
                                  String motivoCancelamento, List<ItemResumo> itens, OffsetDateTime atualizadoEm) {}

    public record ItemResumo(int numero, String ncm, BigDecimal quantidade, BigDecimal valor, String moeda) {}

    public record EventoHistorico(int versao, String tipo, OffsetDateTime ocorridoEm, JsonNode dados) {}

    private static final TypeReference<List<ItemResumo>> LISTA_DE_ITENS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ConsultaProcessos(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // ponytail: sem paginação, só os 100 mais recentes; paginar quando a tela precisar
    public List<ProcessoResumo> listar() {
        return jdbc.sql("""
                        select id, referencia, importador, status, jsonb_array_length(itens) as quantidade_itens, atualizado_em
                        from processo_resumo
                        order by atualizado_em desc
                        limit 100
                        """)
                .query(ProcessoResumo.class)
                .list();
    }

    public Optional<ProcessoDetalhe> buscar(UUID id) {
        return jdbc.sql("select * from processo_resumo where id = ?")
                .param(id)
                .query((linha, _) -> new ProcessoDetalhe(
                        linha.getObject("id", UUID.class),
                        linha.getString("referencia"),
                        linha.getString("importador"),
                        linha.getString("status"),
                        linha.getString("motivo_cancelamento"),
                        json.readValue(linha.getString("itens"), LISTA_DE_ITENS),
                        linha.getObject("atualizado_em", OffsetDateTime.class)))
                .optional();
    }

    // O histórico é o próprio event store: imutável e já em ordem, não precisa de projeção.
    public List<EventoHistorico> historico(UUID id) {
        return jdbc.sql("select version, type, occurred_at, payload from event_store where aggregate_id = ? order by version")
                .param(id)
                .query((linha, _) -> new EventoHistorico(
                        linha.getInt("version"),
                        linha.getString("type"),
                        linha.getObject("occurred_at", OffsetDateTime.class),
                        json.readTree(linha.getString("payload"))))
                .list();
    }
}
