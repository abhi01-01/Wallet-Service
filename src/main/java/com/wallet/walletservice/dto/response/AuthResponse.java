package com.wallet.walletservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Authentication response containing JWT and user info")
public class AuthResponse {

    @Schema(description = "JWT access token", example = "eyJhbGciOiJIUzI1...")
    private String accessToken;

    @Builder.Default
    @Schema(description = "Type of the token", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "User ID", example = "user123")
    private String userId;

    @Schema(description = "User email address", example = "user@example.com")
    private String email;

    @Schema(description = "Type of the account owner", example = "USER")
    private String ownerType;

    /** Only returned for OAuth2 success redirect — not in normal API response */
    @Schema(description = "Optional status message", example = "Login Successful")
    private String message;
}
