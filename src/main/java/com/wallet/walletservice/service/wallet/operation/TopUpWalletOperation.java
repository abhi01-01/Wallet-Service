package com.wallet.walletservice.service.wallet.operation;

import com.wallet.walletservice.domain.enums.TransactionType;
import com.wallet.walletservice.dto.request.TopUpRequest;
import com.wallet.walletservice.service.wallet.policy.SufficientBalancePolicy;
import com.wallet.walletservice.service.wallet.policy.WalletTransferPolicy;
import com.wallet.walletservice.service.wallet.support.WalletProvider;
import com.wallet.walletservice.service.wallet.transfer.TransferCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TopUpWalletOperation implements WalletOperation<TopUpRequest> {

    private final SufficientBalancePolicy sufficientBalancePolicy;

    @Override
    public TransactionType transactionType() {
        return TransactionType.TOPUP;
    }

    @Override
    public String contextLabel() {
        return "TopUp";
    }

    @Override
    public TransferCommand toCommand(TopUpRequest request) {
        return TransferCommand.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .assetCode(request.getAssetCode())
                .debitOwnerId(WalletProvider.SYSTEM_TREASURY)
                .creditOwnerId(request.getUserId())
                .amount(request.getAmount())
                .transactionType(TransactionType.TOPUP)
                .providedDescription(request.getDescription())
                .defaultDescriptionSupplier(() -> String.format(
                        "Top-up: %s %s for user %s",
                        request.getAmount(),
                        request.getAssetCode(),
                        request.getUserId()))
                .build();
    }

    @Override
    public List<WalletTransferPolicy> policies() {
        return List.of(sufficientBalancePolicy);
    }
}
