package io.github.nelso2002.importacao.processo.consulta;

import io.github.nelso2002.importacao.processo.consulta.ConsultaProcessos.ItemResumo;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.DeclaracaoRegistrada;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ItemAdicionado;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ItemRemovido;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ProcessoAberto;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ProcessoCancelado;
import io.github.nelso2002.importacao.processo.domain.ProcessoEvento.ProcessoDesembaracado;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Mantém processo_resumo a partir dos eventos gravados.
 * Síncrono: roda na mesma thread e transação do caso de uso, então a leitura logo após um POST já vê a mudança,
 * e se a projeção falhar o comando inteiro é desfeito. Na Etapa 4 vira assíncrono, consumindo do Kafka.
 */
@Component
public class ProcessoResumoProjetor {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ProcessoResumoProjetor(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // Sem default, como o aplicar() do agregado: um evento novo sem projeção não compila.
    // now() no Postgres é o início da transação: o mesmo instante gravado no occurred_at do evento.
    @EventListener
    public void projetar(ProcessoEvento evento) {
        switch (evento) {
            case ProcessoAberto(var id, var importador, var referencia) -> jdbc.sql("""
                            insert into processo_resumo (id, importador, referencia, status, atualizado_em)
                            values (?, ?, ?, 'ABERTO', now())
                            """)
                    .params(id.valor(), importador.numero(), referencia)
                    .update();
            case ItemAdicionado(var id, var item) -> {
                var resumo = new ItemResumo(item.numero(), item.ncm().codigo(), item.quantidade(),
                        item.valorTotal().valor(), item.valorTotal().moeda().getCurrencyCode());
                jdbc.sql("update processo_resumo set itens = itens || ?::jsonb, atualizado_em = now() where id = ?")
                        .params(json.writeValueAsString(List.of(resumo)), id.valor())
                        .update();
            }
            case ItemRemovido(var id, var numero) -> jdbc.sql("""
                            update processo_resumo
                            set itens = (select coalesce(jsonb_agg(e.item order by e.posicao), '[]'::jsonb)
                                         from jsonb_array_elements(itens) with ordinality as e(item, posicao)
                                         where (e.item ->> 'numero')::int <> ?),
                                atualizado_em = now()
                            where id = ?
                            """)
                    .params(numero, id.valor())
                    .update();
            case DeclaracaoRegistrada(var id) -> mudarStatus(id, "REGISTRADO");
            case ProcessoDesembaracado(var id) -> mudarStatus(id, "DESEMBARACADO");
            case ProcessoCancelado(var id, var motivo) -> jdbc.sql("""
                            update processo_resumo
                            set status = 'CANCELADO', motivo_cancelamento = ?, atualizado_em = now()
                            where id = ?
                            """)
                    .params(motivo, id.valor())
                    .update();
        }
    }

    private void mudarStatus(ProcessoId id, String status) {
        jdbc.sql("update processo_resumo set status = ?, atualizado_em = now() where id = ?")
                .params(status, id.valor())
                .update();
    }
}
