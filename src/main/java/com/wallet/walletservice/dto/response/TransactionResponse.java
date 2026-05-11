package com.wallet.walletservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data @Builder
public class TransactionResponse {
    private Long transactionId;
    private String idempotencyKey;
    private String type;
    private String status;
    private String description;
    private OffsetDateTime createdAt;
    private List<LedgerEntryDto> ledgerEntries;

    @Data
    @Builder
    public static class LedgerEntryDto {
        private Long entryId;
        private Long walletId;
        private String ownerId;
        private String entryType;   // DEBIT | CREDIT
        private BigDecimal amount;
        private BigDecimal balanceAfter;
    }
}
