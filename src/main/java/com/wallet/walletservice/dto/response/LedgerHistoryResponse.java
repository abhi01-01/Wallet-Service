package com.wallet.walletservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Data @Builder
public class LedgerHistoryResponse {
    private Long entryId;
    private Long transactionId;
    private String transactionType;
    private String entryType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private OffsetDateTime createdAt;
}
