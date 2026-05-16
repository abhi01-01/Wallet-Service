package com.wallet.walletservice.domain.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.wallet.walletservice.domain.enums.WebhookStatus;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "webhook_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WebhookEvent {

    @Id
    @Tsid
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 255)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "order_id", length = 255)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WebhookStatus status = WebhookStatus.RECEIVED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;

    @Column(name = "processing_attempts", nullable = false)
    @Builder.Default
    private Integer processingAttempts = 0;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "received_at", updatable = false, nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.receivedAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
