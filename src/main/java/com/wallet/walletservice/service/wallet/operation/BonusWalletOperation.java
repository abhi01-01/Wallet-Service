package com.wallet.walletservice.service.wallet.operation;

import com.wallet.walletservice.domain.enums.TransactionType;
import com.wallet.walletservice.dto.request.BonusRequest;
import com.wallet.walletservice.service.wallet.policy.SufficientBalancePolicy;
import com.wallet.walletservice.service.wallet.policy.WalletTransferPolicy;
import com.wallet.walletservice.service.wallet.support.WalletProvider;
import com.wallet.walletservice.service.wallet.transfer.TransferCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BonusWalletOperation implements WalletOperation<BonusRequest> {

    private final SufficientBalancePolicy sufficientBalancePolicy;

    @Override
    public TransactionType transactionType() {
        return TransactionType.BONUS;
    }

    @Override
    public String contextLabel() {
        return "Bonus";
    }

    @Override
    public TransferCommand toCommand(BonusRequest request) {
        return TransferCommand.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .assetCode(request.getAssetCode())
                .debitOwnerId(WalletProvider.SYSTEM_TREASURY)
                .creditOwnerId(request.getUserId())
                .amount(request.getAmount())
                .transactionType(TransactionType.BONUS)
                .providedDescription(request.getDescription())
                .defaultDescriptionSupplier(() -> String.format(
                        "Bonus issued: %s %s to user %s",
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
