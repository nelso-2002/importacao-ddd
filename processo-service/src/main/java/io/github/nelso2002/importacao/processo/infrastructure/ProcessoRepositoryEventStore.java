package io.github.nelso2002.importacao.processo.infrastructure;

import io.github.nelso2002.importacao.processo.domain.ProcessoEvento;
import io.github.nelso2002.importacao.processo.domain.ProcessoId;
import io.github.nelso2002.importacao.processo.domain.ProcessoImportacao;
import io.github.nelso2002.importacao.processo.domain.ProcessoRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adaptador da porta ProcessoRepository sobre a tabela event_store: gravar é anexar os eventos novos,
 * carregar é reaplicar o histórico. JDBC puro: o event store só faz INSERT e SELECT ordenado.
 */
@Repository
public class ProcessoRepositoryEventStore implements ProcessoRepository {

    // Nome gravado na coluna "type" -> classe do evento. Vem da própria sealed interface, sem lista manual.
    // O nome simples vira contrato: renomear um evento exige migrar (ou "upcast") os eventos já gravados.
    private static final Map<String, Class<?>> TIPOS = Arrays.stream(ProcessoEvento.class.getPermittedSubclasses())
            .collect(Collectors.toUnmodifiableMap(Class::getSimpleName, Function.identity()));

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ProcessoRepositoryEventStore(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void salvar(ProcessoImportacao processo) {
        var novos = processo.retirarEventos();
        // versão em que o processo estava quando foi carregado; cada evento novo ocupa a próxima
        var versao = processo.versao() - novos.size();
        try {
            for (var evento : novos) {
                versao++;
                jdbc.sql("insert into event_store (aggregate_id, version, type, payload) values (?, ?, ?, ?::jsonb)")
                        .params(processo.id().valor(), versao, evento.getClass().getSimpleName(), json.writeValueAsString(evento))
                        .update();
            }
        } catch (DuplicateKeyException e) {
            throw new OptimisticLockingFailureException(
                    "Processo " + processo.id().valor() + " foi alterado por outra gravação (versão " + versao + ")", e);
        }
    }

    @Override
    public Optional<ProcessoImportacao> buscarPorId(ProcessoId id) {
        var historico = jdbc.sql("select type, payload from event_store where aggregate_id = ? order by version")
                .param(id.valor())
                .query((linha, _) -> paraEvento(linha.getString("type"), linha.getString("payload")))
                .list();
        return historico.isEmpty() ? Optional.empty() : Optional.of(ProcessoImportacao.reconstituir(historico));
    }

    private ProcessoEvento paraEvento(String tipo, String payload) {
        var classe = TIPOS.get(tipo);
        if (classe == null) {
            throw new IllegalStateException("Tipo de evento desconhecido no event store: " + tipo);
        }
        return (ProcessoEvento) json.readValue(payload, classe);
    }
}
