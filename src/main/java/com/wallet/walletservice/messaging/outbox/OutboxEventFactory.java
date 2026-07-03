package com.wallet.walletservice.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wallet.walletservice.domain.entity.LedgerEntry;
import com.wallet.walletservice.domain.entity.Transaction;
import com.wallet.walletservice.domain.entity.Wallet;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.messaging.event.EventEnvelope;
import com.wallet.walletservice.messaging.event.WalletTransactionPostedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import static com.wallet.walletservice.messaging.event.WalletMessagingConstants.*;

@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public OutboxEvent walletTransactionalPosted(Transaction transaction){
        validateTransaction(transaction);

        UUID eventId = UUID.randomUUID();
        OffsetDateTime occurredAt = resolveOccurredAt(transaction);
        String aggregateId = String.valueOf(transaction.getId());
        String eventKey = resolveEventKey(transaction, aggregateId);

        WalletTransactionPostedEvent eventPayload = buildWalletTransactionPostedEvent(transaction, occurredAt);

        EventEnvelope<WalletTransactionPostedEvent> envelope = new EventEnvelope<>(
                eventId,
                WALLET_TRANSACTION_POSTED_EVENT,
                WALLET_TRANSACTION_POSTED_SCHEMA_VERSION,
                SOURCE_SERVICE,
                occurredAt,
                AGGREGATE_TYPE_TRANSACTION,
                aggregateId,
                eventPayload
        );

        return OutboxEvent.builder()
                .eventId(eventId)
                .aggregateType(AGGREGATE_TYPE_TRANSACTION)
                .aggregateId(aggregateId)
                .eventType(WALLET_TRANSACTION_POSTED_EVENT)
                .schemaVersion(WALLET_TRANSACTION_POSTED_SCHEMA_VERSION)
                .topic(WALLET_TRANSACTION_EVENTS_TOPIC)
                .eventKey(eventKey)
                .payload(objectMapper.valueToTree(envelope))
                .headers(buildHeaders(envelope))
                .status(OutboxStatus.PENDING)
                .publishAttempts(0)
                .nextAttemptAt(OffsetDateTime.now())
                .build();
    }

    private WalletTransactionPostedEvent buildWalletTransactionPostedEvent(
            Transaction transaction,
            OffsetDateTime occurredAt
    ){
        LedgerEntry firstEntry = transaction.getLedgerEntries().get(0);

        return new WalletTransactionPostedEvent(
                transaction.getId(),
                transaction.getTransactionType().name(),
                transaction.getStatus().name(),
                transaction.getIdempotencyKey(),
                transaction.getDescription(),
                resolveAssetCode(firstEntry),
                firstEntry.getAmount(),
                occurredAt,
                transaction.getLedgerEntries()
                        .stream()
                        .map(this::toLedgerEntrySnapshot)
                        .toList()
        );
    }

    private WalletTransactionPostedEvent.LedgerEntrySnapshot toLedgerEntrySnapshot(LedgerEntry entry){
        Wallet wallet = requireWallet(entry);

        return new WalletTransactionPostedEvent.LedgerEntrySnapshot(
                entry.getId(),
                wallet.getId(),
                wallet.getOwnerId(),
                wallet.getOwnerType().name(),
                resolveAssetCode(entry),
                entry.getEntryType().name(),
                entry.getAmount(),
                entry.getBalanceAfter(),
                entry.getCreatedAt()
        );
    }

    private ObjectNode buildHeaders(EventEnvelope<?> envelope){
        ObjectNode headers = JsonNodeFactory.instance.objectNode();
        headers.put(HEADER_EVENT_ID, envelope.eventId().toString());
        headers.put(HEADER_EVENT_TYPE, envelope.eventType());
        headers.put(HEADER_SCHEMA_VERSION, envelope.schemaVersion());
        headers.put(HEADER_SOURCE, envelope.source());
        headers.put(HEADER_AGGREGATE_TYPE, envelope.aggregateType());
        headers.put(HEADER_AGGREGATE_ID, envelope.aggregateId());
        return headers;
    }

    private String resolveEventKey(Transaction transaction, String fallbackAggregatedId){
        return transaction.getLedgerEntries()
                .stream()
                .map(LedgerEntry::getWallet)
                .filter(wallet -> wallet.getOwnerType() == OwnerType.USER)
                .map(Wallet::getOwnerId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(fallbackAggregatedId);
    }

    private String resolveAssetCode(LedgerEntry entry){
        Wallet wallet = requireWallet(entry);

        if(wallet.getAssetType() == null || wallet.getAssetType().getCode() == null){
            throw new IllegalArgumentException("Ledger entry wallet asset code/type must be present for outbox pattern");
        }

        return wallet.getAssetType().getCode();
    }

    private Wallet requireWallet(LedgerEntry entry){
        if(entry == null || entry.getWallet() == null){
            throw new IllegalArgumentException("Ledger entry wallet must be present for outbox event");
        }

        return entry.getWallet();
    }

    private OffsetDateTime resolveOccurredAt(Transaction transaction){
        return transaction.getCreatedAt() != null ? transaction.getCreatedAt() : OffsetDateTime.now();
    }

    private void validateTransaction(Transaction transaction){
        if(transaction == null){
            throw new IllegalArgumentException("Transaction must be present for outbox event");
        }
        if(transaction.getId() == null){
            throw new IllegalArgumentException("Persisted transaction id must be present for outbox event");
        }
        if(transaction.getLedgerEntries() == null || transaction.getLedgerEntries().isEmpty()){
            throw new IllegalArgumentException("Transaction ledger entries must be present for outbox event");
        }
    }
}