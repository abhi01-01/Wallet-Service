package com.wallet.walletservice.service.wallet.operation;

import com.wallet.walletservice.domain.enums.TransactionType;
import com.wallet.walletservice.dto.request.ForfeitRequest;
import com.wallet.walletservice.service.wallet.support.WalletProvider;
import com.wallet.walletservice.service.wallet.transfer.TransferCommand;
import org.springframework.stereotype.Component;

@Component
public class ForfeitWalletOperation implements WalletOperation<ForfeitRequest> {

    @Override
    public TransactionType transactionType() {
        return TransactionType.FORFEIT;
    }

    @Override
    public String contextLabel() {
        return "Forfeit";
    }

    @Override
    public TransferCommand toCommand(ForfeitRequest request) {
        return TransferCommand.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .assetCode(request.getAssetCode())
                .debitOwnerId(request.getUserId())
                .creditOwnerId(WalletProvider.SYSTEM_TREASURY)
                .amount(request.getAmount())
                .transactionType(TransactionType.FORFEIT)
                .providedDescription(request.getDescription())
                .defaultDescriptionSupplier(() -> String.format(
                        "Forfeiture of %s %s due to account closure",
                        request.getAmount(),
                        request.getAssetCode()))
                .build();
    }
}
