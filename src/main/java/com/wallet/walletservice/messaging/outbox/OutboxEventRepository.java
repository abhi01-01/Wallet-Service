package com.wallet.walletservice.messaging.outbox;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByEventId(UUID eventId);

    Optional<OutboxEvent> findFirstByOrderByCreatedAtDesc();
    Optional<OutboxEvent> findFirstByStatusOrderByCreatedAtDesc(OutboxStatus status);
    @Query(value = """
        SELECT COUNT(*)
        FROM outbox_events
        WHERE status IN ('PENDING', 'FAILED')
          AND next_attempt_at <= :now
        """, nativeQuery = true)
    long countReadyToPublish(@Param("now") OffsetDateTime now);

    boolean existsByEventId(UUID eventId);

    long countByStatus(OutboxStatus status);

    Page<OutboxEvent> findByStatusOrderByCreatedAtDesc(OutboxStatus status, Pageable pageable);

    Page<OutboxEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /*
     * Publisher hot path for the Transactional Outbox pattern.
     *
     * This method must be called inside an active transaction. FOR UPDATE locks the
     * selected rows, and SKIP LOCKED allows multiple app instances to poll without
     * publishing the same event twice.
     *
     * FAILED events are included only when next_attempt_at is due, so retry backoff
     * can be implemented by updating next_attempt_at after each failed publish.
     */

    @Query(value = """
            SELECT *
                FROM outbox_events
                WHERE status IN ('PENDING','FAILED')
                AND next_attempt_at <= NOW()
                ORDER BY next_attempt_at ASC, created_at ASC
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                \s""", nativeQuery = true)
    List<OutboxEvent> findReadyToPublishForUpdate(@Param("limit") int limit);

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE aggregate_type = :aggregateType
            AND aggregate_id = :aggregateId
            ORDER BY created_at DESC\s
           \s""", nativeQuery = true)
    List<OutboxEvent> findByAggregate(
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") String aggregateId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        UPDATE outbox_events
        SET status = 'FAILED',
            locked_by = NULL,
            locked_at = NULL,
            last_error = CONCAT('Recovered stale PUBLISHING event locked before ', CAST(:cutoff AS TEXT)),
            next_attempt_at = NOW(),
            updated_at = NOW()
        WHERE status = 'PUBLISHING'
          AND locked_at IS NOT NULL
          AND locked_at < :cutoff
        """, nativeQuery = true)
    int recoverStalePublishingEvents(@Param("cutoff") OffsetDateTime cutoff);

}