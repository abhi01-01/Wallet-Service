package com.wallet.walletservice.messaging.outbox;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.walletservice.domain.entity.AssetType;
import com.wallet.walletservice.domain.entity.LedgerEntry;
import com.wallet.walletservice.domain.entity.Transaction;
import com.wallet.walletservice.domain.entity.Wallet;
import com.wallet.walletservice.domain.enums.EntryType;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.domain.enums.TransactionStatus;
import com.wallet.walletservice.domain.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static com.wallet.walletservice.messaging.event.WalletMessagingConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OutboxEventFactoryTest{

    private OutboxEventFactory outboxEventFactory;

    @BeforeEach
    void setUp(){
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        outboxEventFactory = new OutboxEventFactory(objectMapper);
    }

    @Test
    void walletTransactionPosted_BuildsStableOutboxEventEnvelope(){
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-06-29T10:15:30+05:30");
        Transaction transaction = transaction(123L, occurredAt);

        OutboxEvent outboxEvent = outboxEventFactory.walletTransactionalPosted(transaction);

        assertNotNull(outboxEvent.getEventId());
        assertEquals(AGGREGATE_TYPE_TRANSACTION, outboxEvent.getAggregateType());
        assertEquals("123", outboxEvent.getAggregateId());
        assertEquals(WALLET_TRANSACTION_POSTED_EVENT, outboxEvent.getEventType());
        assertEquals(WALLET_TRANSACTION_POSTED_SCHEMA_VERSION, outboxEvent.getSchemaVersion());
        assertEquals(WALLET_TRANSACTION_EVENTS_TOPIC, outboxEvent.getTopic());
        assertEquals("user-1", outboxEvent.getEventKey());
        assertEquals(OutboxStatus.PENDING, outboxEvent.getStatus());
        assertEquals(0, outboxEvent.getPublishAttempts());
        assertNotNull(outboxEvent.getNextAttemptAt());

        JsonNode payload = outboxEvent.getPayload();
        assertEquals(outboxEvent.getEventId().toString(), payload.get("eventId").asText());
        assertEquals(WALLET_TRANSACTION_POSTED_EVENT, payload.get("eventType").asText());
        assertEquals(WALLET_TRANSACTION_POSTED_SCHEMA_VERSION, payload.get("schemaVersion").asInt());
        assertEquals(SOURCE_SERVICE, payload.get("source").asText());
        assertEquals(AGGREGATE_TYPE_TRANSACTION, payload.get("aggregateType").asText());
        assertEquals("123", payload.get("aggregateId").asText());

        JsonNode data = payload.get("data");
        assertEquals(123L, data.get("transactionId").asLong());
        assertEquals(TransactionType.TOPUP.name(), data.get("transactionType").asText());
        assertEquals(TransactionStatus.SUCCESS.name(), data.get("transactionStatus").asText());
        assertEquals("key-1", data.get("idempotencyKey").asText());
        assertEquals("Top-up", data.get("description").asText());
        assertEquals("GOLD", data.get("assetCode").asText());
        assertEquals(2, data.get("entries").size());
        assertEquals(EntryType.DEBIT.name(), data.get("entries").get(0).get("entryType").asText());
        assertEquals(EntryType.CREDIT.name(), data.get("entries").get(1).get("entryType").asText());

        JsonNode headers = outboxEvent.getHeaders();
        assertEquals(outboxEvent.getEventId().toString(), headers.get(HEADER_EVENT_ID).asText());
        assertEquals(WALLET_TRANSACTION_POSTED_EVENT, headers.get(HEADER_EVENT_TYPE).asText());
        assertEquals(SOURCE_SERVICE, headers.get(HEADER_SOURCE).asText());
    }

    private Transaction transaction(Long transactionId, OffsetDateTime occurredAt){

        AssetType assetType = AssetType.builder()
                .id(100L)
                .name("Gold")
                .code("GOLD")
                .build();

        Wallet debitWallet = Wallet.builder()
                .id(10L)
                .ownerId("SYSTEM_TREASURY")
                .ownerType(OwnerType.SYSTEM)
                .assetType(assetType)
                .balance(new BigDecimal("95.0000"))
                .build();

        Wallet creditWallet = Wallet.builder()
                .id(11L)
                .ownerId("user-1")
                .ownerType(OwnerType.USER)
                .assetType(assetType)
                .balance(new BigDecimal("15.0000"))
                .build();

        Transaction transaction = Transaction.builder()
                .id(transactionId)
                .idempotencyKey("key-1")
                .transactionType(TransactionType.TOPUP)
                .description("Top-up")
                .status(TransactionStatus.SUCCESS)
                .createdAt(occurredAt)
                .build();

        LedgerEntry debitEntry = LedgerEntry.builder()
                .id(1001L)
                .transaction(transaction)
                .wallet(debitWallet)
                .entryType(EntryType.DEBIT)
                .amount(new BigDecimal("5.0000"))
                .balanceAfter(new BigDecimal("95.0000"))
                .createdAt(occurredAt)
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .id(1002L)
                .transaction(transaction)
                .wallet(creditWallet)
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("5.0000"))
                .balanceAfter(new BigDecimal("15.0000"))
                .createdAt(occurredAt)
                .build();

        transaction.getLedgerEntries().addAll(List.of(debitEntry, creditEntry));
        return transaction;
    }

}