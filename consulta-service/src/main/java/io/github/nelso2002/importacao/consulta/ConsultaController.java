package io.github.nelso2002.importacao.consulta;

import io.github.nelso2002.importacao.consulta.ConsultaProcessos.ProcessoDetalhe;
import io.github.nelso2002.importacao.consulta.ConsultaProcessos.ProcessoResumo;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

/**
 * Os métodos devolvem Flux/Mono em vez de valores: o WebFlux assina e escreve a resposta
 * conforme os dados chegam, sem uma thread bloqueada por requisição.
 */
@RestController
@RequestMapping("/consultas/processos")
class ConsultaController {

    private final ConsultaProcessos consultas;
    private final MonitorDeMudancas monitor;

    ConsultaController(ConsultaProcessos consultas, MonitorDeMudancas monitor) {
        this.consultas = consultas;
        this.monitor = monitor;
    }

    @GetMapping
    Flux<ProcessoResumo> listar(@RequestParam(required = false) String status,
                                @RequestParam(required = false) String busca) {
        return consultas.listar(status, busca);
    }

    @GetMapping("/{id}")
    Mono<ProcessoDetalhe> buscar(@PathVariable UUID id) {
        return consultas.buscar(id)
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processo não encontrado: " + id)));
    }

    // Server-Sent Events: a resposta não termina; cada mudança vira um evento "processo" no navegador
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<ProcessoResumo>> stream() {
        var mudancas = monitor.mudancas()
                .map(resumo -> ServerSentEvent.builder(resumo).event("processo").id(resumo.id().toString()).build());
        // Comentário periódico para proxies e balanceadores não derrubarem a conexão por inatividade.
        // O primeiro sai na hora: os cabeçalhos HTTP só são enviados junto com o primeiro elemento,
        // e sem ele o cliente esperaria a primeira mudança para saber que conectou.
        var manterViva = Flux.interval(Duration.ZERO, Duration.ofSeconds(15))
                .map(_ -> ServerSentEvent.<ProcessoResumo>builder().comment("keep-alive").build());
        return Flux.merge(mudancas, manterViva);
    }
}
