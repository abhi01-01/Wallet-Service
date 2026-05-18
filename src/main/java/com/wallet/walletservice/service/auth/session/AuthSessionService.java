package com.wallet.walletservice.service.auth.session;

import com.wallet.walletservice.config.JwtTokenProvider;
import com.wallet.walletservice.domain.entity.RefreshToken;
import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.domain.enums.UserStatus;
import com.wallet.walletservice.dto.request.LogoutRequest;
import com.wallet.walletservice.dto.request.TokenRefreshRequest;
import com.wallet.walletservice.dto.response.AuthResponse;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshTokenDurationMs;

    public AuthResponse buildAuthResponse(User user) {
        if (user.getAccountStatus() == UserStatus.CLOSED) {
            throw new AuthException("Cannot authenticate a closed account.");
        }

        String accessToken = jwtTokenProvider.generateToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .userId(user.getId().toString())
                .email(user.getEmail())
                .ownerType(user.getOwnerType().name())
                .build();
    }

    public AuthResponse refreshToken(TokenRefreshRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new AuthException("Invalid refresh token."));

        if (refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new AuthException("Refresh token expired. Please sign in again.");
        }

        User user = refreshToken.getUser();
        if (user.getAccountStatus() == UserStatus.CLOSED) {
            throw new AuthException("Account is closed.");
        }

        String newAccessToken = jwtTokenProvider.generateToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken.getToken())
                .tokenType("Bearer")
                .userId(user.getId().toString())
                .ownerType(user.getOwnerType().name())
                .build();
    }

    public void logout(LogoutRequest request, String userIdStr) {
        UUID userId = UUID.fromString(userIdStr);

        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new AuthException("Invalid session or already logged out."));

        // Security boundary: prevent cross-user session revocation.
        if (!refreshToken.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You cannot revoke a session belonging to another user.");
        }

        refreshTokenRepository.delete(refreshToken);
    }

    private RefreshToken createRefreshToken(User user) {
        // Enforce one active refresh token per user to keep session state simple.
        refreshTokenRepository.deleteByUserId(user.getId());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(OffsetDateTime.now().plusDays(refreshTokenDurationMs / 86400000))
                .build();

        return refreshTokenRepository.save(refreshToken);
    }
}
