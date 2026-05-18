package com.wallet.walletservice.service.wallet.policy;

import com.wallet.walletservice.domain.entity.Wallet;
import com.wallet.walletservice.exception.InsufficientBalanceException;
import com.wallet.walletservice.service.wallet.transfer.TransferCommand;
import org.springframework.stereotype.Component;

@Component
public class SufficientBalancePolicy implements WalletTransferPolicy {

    @Override
    public void validate(TransferCommand command, Wallet debitWallet, Wallet creditWallet) {
        // Balance must be checked after the debit wallet has been locked.
        if (debitWallet.getBalance().compareTo(command.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    command.getDebitOwnerId(),
                    command.getAssetCode(),
                    command.getAmount(),
                    debitWallet.getBalance());
        }
    }
}
