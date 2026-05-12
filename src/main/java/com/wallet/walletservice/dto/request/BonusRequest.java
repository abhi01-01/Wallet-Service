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
@Schema(description = "Request to issue a bonus to a user (System Only)")
public class BonusRequest {
    @NotBlank
    @Schema(description = "ID of the user receiving the bonus", example = "user123")
    private String userId;

    @NotBlank
    @Schema(description = "Code of the asset for the bonus", example = "LOYALTY")
    private String assetCode;

    @NotNull
    @DecimalMin("0.0001")
    @Schema(description = "Amount of the bonus", example = "5.0")
    private BigDecimal amount;

    @NotBlank
    @Schema(description = "Unique key to prevent duplicate processing", example = "bonus-id-456")
    private String idempotencyKey;

    @Schema(description = "Optional description for the bonus", example = "Welcome bonus")
    private String description;
}
