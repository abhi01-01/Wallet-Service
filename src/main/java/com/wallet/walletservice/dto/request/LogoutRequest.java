package com.wallet.walletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to log out a specific device session")
public class LogoutRequest {
    @NotBlank(message = "Refresh token is required to identify the session")
    @Schema(description = "The refresh token of the current session to revoke")
    private String refreshToken;
}
