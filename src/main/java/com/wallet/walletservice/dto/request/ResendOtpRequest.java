package com.wallet.walletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Request to resend OTP")
public class ResendOtpRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Schema(description = "User's email address", example = "user@example.com")
    private String email;
}
