package com.wallet.walletservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@Schema(description = "OTP verification request")
public class VerifyOtpRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be valid email address")
    @Schema(description = "User's email address", example = "user@example.com")
    private String email;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "\\d{6}", message = "OTP must be exactly 6 digits")
    @Schema(description = "6-digit OTP sent to email", example = "123456")
    private String otp;
}
