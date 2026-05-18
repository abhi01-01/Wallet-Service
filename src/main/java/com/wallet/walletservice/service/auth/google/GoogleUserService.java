package com.wallet.walletservice.service.auth.google;

import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.domain.enums.AuthProvider;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.domain.enums.UserStatus;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GoogleUserService {

    private final UserRepository userRepository;

    /*
     * Finds an existing user by Google ID, links an existing email account to
     * Google, or creates a new Google-backed account. Closed accounts are never
     * allowed to authenticate through this path.
     */
    public User findOrCreateGoogleUser(String googleId, String email) {
        var byGoogleId = userRepository.findByGoogleId(googleId);
        if (byGoogleId.isPresent()) {
            User existing = byGoogleId.get();
            rejectClosedAccount(existing);
            return Objects.requireNonNull(existing, "Google ID lookup returned null");
        }

        var byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User existing = Objects.requireNonNull(byEmail.get(), "Email lookup returned null");
            rejectClosedAccount(existing);
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
                .build();
        return Objects.requireNonNull(userRepository.save(newUser), "User save must not return null");
    }

    private void rejectClosedAccount(User user) {
        if (user.getAccountStatus() == UserStatus.CLOSED) {
            throw new AuthException("Account is closed.");
        }
    }
}
