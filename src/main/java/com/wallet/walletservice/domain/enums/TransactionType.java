package com.wallet.walletservice.domain.enums;

public enum TransactionType {
    TOPUP,   // User purchases credits (real money -> credits)
    BONUS,   // System issues free credits (referral, reward)
    SPEND    // User spends credits inside the app
}
