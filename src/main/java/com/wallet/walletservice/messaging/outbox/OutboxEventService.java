package com.wallet.walletservice.messaging.outbox;

import com.wallet.walletservice.domain.entity.Transaction;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;

    @Value("${wallet.outbox.publisher.max-attempts:5}")
    private int maxAttempts;

    @Value("${wallet.outbox.publisher.retry-delay-ms:5000}")
    private long retryDelayMs;

    @Value("${wallet.outbox.publisher.stale-lock-timeout-ms:120000}")
    private long staleLockTimeoutMs;

    /*
     * Must run inside the same DB transaction that creates the wallet mutation,
     * transaction row, and ledger entries.
     */

    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent recordWalletTransactionPosted(Transaction transaction){
        return outboxEventRepository.save(outboxEventFactory.walletTransactionalPosted(transaction));
    }

    /*
     * Claims publishable rows using FOR UPDATE SKIP LOCKED.
     * This method must stay transactional so the database row locks are meaningful.
     */
    @Transactional
    public List<OutboxEvent> claimReadyToPublish(int limit, String publisherId) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Outbox publish batch limit must be greater than zero");
        }

        List<OutboxEvent> events = outboxEventRepository.findReadyToPublishForUpdate(limit);

        events.forEach(event -> event.claimForPublishing(publisherId));

        return events;
    }

    @Transactional
    public void markPublished(Long outboxEventId) {
        OutboxEvent event = getRequired(outboxEventId);

        if (event.isTerminal()) {
            return;
        }

        event.markPublished();
    }

    @Transactional
    public void markFailed(Long outboxEventId, Throwable throwable) {
        OutboxEvent event = getRequired(outboxEventId);

        if (event.isTerminal()) {
            return;
        }

        event.markPublishFailed(resolveErrorMessage(throwable), maxAttempts, Duration.ofMillis(retryDelayMs));
    }

    @Transactional
    public int recoverStalePublishingEvents() {
        if (staleLockTimeoutMs <= 0) {
            return 0;
        }

        OffsetDateTime cutoff = OffsetDateTime.now().minus(Duration.ofMillis(staleLockTimeoutMs));
        return outboxEventRepository.recoverStalePublishingEvents(cutoff);
    }

    private OutboxEvent getRequired(Long outboxEventId) {
        return outboxEventRepository.findById(outboxEventId)
                .orElseThrow(() -> new EntityNotFoundException("Outbox event not found: " + outboxEventId));
    }

    private String resolveErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown Kafka publish failure";
        }

        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return throwable.getClass().getSimpleName() + ": " + message;
    }



}