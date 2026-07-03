package com.wallet.walletservice.dto.response.messaging;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.walletservice.messaging.kafka.audit.KafkaEventAudit;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record KafkaEventAuditResponse(
        Long id,
        UUID eventId,
        String eventType,
        Integer schemaVersion,
        String source,
        String aggregateType,
        String aggregateId,
        String topic,
        Integer partitionId,
        Long eventOffset,
        String eventKey,
        Map<String, Object> payload,
        Map<String, Object> headers,
        OffsetDateTime consumedAt
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    public static KafkaEventAuditResponse from(KafkaEventAudit audit){
        return new KafkaEventAuditResponse(
                audit.getId(),
                audit.getEventId(),
                audit.getEventType(),
                audit.getSchemaVersion(),
                audit.getSource(),
                audit.getAggregateType(),
                audit.getAggregateId(),
                audit.getTopic(),
                audit.getPartitionId(),
                audit.getEventOffset(),
                audit.getEventKey(),
                toMap(audit.getPayload()),
                toMap(audit.getHeaders()),
                audit.getConsumedAt()
        );
    }

    private static Map<String, Object> toMap(Object value) {
        if (value == null) {
            return Map.of();
        }

        return OBJECT_MAPPER.convertValue(value, MAP_TYPE);
    }
}