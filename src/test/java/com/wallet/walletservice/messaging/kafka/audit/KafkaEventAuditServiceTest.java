package com.wallet.walletservice.messaging.kafka.audit;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class KafkaEventAuditServiceTest {

    private KafkaEventAuditRepository kafkaEventAuditRepository;
    private KafkaEventAuditService kafkaEventAuditService;

    @BeforeEach
    void setUp(){
        kafkaEventAuditRepository = mock(KafkaEventAuditRepository.class);
        kafkaEventAuditService = new KafkaEventAuditService(
                kafkaEventAuditRepository,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void audit_WhenEventIsNew_SavesKafkaEventAudit(){
        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, String> record = record(eventId, 0, 15L);

        when(kafkaEventAuditRepository.existsByEventId(eventId)).thenReturn(false);
        when(kafkaEventAuditRepository.existsByTopicAndPartitionIdAndEventOffset(
                "wallet.transaction.events.v1",
                0,
                15L
        )).thenReturn(false);

        kafkaEventAuditService.audit(record);

        verify(kafkaEventAuditRepository).save(argThat(audit ->
                audit.getEventId().equals(eventId)
                        && audit.getEventType().equals("wallet.transaction.posted.v1")
                        && audit.getSchemaVersion().equals(1)
                        && audit.getSource().equals("wallet-service")
                        && audit.getAggregateType().equals("TRANSACTION")
                        && audit.getAggregateId().equals("txn-123")
                        && audit.getTopic().equals("wallet.transaction.events.v1")
                        && audit.getPartitionId().equals(0)
                        && audit.getEventOffset().equals(15L)
                        && audit.getEventKey().equals("user-1")
                        && audit.getPayload() != null
                        && audit.getHeaders() != null
        ));
    }

    @Test
    void audit_WhenEventIdAlreadyExists_DoesNotSaveAgain(){
        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, String> record = record(eventId, 0, 15L);

        when(kafkaEventAuditRepository.existsByEventId(eventId)).thenReturn(true);

        kafkaEventAuditService.audit(record);

        verify(kafkaEventAuditRepository, never()).save(any());
        verify(kafkaEventAuditRepository, never())
                .existsByTopicAndPartitionIdAndEventOffset(anyString(), anyInt(), anyLong());
    }

    @Test
    void audit_WhenTopicPartitionOffsetAlreadyExists_DoesNotSaveAgain(){
        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, String> record = record(eventId, 0, 15L);

        when(kafkaEventAuditRepository.existsByEventId(eventId)).thenReturn(false);
        when(kafkaEventAuditRepository.existsByTopicAndPartitionIdAndEventOffset(
                "wallet.transaction.events.v1",
                0,
                15L
        )).thenReturn(true);

        kafkaEventAuditService.audit(record);

        verify(kafkaEventAuditRepository, never()).save(any());
    }

    @Test
    void audit_WhenUniqueConstraintCollisionHappens_DoesNotThrow(){
        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, String> record = record(eventId, 0, 15L);

        when(kafkaEventAuditRepository.existsByEventId(eventId)).thenReturn(false);
        when(kafkaEventAuditRepository.existsByTopicAndPartitionIdAndEventOffset(
                "wallet.transaction.events.v1",
                0,
                15L
        )).thenReturn(false);

        when(kafkaEventAuditRepository.save(any(KafkaEventAudit.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertDoesNotThrow(() -> kafkaEventAuditService.audit(record));

        verify(kafkaEventAuditRepository).save(any(KafkaEventAudit.class));
    }

    @Test
    void audit_WhenPayloadIsMalformed_ThrowsAndDoesNotSave() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "wallet.transaction.events.v1",
                0,
                15L,
                "user-1",
                "not-json"
        );

        assertThrows(IllegalArgumentException.class, () -> kafkaEventAuditService.audit(record));

        verify(kafkaEventAuditRepository, never()).save(any());
    }

    @Test
    void audit_WhenRequiredFieldMissing_ThrowsAndDoesNotSave() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "wallet.transaction.events.v1",
                0,
                15L,
                "user-1",
                """
                {
                  "eventType": "wallet.transaction.posted.v1",
                  "schemaVersion": 1,
                  "source": "wallet-service",
                  "aggregateType": "TRANSACTION",
                  "aggregateId": "txn-123",
                  "data": {}
                }
                """
        );

        assertThrows(IllegalArgumentException.class, () -> kafkaEventAuditService.audit(record));

        verify(kafkaEventAuditRepository, never()).save(any());
    }


    // Supporting Methods
    private ConsumerRecord<String, String> record(UUID eventId, int partition, long offset){
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "wallet.transaction.events.v1",
                partition,
                offset,
                "user-1",
                """
                        {
                           "eventId": "%s",
                           "eventType": "wallet.transaction.posted.v1",
                           "schemaVersion": 1,
                           "source": "wallet-service",
                           "occurredAt": "2026-07-01T23:22:46.471504+05:30",
                           "aggregateType": "TRANSACTION",
                           "aggregateId": "txn-123",
                           "data": {
                             "transactionId": 123,
                             "transactionType": "BONUS",
                             "transactionStatus": "SUCCESS",
                             "idempotencyKey": "test-key",
                             "assetCode": "LOYALTY",
                             "amount": 1000,
                             "entries": [
                               {
                                 "entryType": "DEBIT"
                               },
                               {
                                 "entryType": "CREDIT"
                               }
                             ]
                           }
                         }
                        \s""".formatted(eventId)
        );

        record.headers().add("event_id", eventId.toString().getBytes(StandardCharsets.UTF_8));
        record.headers().add("event_type", "wallet.transaction.posted.v1".getBytes(StandardCharsets.UTF_8));
        record.headers().add("source", "wallet-service".getBytes(StandardCharsets.UTF_8));

        return record;
    }


}