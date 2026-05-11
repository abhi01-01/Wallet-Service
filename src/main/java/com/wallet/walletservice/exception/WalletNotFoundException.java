package com.wallet.walletservice.exception;

public class WalletNotFoundException extends RuntimeException{
    public WalletNotFoundException(String userId, String assetCode){
        super(String.format("Wallet not found for user %s with assetCode %s", userId, assetCode));
    }
}
