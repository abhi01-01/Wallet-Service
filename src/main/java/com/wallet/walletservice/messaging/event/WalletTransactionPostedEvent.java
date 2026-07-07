package com.wallet.walletservice.messaging.event;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record WalletTransactionPostedEvent(
        Long transactionId,
        String transactionType,
        String transactionStatus,
        String idempotencyKey,
        String description,
        String assetCode,
        BigDecimal amount,
        OffsetDateTime occurredAt,
        List<LedgerEntrySnapshot> entries
) {

    public record LedgerEntrySnapshot(
            Long ledgerEntryId,
            Long walletId,
            String ownerId,
            String ownerType,
            String assetCode,
            String entryType,
            BigDecimal amount,
            BigDecimal balanceAfter,
            OffsetDateTime createdAt
    ){
    }
}