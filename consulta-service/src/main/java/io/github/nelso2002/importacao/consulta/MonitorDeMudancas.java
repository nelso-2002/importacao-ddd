package io.github.nelso2002.importacao.consulta;

import io.github.nelso2002.importacao.consulta.ConsultaProcessos.ProcessoResumo;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

/**
 * Repassa cada processo alterado pela projeção a todos os streams SSE abertos.
 * Alimentado pelo consumidor Kafka (ProjecaoProcessos); o polling da Etapa 3 deixou de existir.
 */
@Component
public class MonitorDeMudancas {

    // multicast: um emissor, vários assinantes; directBestEffort: quem está lento perde a mudança
    // em vez de segurar os outros, e sem assinantes nada é acumulado
    private final Sinks.Many<ProcessoResumo> mudancas = Sinks.many().multicast().directBestEffort();

    public void publicar(ProcessoResumo resumo) {
        // Um Sink não aceita duas emissões ao mesmo tempo (FAIL_NON_SERIALIZED). Se o consumidor rodar com
        // mais de uma thread, a segunda espera um instante e tenta de novo, em vez de perder a mudança.
        mudancas.emitNext(resumo, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
    }

    public Flux<ProcessoResumo> mudancas() {
        return mudancas.asFlux();
    }
}
