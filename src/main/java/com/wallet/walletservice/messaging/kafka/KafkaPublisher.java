package com.wallet.walletservice.messaging.kafka;

import com.wallet.walletservice.messaging.outbox.OutboxEvent;

public interface KafkaPublisher {
    KafkaPublishResult publish(OutboxEvent outboxEvent);
}