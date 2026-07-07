package com.wallet.walletservice.dto.response.messaging;

import com.wallet.walletservice.messaging.outbox.OutboxStatus;
import org.apache.kafka.common.protocol.types.Field;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record MessagingSummaryResponse(
        OutboxSummary outbox,
        KafkaAuditSummary kafkaAudit
) {

    public record OutboxSummary(
            long total,
            Map<OutboxStatus, Long> countsByStatus,
            long readyToPublish,
            LatestOutboxEvent latestEvent
    ) {
    }

    public record KafkaAuditSummary(
            long totalConsumed,
            long walletTransactionPostedConsumed,
            LatestKafkaAuditEvent latestEvent
    ) {
    }

    public record LatestOutboxEvent(
            Long id,
            UUID eventId,
            String eventType,
            String topic,
            String eventKey,
            OutboxStatus status,
            Integer publishAttempts,
            OffsetDateTime createdAt,
            OffsetDateTime publishedAt,
            String lastError
    ) {
    }

    public record LatestKafkaAuditEvent(
            Long id,
            UUID eventId,
            String eventType,
            String topic,
            Integer partitionId,
            Long eventOffset,
            String eventKey,
            OffsetDateTime consumedAt
    ) {
    }
}