package io.github.nelso2002.importacao.consulta;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConfig {

    // O padrão do Spring Kafka tenta 10 vezes e depois DESCARTA a mensagem (confirma o offset).
    // Numa projeção isso é perder um evento para sempre. Aqui: tenta de novo a cada 2 s até o banco voltar.
    // ponytail: uma mensagem que nunca vai dar certo (JSON quebrado) trava a partição; usar um tópico de
    // mensagens mortas (DeadLetterPublishingRecoverer) quando isso virar problema.
    @Bean
    CommonErrorHandler tratamentoDeErros() {
        return new DefaultErrorHandler(new FixedBackOff(2000, FixedBackOff.UNLIMITED_ATTEMPTS));
    }
}
