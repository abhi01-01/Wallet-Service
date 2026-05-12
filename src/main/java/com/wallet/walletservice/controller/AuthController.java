package com.wallet.walletservice.controller;

import com.wallet.walletservice.dto.request.*;
import com.wallet.walletservice.dto.response.AuthResponse;
import com.wallet.walletservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints for user registration, login, and OTP verification")
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "Register new user", description = "Register with email and password. Sends an OTP to the provided email address for verification.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Signup successful, OTP sent"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid input or user already exists")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<String>> signup(@Valid @RequestBody SignUpRequest req){
        log.info("Signup API called for email: {}", req.getEmail());
        String message = authService.signup(req);
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok(message));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Submit the OTP sent via email to verify the user account and receive an access token.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Email verified successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired OTP")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest req){
        log.info("Verify OTP API called for email: {}", req.getEmail());
        AuthResponse auth = authService.verifyOtp(req);
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok("Email Verified Successfully", auth));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate with email and password to receive a JWT access token.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req){
        log.info("Login API called for email: {}", req.getEmail());
        AuthResponse auth = authService.login(req);
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok("Login Successful", auth));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP", description = "Request a new OTP for email verification.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OTP resent successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found or already verified")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<String>> resendOtp(@Valid @RequestBody ResendOtpRequest req){
        log.info("Resend OTP API called for email: {}", req.getEmail());
        String message = authService.resentOtp(req);
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok(message));
    }

    @PostMapping("/oauth2/success")
    @Operation(summary = "OAuth2 success redirect", description = "Handles successful Google OAuth2 login and returns the generated JWT.")
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<AuthResponse>> oauth2Success(@RequestParam String token){
        AuthResponse response = AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .message("Google login successful")
                .build();
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok("Google login successful", response));
    }

    @PostMapping("/google")
    @Operation(summary = "Google ID Token Login", description = "Verify a Google ID token from a frontend (e.g., React) to login or signup.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Google login successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid Google ID token")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleAuthRequest req){
        log.info("Google login API called");
        AuthResponse auth = authService.googleLogin(req);
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok("Google login successful", auth));
    }

}
