package com.wallet.walletservice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private String userId;
    private String email;
    private String ownerType;
    /** Only returned for OAuth2 success redirect — not in normal API response */
    private String message;
}
