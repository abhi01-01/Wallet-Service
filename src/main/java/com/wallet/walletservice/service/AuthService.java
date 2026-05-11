package com.wallet.walletservice.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.wallet.walletservice.config.JwtTokenProvider;
import com.wallet.walletservice.domain.entity.OtpCode;
import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.domain.enums.AuthProvider;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.dto.request.*;
import com.wallet.walletservice.dto.response.AuthResponse;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.repository.OtpCodeRepository;
import com.wallet.walletservice.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int OTP_EXPIRY_MINUTES = 10 ;

    private final UserRepository userRepository;
    private final OtpCodeRepository otpCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    // ── 1. EMAIL + PASSWORD SIGNUP ────────────────────────────────

    @Transactional
    public String signup(SignUpRequest req){
        if(userRepository.existsByEmail(req.getEmail())){
            throw new AuthException("Email is already registered.");
        }

        log.info("Signup API called");
        User user = User.builder()
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .ownerType(OwnerType.USER)
                .provider(AuthProvider.EMAIL)
                .emailVerified(false)
                .build();

        User savedUser = Objects.requireNonNull(userRepository.save(user), "User save must not return null");

        sendOtp(savedUser);
        log.info("New User Registered: {}", req.getEmail());
        return "Registration successful. Check your email for OTP.";
    }

    // ── 2. VERIFY OTP ─────────────────────────────────────────────

    @Transactional
    public AuthResponse verifyOtp(VerifyOtpRequest req){
        User user = findUserByEmail(req.getEmail()) ;

        if(user.isEmailVerified()){
            throw new AuthException("Email is already verified. Please log in.");
        }

        OtpCode otp = otpCodeRepository
                .findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new AuthException("No active OTP found. Please request a new one."));

        if(otp.isExpired()){
            throw new AuthException("OTP has expired. Please request a new one.");
        }

        if (!otp.getCode().equals(req.getOtp())) {
            throw new AuthException("Invalid OTP.");
        }

        // Mark OTP used
        otp.setUsed(true);
        otpCodeRepository.save(otp);

        // Mark email verified
        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("Email verified for user: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    // ── 3. LOGIN ──────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest req){
        User user = findUserByEmail(req.getEmail());

        if(user.getProvider() == AuthProvider.GOOGLE && user.getPasswordHash() == null){
            throw new AuthException("This account uses Google Sign-In. Please log in with Google.");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new AuthException("Invalid email or password.");
        }

        if (!user.isEmailVerified()) {
            throw new AuthException("Email not verified. Please check your inbox for the OTP.");
        }

        log.info("User logged in: {}", user.getEmail());
        return buildAuthResponse(user);
    }

    // ── 4. RESEND OTP ─────────────────────────────────────────────

    @Transactional
    public String resentOtp(ResendOtpRequest req){
        User user = findUserByEmail(req.getEmail());

        if (user.isEmailVerified()) {
            throw new AuthException("Email is already verified.");
        }

        // Invalidate all previous OTPs before issuing a new one
        otpCodeRepository.invalidateAllForUser(user.getId());
        sendOtp(user);

        log.info("OTP resent to: {}", user.getEmail());
        return "A new OTP has been sent to your email.";
    }

    // ── 5. GOOGLE REST LOGIN/SIGNUP (For Frontend Apps) ───────────

    @Transactional
    public AuthResponse googleLogin(GoogleAuthRequest req){
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        try {
            GoogleIdToken idToken = verifier.verify(req.getIdToken());
            if(idToken == null){
                throw new AuthException("Invalid Google ID token.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String googleId = payload.getSubject();
            String email = payload.getEmail();

            // Same find-or-create logic as OAuth2SuccessHandler

            User user = userRepository.findByGoogleId(googleId).orElseGet(() -> {
                var byEmail = userRepository.findByEmail(email);
                if(byEmail.isPresent()){
                    User existing = byEmail.get();
                    existing.setGoogleId(googleId);
                    existing.setEmailVerified(true);
                    existing.setProvider(AuthProvider.GOOGLE);
                    return Objects.requireNonNull(userRepository.save(existing), "User save must not return null");
                }

                User newUser = User.builder()
                        .email(email)
                        .googleId(googleId)
                        .provider(AuthProvider.GOOGLE)
                        .ownerType(OwnerType.USER)
                        .emailVerified(true)
                        .build() ;

                return Objects.requireNonNull(userRepository.save(newUser), "User save must not return null");
            });

            log.info("React Google login success for user: {}", user.getEmail());
            return buildAuthResponse(user);

        }catch (Exception e) {
            log.error("Google token verification failed", e);
            throw new AuthException("Google authentication failed. Token may be invalid or expired.");
        }
    }

    // ── PRIVATE HELPERS ───────────────────────────────────────────

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("No account found with this email."));
    }

    private void sendOtp(User user){
        String code = generateOtp();

        OtpCode otpCode = OtpCode.builder()
                 .userId(user.getId())
                .code(code)
                .expiresAt(OffsetDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .used(false)
                .build() ;

        otpCodeRepository.save(Objects.requireNonNull(otpCode));
        emailService.sendOtpEmail(user.getEmail(), code);
    }

    private String generateOtp(){
        // Cryptographically Secure 6-digit OTP
        SecureRandom random = new SecureRandom();
        int code = 100_000 + random.nextInt(900_000);
        return String.valueOf(code);
    }

    private AuthResponse buildAuthResponse(User user){
        String token = jwtTokenProvider.generateToken(user);

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId().toString())
                .email(user.getEmail())
                .ownerType(user.getOwnerType().name())
                .build();
    }

}
