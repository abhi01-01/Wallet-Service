package com.wallet.walletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to spend credits from a wallet")
public class SpendRequest {
    @NotBlank
    @Schema(description = "ID of the user spending the credits", example = "user123")
    private String userId;

    @NotBlank
    @Schema(description = "Code of the asset to spend", example = "DIAMOND")
    private String assetCode;

    @NotNull
    @DecimalMin("0.0001")
    @Schema(description = "Amount to spend", example = "100.0")
    private BigDecimal amount;

    @NotBlank
    @Schema(description = "Unique key to prevent duplicate processing", example = "spend-id-789")
    private String idempotencyKey;

    @Schema(description = "Optional description for the spending", example = "In-app purchase")
    private String description;
}
