package io.github.nelso2002.importacao.processo.infrastructure;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
class MensageriaConfig {

    // Criado na subida pelo KafkaAdmin do Spring. Várias partições para paralelizar o consumo;
    // a ordem só é garantida dentro de uma partição, por isso a chave da mensagem é o id do processo.
    @Bean
    NewTopic topicoProcessoEventos() {
        return TopicBuilder.name(PublicadorOutbox.TOPICO).partitions(3).replicas(1).build();
    }
}
