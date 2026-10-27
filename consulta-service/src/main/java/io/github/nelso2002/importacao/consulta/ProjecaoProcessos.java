package io.github.nelso2002.importacao.consulta;

import io.github.nelso2002.importacao.consulta.ConsultaProcessos.ItemResumo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Consome processo-eventos e mantém processo_resumo no banco deste serviço.
 * Não depende das classes do processo-service: o contrato entre os dois é o JSON da mensagem.
 */
@Component
public class ProjecaoProcessos {

    private static final Logger log = LoggerFactory.getLogger(ProjecaoProcessos.class);

    /** Envelope publicado pelo PublicadorOutbox do processo-service. */
    record EventoRecebido(UUID aggregateId, int versao, String tipo, OffsetDateTime ocorridoEm, JsonNode dados) {}

    private final DatabaseClient db;
    private final JsonMapper json;
    private final ConsultaProcessos consultas;
    private final MonitorDeMudancas monitor;

    public ProjecaoProcessos(DatabaseClient db, JsonMapper json, ConsultaProcessos consultas, MonitorDeMudancas monitor) {
        this.db = db;
        this.json = json;
        this.consultas = consultas;
        this.monitor = monitor;
    }

    // Roda numa thread do consumidor Kafka (não nas do Netty), então esperar o R2DBC com block() é seguro aqui.
    // O offset só é confirmado depois que o método termina: se a projeção falhar, a mensagem é entregue de novo.
    @KafkaListener(topics = "processo-eventos")
    public void receber(String mensagem) {
        var evento = json.readValue(mensagem, EventoRecebido.class);
        var alteradas = projetar(evento).block();
        if (alteradas != null && alteradas > 0) {
            consultas.resumo(evento.aggregateId()).doOnNext(monitor::publicar).block();
        }
    }

    /**
     * Idempotente: o ProcessoAberto não insere duas vezes, e os demais só atualizam se a projeção
     * estiver numa versão menor. Mensagem repetida (o outbox é at-least-once) não altera nada.
     * Assume ordem por processo, garantida pela chave da mensagem (mesma partição).
     * O atualizado_em vem do evento, e não do relógio: ao reconstruir a projeção, as datas continuam as originais.
     */
    Mono<Long> projetar(EventoRecebido evento) {
        var dados = evento.dados();
        return switch (evento.tipo()) {
            case "ProcessoAberto" -> db.sql("""
                            insert into processo_resumo (id, importador, referencia, status, atualizado_em, versao)
                            values (:id, :importador, :referencia, 'ABERTO', :quando, :versao)
                            on conflict (id) do nothing
                            """)
                    .bind("id", evento.aggregateId())
                    .bind("importador", dados.get("importador").get("numero").asString())
                    .bind("referencia", dados.get("referencia").asString())
                    .bind("quando", evento.ocorridoEm())
                    .bind("versao", evento.versao())
                    .fetch().rowsUpdated();
            case "ItemAdicionado" -> {
                var item = dados.get("item");
                var resumo = new ItemResumo(
                        item.get("numero").asInt(),
                        item.get("ncm").get("codigo").asString(),
                        item.get("quantidade").decimalValue(),
                        item.get("valorTotal").get("valor").decimalValue(),
                        item.get("valorTotal").get("moeda").asString());
                yield atualizar(evento, "itens = itens || cast(:item as jsonb)", "item", json.writeValueAsString(List.of(resumo)));
            }
            case "ItemRemovido" -> atualizar(evento, """
                            itens = (select coalesce(jsonb_agg(e.item order by e.posicao), '[]'::jsonb)
                                     from jsonb_array_elements(itens) with ordinality as e(item, posicao)
                                     where (e.item ->> 'numero')::int <> :numero)
                            """, "numero", dados.get("numeroItem").asInt());
            case "DeclaracaoRegistrada" -> atualizar(evento, "status = 'REGISTRADO'", null, null);
            case "ProcessoDesembaracado" -> atualizar(evento, "status = 'DESEMBARACADO'", null, null);
            case "ProcessoCancelado" -> atualizar(evento, "status = 'CANCELADO', motivo_cancelamento = :motivo",
                    "motivo", dados.get("motivo").asString());
            // Leitor tolerante: um tipo novo publicado pelo outro serviço não derruba este consumidor.
            // (Dentro de um serviço o switch é exaustivo; entre serviços, cada um evolui no seu ritmo.)
            default -> {
                log.info("Evento {} ignorado pela projeção", evento.tipo());
                yield Mono.just(0L);
            }
        };
    }

    private Mono<Long> atualizar(EventoRecebido evento, String alteracao, String parametro, Object valor) {
        var sql = "update processo_resumo set " + alteracao + ", atualizado_em = :quando, versao = :versao "
                + "where id = :id and versao < :versao";
        var consulta = db.sql(sql)
                .bind("quando", evento.ocorridoEm())
                .bind("versao", evento.versao())
                .bind("id", evento.aggregateId());
        if (parametro != null) {
            consulta = consulta.bind(parametro, valor);
        }
        return consulta.fetch().rowsUpdated();
    }
}
