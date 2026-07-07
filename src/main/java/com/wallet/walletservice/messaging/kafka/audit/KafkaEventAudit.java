package com.wallet.walletservice.messaging.kafka.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;


@Entity
@Table(
        name = "kafka_event_audit",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_kafka_event_audit_event_id", columnNames = "event_id"),
                @UniqueConstraint(
                        name = "uk_kafka_event_audit_topic_partition_offset",
                        columnNames = {"topic", "partition_id", "event_offset"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KafkaEventAudit {

    @Id
    @Tsid
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(nullable = false, length = 120)
    private String source;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 120)
    private String aggregateId;

    @Column(nullable = false, length = 150)
    private String topic;

    @Column(name = "partition_id", nullable = false)
    private Integer partitionId;

    @Column(name = "event_offset", nullable = false)
    private Long eventOffset;

    @Column(name = "event_key", length = 150)
    private String eventKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private JsonNode headers = JsonNodeFactory.instance.objectNode();

    @Column(name = "consumed_at", nullable = false, updatable = false)
    private OffsetDateTime consumedAt;

    @PrePersist
    protected void onCreate() {
        if (this.consumedAt == null) {
            this.consumedAt = OffsetDateTime.now();
        }

        if (this.headers == null) {
            this.headers = JsonNodeFactory.instance.objectNode();
        }
    }
}