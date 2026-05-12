package com.wallet.walletservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;


@Data @Builder
@Schema(description = "Response representing a single ledger entry in history")
public class LedgerHistoryResponse {
    @Schema(description = "Internal entry ID", example = "100")
    private Long entryId;

    @Schema(description = "Associated transaction ID", example = "50")
    private Long transactionId;

    @Schema(description = "Type of the transaction", example = "SPEND")
    private String transactionType;

    @Schema(description = "Entry type (CREDIT/DEBIT)", example = "DEBIT")
    private String entryType;

    @Schema(description = "Amount of the entry", example = "10.0")
    private BigDecimal amount;

    @Schema(description = "Wallet balance after this entry", example = "90.0")
    private BigDecimal balanceAfter;

    @Schema(description = "Timestamp of the entry")
    private OffsetDateTime createdAt;
}
