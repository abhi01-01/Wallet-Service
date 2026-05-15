package com.wallet.walletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to refresh the JWT access token")
public class TokenRefreshRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "The valid refresh token issued at login")
    private String refreshToken;
}
