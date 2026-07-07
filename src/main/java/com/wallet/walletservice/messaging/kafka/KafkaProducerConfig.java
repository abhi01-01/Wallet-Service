package com.wallet.walletservice.messaging.kafka;

import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "wallet.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, String> walletJsonNodeProducerFactory(KafkaProperties kafkaProperties){
        return new DefaultKafkaProducerFactory<>(producerProperties(kafkaProperties));
    }

    @Bean
    public KafkaTemplate<String, String> walletJsonNodeKafkaTemplate(ProducerFactory<String, String> walletJsonNodeProducerFactory){
        return new KafkaTemplate<>(walletJsonNodeProducerFactory);
    }

    private Map<String, Object> producerProperties(KafkaProperties kafkaProperties){
        KafkaProperties.Producer producer = kafkaProperties.getProducer();
        Map<String, Object> props = new HashMap<>();

        List<String> bootstrapServers = kafkaProperties.getBootstrapServers();
        props.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers.isEmpty() ? List.of("localhost:9092") : bootstrapServers
        );
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.CLIENT_ID_CONFIG,
                StringUtils.hasText(producer.getClientId()) ? producer.getClientId() : "wallet-service-producer");
        props.put(ProducerConfig.ACKS_CONFIG,
                StringUtils.hasText(producer.getAcks()) ? producer.getAcks() : "all");
        props.put(ProducerConfig.RETRIES_CONFIG, producer.getRetries() != null ? producer.getRetries() : 10);

        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        props.putAll(producer.getProperties());
        return props;
    }

}