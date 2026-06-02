package com.wallet.walletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
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
@Schema(description = "Request to create a payment order")
public class PaymentOrderRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "100", message = "Amount must be at least 100")
    @Schema(description = "Amount to pay in paisa/lowest currency unit", example = "10000")
    private BigDecimal amount;

    @NotNull(message = "Asset code is required")
    @Schema(description = "Code of the asset being purchased", example = "GOLD")
    private String assetCode;
}
