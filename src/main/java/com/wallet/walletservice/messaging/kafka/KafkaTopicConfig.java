package com.wallet.walletservice.messaging.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(WalletKafkaProperties.class)
@ConditionalOnProperty(prefix = "wallet.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    private final WalletKafkaProperties walletKafkaProperties;

    @Bean
    public NewTopic walletTransactionEventTopic(){
        WalletKafkaProperties.TopicSettings settings = walletKafkaProperties.getTopicSettings();

        return TopicBuilder.name(walletKafkaProperties.getTopics().getWalletTransactionsEvents())
                .partitions(settings.getPartitions())
                .replicas(settings.getReplicas())
                .configs(settings.asKafkaConfigs())
                .build();
    }
}