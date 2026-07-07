package com.wallet.walletservice.messaging.kafka.audit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class WalletTransactionAuditConsumerTest {

    private KafkaEventAuditService kafkaEventAuditService;
    private WalletTransactionAuditConsumer walletTransactionAuditConsumer;

    @BeforeEach
    void setUp() {
        kafkaEventAuditService = mock(KafkaEventAuditService.class);
        walletTransactionAuditConsumer = new WalletTransactionAuditConsumer(kafkaEventAuditService);
    }

    @Test
    void consumeWalletTransactionEvent_WhenAuditSucceeds_AcknowledgesOffset() {
        ConsumerRecord<String, String> record = record();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        walletTransactionAuditConsumer.consumeWalletTransactionEvent(record, acknowledgment);

        verify(kafkaEventAuditService).audit(record);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void consumeWalletTransactionEvent_WhenAuditFails_DoesNotAcknowledgeOffset() {
        ConsumerRecord<String, String> record = record();
        Acknowledgment acknowledgment = mock(Acknowledgment.class);

        doThrow(new IllegalArgumentException("bad payload"))
                .when(kafkaEventAuditService)
                .audit(record);

        assertThrows(
                IllegalArgumentException.class,
                () -> walletTransactionAuditConsumer.consumeWalletTransactionEvent(record, acknowledgment)
        );

        verify(kafkaEventAuditService).audit(record);
        verify(acknowledgment, never()).acknowledge();
    }

    private ConsumerRecord<String, String> record() {
        return new ConsumerRecord<>(
                "wallet.transaction.events.v1",
                0,
                10L,
                "user-1",
                """
                {
                  "eventId": "0130d8e1-1c5f-4509-8c6b-63ee6ff429cd",
                  "eventType": "wallet.transaction.posted.v1",
                  "schemaVersion": 1,
                  "source": "wallet-service",
                  "aggregateType": "TRANSACTION",
                  "aggregateId": "txn-123",
                  "data": {}
                }
                """
        );
    }

}