package com.wallet.walletservice.exception;

import jakarta.validation.constraints.NotBlank;


public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(@NotBlank String userId, @NotBlank String assetCode, Object required, Object available) {
        super(String.format("User '%s' has insufficient %s balance. Required: %s, Available: %s",
                userId, assetCode, required, available));
    }
}
