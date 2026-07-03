package com.wallet.walletservice.messaging.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.StringUtils;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EnableKafka
@Configuration
@ConditionalOnProperty(prefix = "wallet.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerConfig {

    @Value("${wallet.kafka.consumer.concurrency:1}")
    private int concurrency;

    @Value("${wallet.kafka.consumer.retry-interval-ms:1000}")
    private long retryIntervalMs;

    @Value("${wallet.kafka.consumer.retry-max-attempts:3}")
    private long retryMaxAttempts;


    @Bean
    public ConsumerFactory<String, String> walletStringConsumerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaConsumerFactory<>(consumerProperties(kafkaProperties));
    }

    @Bean(name = "walletStringKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> walletStringKafkaListenerContainerFactory(
            ConsumerFactory<String, String> walletStringConsumerFactory
    ){
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(walletStringConsumerFactory);
        factory.setConcurrency(Math.max(1, concurrency));

        /*
         * Manual ack means we commit offset only after our listener finishes DB work.
         * We will explicitly acknowledge after kafka_event_audit insert succeeds.
         */
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        /*
         * If the listener throws, Spring retries before moving forward.
         * We are not adding DLT yet; that can come later if needed.
         */
        factory.setCommonErrorHandler(
                new DefaultErrorHandler(
                        new FixedBackOff(
                                Math.max(0, retryIntervalMs),
                                Math.max(0, retryMaxAttempts)
                        )
                )
        );

        return factory;
    }

    private Map<String, Object> consumerProperties(KafkaProperties kafkaProperties){
        KafkaProperties.Consumer consumer = kafkaProperties.getConsumer();

        Map<String, Object> props = new HashMap<>();

        List<String> bootStrapServers = kafkaProperties.getBootstrapServers();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootStrapServers.isEmpty() ? List.of("localhost:9092") : bootStrapServers);
        props.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                StringUtils.hasText(consumer.getGroupId())
                        ? consumer.getGroupId()
                        : "wallet-service-audit-consumer"
        );

        props.put(
                ConsumerConfig.CLIENT_ID_CONFIG,
                StringUtils.hasText(consumer.getClientId())
                        ? consumer.getClientId()
                        : "wallet-service-audit-consumer"
        );

        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                StringUtils.hasText(consumer.getAutoOffsetReset())
                        ? consumer.getAutoOffsetReset()
                        : "earliest"
        );

        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);

        props.putAll(consumer.getProperties());

        /*
         * Force the final deserializer choice. We are consuming raw JSON strings,
         * not Java DTOs or JsonNode values.
         */
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return props;

    }
}