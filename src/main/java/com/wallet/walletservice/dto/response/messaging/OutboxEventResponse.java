package com.wallet.walletservice.dto.response.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.walletservice.messaging.outbox.OutboxEvent;
import com.wallet.walletservice.messaging.outbox.OutboxStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record OutboxEventResponse(
        Long id,
        UUID eventId,
        String aggregateType,
        String aggregateId,
        String eventType,
        Integer schemaVersion,
        String topic,
        String eventKey,
        Map<String, Object> payload,
        Map<String, Object> headers,
        OutboxStatus status,
        Integer publishAttempts,
        OffsetDateTime nextAttemptAt,
        String lockedBy,
        OffsetDateTime lockedAt,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt,
        OffsetDateTime updatedAt
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    public static OutboxEventResponse from(OutboxEvent event){
        return new OutboxEventResponse(
                event.getId(),
                event.getEventId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getSchemaVersion(),
                event.getTopic(),
                event.getEventKey(),
                toMap(event.getPayload()),
                toMap(event.getHeaders()),
                event.getStatus(),
                event.getPublishAttempts(),
                event.getNextAttemptAt(),
                event.getLockedBy(),
                event.getLockedAt(),
                event.getLastError(),
                event.getCreatedAt(),
                event.getPublishedAt(),
                event.getUpdatedAt()
        );
    }

    private static Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Map.of();
        }

        return OBJECT_MAPPER.convertValue(value, MAP_TYPE);
    }

}