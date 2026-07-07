package com.wallet.walletservice.messaging.kafka;

public record KafkaPublishResult(
        String topic,
        int partition,
        long offset,
        long timestamp
) {
}