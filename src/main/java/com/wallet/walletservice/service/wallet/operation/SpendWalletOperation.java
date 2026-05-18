package com.wallet.walletservice.service.wallet.operation;

import com.wallet.walletservice.domain.enums.TransactionType;
import com.wallet.walletservice.dto.request.SpendRequest;
import com.wallet.walletservice.service.wallet.policy.SufficientBalancePolicy;
import com.wallet.walletservice.service.wallet.policy.WalletTransferPolicy;
import com.wallet.walletservice.service.wallet.support.WalletProvider;
import com.wallet.walletservice.service.wallet.transfer.TransferCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SpendWalletOperation implements WalletOperation<SpendRequest> {

    private final SufficientBalancePolicy sufficientBalancePolicy;

    @Override
    public TransactionType transactionType() {
        return TransactionType.SPEND;
    }

    @Override
    public String contextLabel() {
        return "Spend";
    }

    @Override
    public TransferCommand toCommand(SpendRequest request) {
        return TransferCommand.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .assetCode(request.getAssetCode())
                .debitOwnerId(request.getUserId())
                .creditOwnerId(WalletProvider.SYSTEM_TREASURY)
                .amount(request.getAmount())
                .transactionType(TransactionType.SPEND)
                .providedDescription(request.getDescription())
                .defaultDescriptionSupplier(() -> String.format(
                        "Spend: %s %s by user %s",
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
