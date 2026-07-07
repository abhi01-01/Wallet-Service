package com.wallet.walletservice.service.admin;

import com.wallet.walletservice.dto.response.messaging.MessagingSummaryResponse;
import com.wallet.walletservice.messaging.kafka.audit.KafkaEventAudit;
import com.wallet.walletservice.messaging.kafka.audit.KafkaEventAuditRepository;
import com.wallet.walletservice.messaging.outbox.OutboxEvent;
import com.wallet.walletservice.messaging.outbox.OutboxEventRepository;
import com.wallet.walletservice.messaging.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;

import static com.wallet.walletservice.messaging.event.WalletMessagingConstants.WALLET_TRANSACTION_POSTED_EVENT;

@Service
@RequiredArgsConstructor
public class AdminMessagingSummaryService {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaEventAuditRepository kafkaEventAuditRepository;

    @Transactional(readOnly = true)
    public MessagingSummaryResponse getSummary() {
        return new MessagingSummaryResponse(
                buildOutboxSummary(),
                buildKafkaAuditSummary()
        );
    }

    private MessagingSummaryResponse.OutboxSummary buildOutboxSummary(){

        Map<OutboxStatus, Long> countsByStatus = new EnumMap<>(OutboxStatus.class);

        for (OutboxStatus status : OutboxStatus.values()){
            countsByStatus.put(status, outboxEventRepository.countByStatus(status));
        }

        long total = countsByStatus.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum();

        long readyToPublish = outboxEventRepository.countReadyToPublish(OffsetDateTime.now());

        MessagingSummaryResponse.LatestOutboxEvent latestEvent = outboxEventRepository
                .findFirstByOrderByCreatedAtDesc()
                .map(this::toLatestOutboxEvent)
                .orElse(null);

        return new MessagingSummaryResponse.OutboxSummary(
                total,
                countsByStatus,
                readyToPublish,
                latestEvent
        );

    }

    private MessagingSummaryResponse.KafkaAuditSummary buildKafkaAuditSummary(){

        long totalConsumed = kafkaEventAuditRepository.count();

        long walletTransactionPostedConsumed = kafkaEventAuditRepository.countByEventType(WALLET_TRANSACTION_POSTED_EVENT);

        MessagingSummaryResponse.LatestKafkaAuditEvent lastestEvent = kafkaEventAuditRepository
                .findFirstByOrderByConsumedAtDesc()
                .map(this::toLatestKafkaAuditEvent)
                .orElse(null);

        return new MessagingSummaryResponse.KafkaAuditSummary(
                totalConsumed,
                walletTransactionPostedConsumed,
                lastestEvent
        );

    }

    // Supporting methods

    private MessagingSummaryResponse.LatestOutboxEvent toLatestOutboxEvent(OutboxEvent event){
        return new MessagingSummaryResponse.LatestOutboxEvent(
                event.getId(),
                event.getEventId(),
                event.getEventType(),
                event.getTopic(),
                event.getEventKey(),
                event.getStatus(),
                event.getPublishAttempts(),
                event.getCreatedAt(),
                event.getPublishedAt(),
                event.getLastError()
        );
    }

    private MessagingSummaryResponse.LatestKafkaAuditEvent toLatestKafkaAuditEvent(KafkaEventAudit audit){
        return new MessagingSummaryResponse.LatestKafkaAuditEvent(
                audit.getId(),
                audit.getEventId(),
                audit.getEventType(),
                audit.getTopic(),
                audit.getPartitionId(),
                audit.getEventOffset(),
                audit.getEventKey(),
                audit.getConsumedAt()
        );
    }
}