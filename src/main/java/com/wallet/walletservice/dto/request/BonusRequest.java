package com.wallet.walletservice.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BonusRequest {
    @NotBlank
    private String userId;
    @NotBlank private String assetCode;

    @NotNull
    @DecimalMin("0.0001")
    private BigDecimal amount;

    @NotBlank private String idempotencyKey;
    private String description;
}
