package com.wallet.walletservice.messaging.kafka.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KafkaEventAuditRepository extends JpaRepository<KafkaEventAudit, Long> {

    boolean existsByEventId(UUID eventId);

    boolean existsByTopicAndPartitionIdAndEventOffset(String topic, Integer partitionId, Long eventOffset);

    Optional<KafkaEventAudit> findByEventId(UUID eventId);

    Page<KafkaEventAudit> findByEventTypeOrderByConsumedAtDesc(String eventType, Pageable pageable);

    Page<KafkaEventAudit> findByAggregateTypeAndAggregateIdOrderByConsumedAtDesc(
            String aggregateType,
            String aggregateId,
            Pageable pageable
    );

    Page<KafkaEventAudit> findAllByOrderByConsumedAtDesc(Pageable pageable);

    long countByEventType(String eventType);
    Optional<KafkaEventAudit> findFirstByOrderByConsumedAtDesc();
}