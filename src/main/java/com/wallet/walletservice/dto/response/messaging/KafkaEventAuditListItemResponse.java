package com.wallet.walletservice.dto.response.messaging;

import com.wallet.walletservice.messaging.kafka.audit.KafkaEventAudit;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KafkaEventAuditListItemResponse(
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
        OffsetDateTime consumedAt
) {

    public static KafkaEventAuditListItemResponse from(KafkaEventAudit audit) {
        return new KafkaEventAuditListItemResponse(
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
                audit.getConsumedAt()
        );
    }
}