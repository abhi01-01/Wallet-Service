package com.wallet.walletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to top-up a user's wallet (System Only)")
public class TopUpRequest {

    @NotBlank(message = "User Id cannot be empty")
    @Schema(description = "ID of the user to top-up", example = "user123")
    private String userId;

    @NotBlank(message = "Asset Code is required e.g.(GOLD, DIAMOND, LOYALTY)")
    @Schema(description = "Code of the asset to credit", example = "GOLD")
    private String assetCode;

    @NonNull @DecimalMin(value = "0.0001", message = "Amount must be positive")
    @Schema(description = "Amount of asset to credit", example = "10.5")
    private BigDecimal amount;

    @NotBlank(message = "Idempotency Key is required")
    @Schema(description = "Unique key to prevent duplicate processing", example = "unique-id-123")
    private String idempotencyKey;

    @Schema(description = "Optional description for the transaction", example = "Store purchase")
    private String description;
}
