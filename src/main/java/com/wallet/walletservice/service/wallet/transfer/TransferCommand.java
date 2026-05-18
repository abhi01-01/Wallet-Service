package com.wallet.walletservice.service.wallet.transfer;

import com.wallet.walletservice.domain.enums.TransactionType;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.function.Supplier;

@Value
@Builder
public class TransferCommand {
    String idempotencyKey;
    String assetCode;
    String debitOwnerId;
    String creditOwnerId;
    BigDecimal amount;
    TransactionType transactionType;
    String providedDescription;
    Supplier<String> defaultDescriptionSupplier;
}
