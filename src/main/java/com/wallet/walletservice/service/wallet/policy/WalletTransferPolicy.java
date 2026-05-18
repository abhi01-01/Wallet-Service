package com.wallet.walletservice.service.wallet.policy;

import com.wallet.walletservice.domain.entity.Wallet;
import com.wallet.walletservice.service.wallet.transfer.TransferCommand;

public interface WalletTransferPolicy {
    void validate(TransferCommand command, Wallet debitWallet, Wallet creditWallet);
}
