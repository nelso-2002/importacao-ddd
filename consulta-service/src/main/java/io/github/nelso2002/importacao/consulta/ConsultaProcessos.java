package io.github.nelso2002.importacao.consulta;

import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Leituras reativas da projeção processo_resumo (R2DBC): nenhuma thread fica parada esperando o banco;
 * cada linha que chega vira um elemento do Flux.
 */
@Component
public class ConsultaProcessos {

    public record ProcessoResumo(UUID id, String referencia, String importador, String status,
                                 int quantidadeItens, OffsetDateTime atualizadoEm) {}

    public record ProcessoDetalhe(UUID id, String referencia, String importador, String status,
                                  String motivoCancelamento, List<ItemResumo> itens, OffsetDateTime atualizadoEm) {}

    public record ItemResumo(int numero, String ncm, BigDecimal quantidade, BigDecimal valor, String moeda) {}

    private static final String COLUNAS_RESUMO = """
            select id, referencia, importador, status, jsonb_array_length(itens) as quantidade_itens, atualizado_em
            from processo_resumo
            """;

    private static final TypeReference<List<ItemResumo>> LISTA_DE_ITENS = new TypeReference<>() {};

    private final DatabaseClient db;
    private final JsonMapper json;

    public ConsultaProcessos(DatabaseClient db, JsonMapper json) {
        this.db = db;
        this.json = json;
    }

    // ponytail: só os 100 mais recentes, sem paginação; paginar quando a tela precisar
    public Flux<ProcessoResumo> listar(String status, String busca) {
        // filtros opcionais montados no SQL, mas valores sempre como parâmetros (nada de concatenar texto do usuário)
        var sql = new StringBuilder(COLUNAS_RESUMO).append("where true");
        if (StringUtils.hasText(status)) {
            sql.append(" and status = :status");
        }
        if (StringUtils.hasText(busca)) {
            sql.append(" and (referencia ilike :busca or importador ilike :busca)");
        }
        sql.append(" order by atualizado_em desc limit 100");

        var consulta = db.sql(sql.toString());
        if (StringUtils.hasText(status)) {
            consulta = consulta.bind("status", status);
        }
        if (StringUtils.hasText(busca)) {
            consulta = consulta.bind("busca", "%" + literal(busca.strip()) + "%");
        }
        return consulta.map(ConsultaProcessos::paraResumo).all();
    }

    public Mono<ProcessoResumo> resumo(UUID id) {
        return db.sql(COLUNAS_RESUMO + "where id = :id")
                .bind("id", id)
                .map(ConsultaProcessos::paraResumo)
                .one();
    }

    public Mono<ProcessoDetalhe> buscar(UUID id) {
        return db.sql("""
                        select id, referencia, importador, status, motivo_cancelamento, itens::text as itens, atualizado_em
                        from processo_resumo
                        where id = :id
                        """)
                .bind("id", id)
                .map(linha -> new ProcessoDetalhe(
                        linha.get("id", UUID.class),
                        linha.get("referencia", String.class),
                        linha.get("importador", String.class),
                        linha.get("status", String.class),
                        linha.get("motivo_cancelamento", String.class),
                        json.readValue(linha.get("itens", String.class), LISTA_DE_ITENS),
                        linha.get("atualizado_em", OffsetDateTime.class)))
                .one();
    }

    /**
     * O texto digitado é procurado como texto, não como padrão: % e _ são curingas do (i)like
     * ("_" sozinho acharia tudo). O valor já ia como parâmetro, então isto não é sobre injeção de SQL,
     * e sim sobre a busca fazer o que o usuário pediu. A barra é o caractere de escape padrão do Postgres.
     */
    static String literal(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ProcessoResumo paraResumo(Readable linha) {
        return new ProcessoResumo(
                linha.get("id", UUID.class),
                linha.get("referencia", String.class),
                linha.get("importador", String.class),
                linha.get("status", String.class),
                linha.get("quantidade_itens", Integer.class),
                linha.get("atualizado_em", OffsetDateTime.class));
    }
}
