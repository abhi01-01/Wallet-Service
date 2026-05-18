package com.wallet.walletservice.service.wallet.operation;

import com.wallet.walletservice.domain.enums.TransactionType;
import com.wallet.walletservice.service.wallet.policy.WalletTransferPolicy;
import com.wallet.walletservice.service.wallet.transfer.TransferCommand;

import java.util.List;

public interface WalletOperation<T> {
    TransactionType transactionType();

    String contextLabel();

    TransferCommand toCommand(T request);

    default List<WalletTransferPolicy> policies() {
        return List.of();
    }
}
