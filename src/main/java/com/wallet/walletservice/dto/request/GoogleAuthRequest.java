package com.wallet.walletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Google authentication request")
public class GoogleAuthRequest {
    @NotBlank(message = "Google ID token is required")
    @Schema(description = "Google ID token received from the frontend", example = "eyJhbGciOiJSUzI1...")
    private String idToken;
}
