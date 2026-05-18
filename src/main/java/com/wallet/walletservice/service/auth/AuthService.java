package com.wallet.walletservice.service.auth;

import com.wallet.walletservice.dto.request.AccountCloseRequest;
import com.wallet.walletservice.dto.request.GoogleAuthRequest;
import com.wallet.walletservice.dto.request.LoginRequest;
import com.wallet.walletservice.dto.request.LogoutRequest;
import com.wallet.walletservice.dto.request.ResendOtpRequest;
import com.wallet.walletservice.dto.request.SignUpRequest;
import com.wallet.walletservice.dto.request.TokenRefreshRequest;
import com.wallet.walletservice.dto.request.VerifyOtpRequest;
import com.wallet.walletservice.dto.response.AuthResponse;
import com.wallet.walletservice.service.auth.account.AccountClosureService;
import com.wallet.walletservice.service.auth.email.EmailAuthService;
import com.wallet.walletservice.service.auth.google.GoogleAuthService;
import com.wallet.walletservice.service.auth.session.AuthSessionService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final EmailAuthService emailAuthService;
    private final GoogleAuthService googleAuthService;
    private final AccountClosureService accountClosureService;
    private final AuthSessionService authSessionService;

    @Transactional
    public String signup(SignUpRequest request) {
        log.info("Signup API called");
        return emailAuthService.signup(request);
    }

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        return emailAuthService.verifyOtp(request);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return emailAuthService.login(request);
    }

    @Transactional
    public String resentOtp(ResendOtpRequest request) {
        return emailAuthService.resendOtp(request);
    }

    @Transactional
    public AuthResponse googleLogin(GoogleAuthRequest request) {
        return googleAuthService.googleLogin(request);
    }

    @Transactional
    public void closeAccount(String userIdStr, AccountCloseRequest request) {
        accountClosureService.closeAccount(userIdStr, request);
    }

    @Transactional
    public AuthResponse refreshToken(TokenRefreshRequest request) {
        return authSessionService.refreshToken(request);
    }

    @Transactional
    public void logout(LogoutRequest request, String userIdStr) {
        authSessionService.logout(request, userIdStr);
        log.info("Targeted session revoked (logout) for user ID: {}", userIdStr);
    }
}
