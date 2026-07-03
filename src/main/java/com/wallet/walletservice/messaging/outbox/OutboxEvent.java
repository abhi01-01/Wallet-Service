package com.wallet.walletservice.messaging.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 4_000;

    @Id
    @Tsid
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 80)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 120)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 120)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    @Builder.Default
    private Integer schemaVersion = 1;

    @Column(nullable = false, length = 150)
    private String topic;

    @Column(name = "event_key", nullable = false, length = 150)
    private String eventKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private JsonNode payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private JsonNode headers = JsonNodeFactory.instance.objectNode();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "publish_attempts", nullable = false)
    @Builder.Default
    private Integer publishAttempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "locked_by", length = 120)
    private String lockedBy;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate(){
        OffsetDateTime now = OffsetDateTime.now();

        if(this.eventId == null){
            this.eventId = UUID.randomUUID();
        }

        if(this.schemaVersion == null){
            this.schemaVersion = 1;
        }

        if(this.headers == null){
            this.headers = JsonNodeFactory.instance.objectNode();
        }

        if(this.status == null){
            this.status = OutboxStatus.PENDING;
        }

        if(this.publishAttempts == null){
            this.publishAttempts = 0 ;
        }

        if(this.nextAttemptAt == null){
            this.nextAttemptAt = now;
        }

        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate(){
        this.updatedAt = OffsetDateTime.now();
    }

    public void claimForPublishing(String publisherId){
        if (this.status != OutboxStatus.PENDING && this.status != OutboxStatus.FAILED) {
            throw new IllegalStateException("Only PENDING or FAILED outbox events can be claimed for publishing");
        }

        OffsetDateTime now = OffsetDateTime.now();

        this.status = OutboxStatus.PUBLISHING;
        this.lockedBy = publisherId;
        this.lockedAt = now;
        this.updatedAt = now;
    }

    public void markPublished() {
        OffsetDateTime now = OffsetDateTime.now();

        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = now;
        this.lockedBy = null;
        this.lockedAt = null;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void markPublishFailed(String errorMessage, int maxAttempts, Duration retryDelay){
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be greater than zero");
        }
        if (retryDelay == null || retryDelay.isNegative()) {
            throw new IllegalArgumentException("retryDelay must be zero or positive");
        }

        OffsetDateTime now = OffsetDateTime.now();

        this.publishAttempts = this.publishAttempts == null ? 1 : this.publishAttempts + 1;
        this.lastError = truncateError(errorMessage);
        this.lockedBy = null;
        this.lockedAt = null;
        this.updatedAt = now;

        if (this.publishAttempts >= maxAttempts) {
            this.status = OutboxStatus.DEAD;
            this.nextAttemptAt = now;
            return;
        }

        this.status = OutboxStatus.FAILED;
        this.nextAttemptAt = now.plus(retryDelay);
    }

    public boolean isTerminal() {
        return this.status == OutboxStatus.PUBLISHED || this.status == OutboxStatus.DEAD;
    }

    private String truncateError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }

        if (errorMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return errorMessage;
        }

        return errorMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

}