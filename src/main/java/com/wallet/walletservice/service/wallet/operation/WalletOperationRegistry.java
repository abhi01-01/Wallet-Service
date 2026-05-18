package com.wallet.walletservice.service.wallet.operation;

import com.wallet.walletservice.domain.enums.TransactionType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class WalletOperationRegistry {

    private final Map<TransactionType, WalletOperation<?>> operations;

    public WalletOperationRegistry(List<WalletOperation<?>> operations) {
        this.operations = new EnumMap<>(TransactionType.class);
        for (WalletOperation<?> operation : operations) {
            this.operations.put(operation.transactionType(), operation);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> WalletOperation<T> get(TransactionType transactionType) {
        WalletOperation<?> operation = operations.get(transactionType);
        if (operation == null) {
            throw new IllegalArgumentException("Unsupported wallet operation: " + transactionType);
        }
        return (WalletOperation<T>) operation;
    }
}
