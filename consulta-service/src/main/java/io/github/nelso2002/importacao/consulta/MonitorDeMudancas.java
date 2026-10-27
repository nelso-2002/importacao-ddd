package io.github.nelso2002.importacao.consulta;

import io.github.nelso2002.importacao.consulta.ConsultaProcessos.ProcessoResumo;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Um único polling para todos os clientes: a cada intervalo busca o que mudou na projeção
 * e emite num Sink multicast, que repassa cada mudança a todos os streams SSE abertos.
 */
@Component
public class MonitorDeMudancas {

    private static final Logger log = LoggerFactory.getLogger(MonitorDeMudancas.class);

    // multicast: um emissor, vários assinantes; directBestEffort: quem está lento perde a mudança
    // em vez de segurar os outros, e sem assinantes nada é acumulado (só interessa o que acontece daqui para frente)
    private final Sinks.Many<ProcessoResumo> mudancas = Sinks.many().multicast().directBestEffort();
    private final Disposable polling;

    public MonitorDeMudancas(ConsultaProcessos consultas,
                             @Value("${consulta.intervalo-polling:2s}") Duration intervalo) {
        var ultimaVista = new AtomicReference<>(OffsetDateTime.now());
        // ponytail: polling por atualizado_em perde a mudança de uma transação que começou antes da última vista
        // e terminou depois dela; na Etapa 4 o Kafka substitui o polling e o problema some
        polling = Flux.interval(intervalo)
                .onBackpressureDrop()   // se a consulta demorar mais que o intervalo, pula o tique em vez de enfileirar
                .concatMap(_ -> consultas.alteradosDepoisDe(ultimaVista.get()))
                .doOnNext(resumo -> ultimaVista.set(resumo.atualizadoEm()))
                .doOnError(erro -> log.warn("Falha no polling da projeção; tentando de novo: {}", erro.getMessage()))
                .retry()                // erro de banco não pode encerrar o polling para sempre
                .subscribe(mudancas::tryEmitNext);
    }

    public Flux<ProcessoResumo> mudancas() {
        return mudancas.asFlux();
    }

    @PreDestroy
    void parar() {
        polling.dispose();
    }
}
