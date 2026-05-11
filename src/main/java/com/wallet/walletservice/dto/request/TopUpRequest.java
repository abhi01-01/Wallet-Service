package com.wallet.walletservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NonNull;

import java.math.BigDecimal;

@Data
public class TopUpRequest {

    @NotBlank( message = "User Id cannot be empty")
    private String userId;

    @NotBlank( message = "Asset Code is required e.g.(GOLD, DIAMOND, LOYALTY)")
    private String assetCode ;

    @NonNull @DecimalMin(value = "0.0001", message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank( message = "Idempotency Key is required")
    private String idempotencyKey;

    private String description;
}
