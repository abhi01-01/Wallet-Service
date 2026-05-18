package com.wallet.walletservice.service.auth.email;

import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.domain.enums.AuthProvider;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.dto.request.LoginRequest;
import com.wallet.walletservice.dto.request.ResendOtpRequest;
import com.wallet.walletservice.dto.request.SignUpRequest;
import com.wallet.walletservice.dto.request.VerifyOtpRequest;
import com.wallet.walletservice.dto.response.AuthResponse;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.repository.UserRepository;
import com.wallet.walletservice.service.auth.otp.OtpService;
import com.wallet.walletservice.service.auth.session.AuthSessionService;
import com.wallet.walletservice.service.auth.support.UserLookupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailAuthService {

    private final UserRepository userRepository;
    private final UserLookupService userLookupService;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final AuthSessionService authSessionService;

    public String signup(SignUpRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("Email is already registered.");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .ownerType(OwnerType.USER)
                .provider(AuthProvider.EMAIL)
                .emailVerified(false)
                .build();

        User savedUser = Objects.requireNonNull(userRepository.save(user), "User save must not return null");

        otpService.sendOtp(savedUser);
        log.info("New User Registered: {}", request.getEmail());
        return "Registration successful. Check your email for OTP.";
    }

    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        User user = otpService.verifyOtp(request);

        log.info("Email verified for user: {}", user.getEmail());
        return authSessionService.buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userLookupService.findByEmailOrThrow(request.getEmail());

        if (user.getProvider() == AuthProvider.GOOGLE && user.getPasswordHash() == null) {
            throw new AuthException("This account uses Google Sign-In. Please log in with Google.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AuthException("Invalid email or password.");
        }

        if (!user.isEmailVerified()) {
            throw new AuthException("Email not verified. Please check your inbox for the OTP.");
        }

        log.info("User logged in: {}", user.getEmail());
        return authSessionService.buildAuthResponse(user);
    }

    public String resendOtp(ResendOtpRequest request) {
        User user = userLookupService.findByEmailOrThrow(request.getEmail());

        if (user.isEmailVerified()) {
            throw new AuthException("Email is already verified.");
        }

        otpService.resendOtp(user);
        log.info("OTP resent to: {}", user.getEmail());
        return "A new OTP has been sent to your email.";
    }
}
