package com.wallet.walletservice.messaging.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stable Kafka message envelope. Outbox columns duplicate these values for fast
 * querying, while the payload stores the full envelope that downstream consumers
 * receive.
 */

public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        Integer schemaVersion,
        String source,
        OffsetDateTime occurredAt,
        String aggregateType,
        String aggregateId,
        T data
) {
}