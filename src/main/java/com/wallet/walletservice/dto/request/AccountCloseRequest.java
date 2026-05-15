package com.wallet.walletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Request to close an account permanently")
public class AccountCloseRequest {

    @NotNull(message = "Explicit confirmation of balance forfeiture is required")
    @Schema(description = "Confirms forfeiture of any remaining positive balance to the system treasury", example = "true")
    private Boolean confirmForfeitBalance;
}
