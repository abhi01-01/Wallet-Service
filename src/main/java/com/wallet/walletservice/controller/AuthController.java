package com.wallet.walletservice.controller;

import com.wallet.walletservice.dto.request.*;
import com.wallet.walletservice.dto.response.ApiResponse;
import com.wallet.walletservice.dto.response.AuthResponse;
import com.wallet.walletservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth API", description = "Authentication — email/password + OTP verification + Google OAuth2")
@Slf4j
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "Register with email & password — sends OTP to email")
    public ResponseEntity<ApiResponse<String>> signup(@Valid @RequestBody SignUpRequest req){
        log.info("Signup API called for email: {}", req.getEmail());
        String message = authService.signup(req);
        return ResponseEntity.ok(ApiResponse.ok(message));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Submit OTP to verify email and receive a JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest req){
        log.info("Verify OTP API called for email: {}", req.getEmail());
        AuthResponse auth = authService.verifyOtp(req);
        return ResponseEntity.ok(ApiResponse.ok("Email Verified Successfully", auth));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email & password — returns JWT")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest req){
        log.info("Login API called for email: {}", req.getEmail());
        AuthResponse auth = authService.login(req);
        return ResponseEntity.ok(ApiResponse.ok("Login Successful", auth));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP verification email")
    public ResponseEntity<ApiResponse<String>> resendOtp(@Valid @RequestBody ResendOtpRequest req){
        log.info("Resend OTP API called for email: {}", req.getEmail());
        String message = authService.resentOtp(req);
        return ResponseEntity.ok(ApiResponse.ok(message));
    }

    @PostMapping("/oauth2/success")
    @Operation(summary = "OAuth2 success landing — returns the JWT from Google login")
    public ResponseEntity<ApiResponse<AuthResponse>> oauth2Success(@RequestParam String token){
        AuthResponse response = AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .message("Google login successful")
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Google login successful", response));
    }

    @PostMapping("/google")
    @Operation(summary = "Login/Signup with Google ID Token — from React frontend")
    public ResponseEntity<ApiResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleAuthRequest req){
        log.info("Google login API called");
        AuthResponse auth = authService.googleLogin(req);
        return ResponseEntity.ok(ApiResponse.ok("Google login successful", auth));
    }

}
