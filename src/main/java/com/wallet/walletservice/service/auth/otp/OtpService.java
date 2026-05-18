package com.wallet.walletservice.service.auth.otp;

import com.wallet.walletservice.domain.entity.OtpCode;
import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.dto.request.VerifyOtpRequest;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.repository.OtpCodeRepository;
import com.wallet.walletservice.repository.UserRepository;
import com.wallet.walletservice.service.auth.support.UserLookupService;
import com.wallet.walletservice.service.notification.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final int OTP_EXPIRY_MINUTES = 10;

    private final OtpCodeRepository otpCodeRepository;
    private final UserRepository userRepository;
    private final UserLookupService userLookupService;
    private final EmailNotificationService emailNotificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public void sendOtp(User user) {
        String code = generateOtp();

        OtpCode otpCode = OtpCode.builder()
                .userId(user.getId())
                .code(code)
                .expiresAt(OffsetDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES))
                .used(false)
                .build();

        otpCodeRepository.save(Objects.requireNonNull(otpCode));
        emailNotificationService.sendOtpEmail(user.getEmail(), code);
    }

    public User verifyOtp(VerifyOtpRequest request) {
        User user = userLookupService.findByEmailOrThrow(request.getEmail());

        if (user.isEmailVerified()) {
            throw new AuthException("Email is already verified. Please log in.");
        }

        OtpCode otp = otpCodeRepository
                .findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new AuthException("No active OTP found. Please request a new one."));

        if (otp.isExpired()) {
            throw new AuthException("OTP has expired. Please request a new one.");
        }

        if (!otp.getCode().equals(request.getOtp())) {
            throw new AuthException("Invalid OTP.");
        }

        otp.setUsed(true);
        otpCodeRepository.save(otp);

        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    public void resendOtp(User user) {
        // Invalidate all previous OTPs before issuing a new one.
        otpCodeRepository.invalidateAllForUser(user.getId());
        sendOtp(user);
    }

    private String generateOtp() {
        // Cryptographically secure 6-digit OTP.
        int code = 100_000 + secureRandom.nextInt(900_000);
        return String.valueOf(code);
    }
}
