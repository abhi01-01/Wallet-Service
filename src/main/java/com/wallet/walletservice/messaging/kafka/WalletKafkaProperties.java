package com.wallet.walletservice.messaging.kafka;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "wallet.kafka")
public class WalletKafkaProperties {

    /*
     * Feature switch for Kafka infrastructure. Keep this true locally only when Kafka is running.
     */
    private boolean enabled = true;

    private Topics topics = new Topics();

    private TopicSettings topicSettings = new TopicSettings();

    @Getter
    @Setter
    public static class Topics{
        private String walletTransactionsEvents = "wallet.transaction.events.v1";
    }

    @Getter
    @Setter
    public static class TopicSettings{
        private int partitions = 3;
        private short replicas = 1;
        private long retentionMs = 604_800_000L;
        private String cleanupPolicy = "delete";
        private int minInSyncReplicas = 1;

        public Map<String, String> asKafkaConfigs(){
            Map<String, String> configs = new HashMap<>();
            configs.put("retention.ms", String.valueOf(retentionMs));
            configs.put("cleanup.policy", cleanupPolicy);
            configs.put("min.insync.replicas", String.valueOf(minInSyncReplicas));
            return configs;
        }
    }
}