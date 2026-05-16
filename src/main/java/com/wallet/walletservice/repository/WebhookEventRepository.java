package com.wallet.walletservice.repository;

import com.wallet.walletservice.domain.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    Optional<WebhookEvent> findByEventId(String eventId);

    /*
     * Polling mechanism for the Inbox pattern.
     * SKIP LOCKED ensures multiple threads/pods do not process the same event simultaneously.
     * Native query is required because JPQL does not consistently support SKIP LOCKED across all dialects.
     */

    @Query(value = """
            SELECT * FROM webhook_events 
            WHERE status = 'RECEIVED' OR (status = 'FAILED' AND processing_attempts < :maxAttempts)
            ORDER BY received_at ASC 
            LIMIT 1 
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<WebhookEvent> findNextAvailableEvent(@Param("maxAttempts") int maxAttempts);
}
