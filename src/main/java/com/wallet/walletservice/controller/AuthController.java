package com.wallet.walletservice.controller;

import com.wallet.walletservice.dto.request.*;
import com.wallet.walletservice.dto.response.AuthResponse;
import com.wallet.walletservice.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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


    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh Access Token", description = "Exchange a valid refresh token for a new short-lived access token.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid or expired refresh token")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody TokenRefreshRequest req){
        log.info("Token refresh API called");
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok(
                "Token refreshed successfully", authService.refreshToken(req)));
    }

    @DeleteMapping("/close-account")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Close Account", description = "Permanently close user account, forfeit remaining balances if confirmed, and scrub PII.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Account closed successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Conflict - Positive balance without forfeit confirmation")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<String>> closeAccount(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody AccountCloseRequest req){
        log.info("Account closure requested for user: {}", userId);
        authService.closeAccount(userId, req);
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok(
                "Account closed successfully. All active sessions terminated."));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAnyRole('USER', 'SYSTEM')")
    @Operation(summary = "Logout", description = "Revokes the targeted refresh token, terminating the specific device session.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Logged out successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Token ownership mismatch")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<String>> logout(
            @Valid @RequestBody LogoutRequest req,
            @AuthenticationPrincipal String userId){
        log.info("Targeted Device logout requested for user: {}", userId);
        authService.logout(req, userId);
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok("Logged out successfully"));
    }

}
