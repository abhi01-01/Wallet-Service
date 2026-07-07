package com.wallet.walletservice.messaging.kafka.audit;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.kafka.support.Acknowledgment;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "wallet.kafka.consumer",
        name = "audit-enabled",
        havingValue = "true"
)
public class WalletTransactionAuditConsumer {

    private final KafkaEventAuditService kafkaEventAuditService;

    @PostConstruct
    void init() {
        log.info("WalletTransactionAuditConsumer initialized and enabled");
    }

    @KafkaListener(
            topics = "${wallet.kafka.topics.wallet-transaction-events}",
            containerFactory = "walletStringKafkaListenerContainerFactory"
    )
    public void consumeWalletTransactionEvent(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment
    ){
        log.info(
                "Received wallet transaction Kafka event. topic={}, partition={}, offset={}, key={}",
                record.topic(),
                record.partition(),
                record.offset(),
                record.key()
        );

        kafkaEventAuditService.audit(record);

        /*
         * Manual ack happens only after DB audit insert succeeds or duplicate is safely detected.
         */
        acknowledgment.acknowledge();

        log.info(
                "Acknowledged wallet transaction Kafka event. topic={}, partition={}, offset={}",
                record.topic(),
                record.partition(),
                record.offset()
        );
    }
}
