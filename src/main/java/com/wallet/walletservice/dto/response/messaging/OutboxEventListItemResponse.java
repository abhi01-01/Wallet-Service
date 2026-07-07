package com.wallet.walletservice.dto.response.messaging;

import com.wallet.walletservice.messaging.outbox.OutboxEvent;
import com.wallet.walletservice.messaging.outbox.OutboxStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboxEventListItemResponse(
        Long id,
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        Integer schemaVersion,
        String topic,
        String eventKey,
        OutboxStatus status,
        Integer publishAttempts,
        OffsetDateTime nextAttemptAt,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt,
        String lastError
) {

    public static OutboxEventListItemResponse from(OutboxEvent event) {
        return new OutboxEventListItemResponse(
                event.getId(),
                event.getEventId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getSchemaVersion(),
                event.getTopic(),
                event.getEventKey(),
                event.getStatus(),
                event.getPublishAttempts(),
                event.getNextAttemptAt(),
                event.getCreatedAt(),
                event.getPublishedAt(),
                event.getLastError()
        );
    }
}