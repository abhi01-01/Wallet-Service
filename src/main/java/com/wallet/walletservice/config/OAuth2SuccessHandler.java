package com.wallet.walletservice.config;

import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.domain.enums.AuthProvider;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.domain.enums.UserStatus;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j

/*
 * Called after successful Google OAuth2 login.
 * Finds or creates the user record, issues a JWT, and redirects with
 * the token as a query parameter, so the frontend can store it.
 */
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public void onAuthenticationSuccess(@NonNull HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String googleId = oAuth2User != null ? oAuth2User.getAttribute("sub") : null;
        String email = oAuth2User != null ? oAuth2User.getAttribute("email") : null;

        if(googleId == null || email == null){
            log.error("Google OAuth2 token missing required claims: sub={}, email={}", googleId, email);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Google account must grant access to email address.");
            return;
        }

        User user = findOrCreateUser(googleId, email);

        String token = jwtTokenProvider.generateToken(user);
        log.info("Google OAuth2 login success for user={}", user.getEmail());

        // Redirect to the frontend with token — adjust the base URL for your frontend
        String targetUrl = UriComponentsBuilder
                .fromUriString("/api/v1/auth/oauth2/success")
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    /*
     * Finds an existing user by GoogleID, or by email (links the GoogleID),
     * or creates a brand-new user. Always returns a persisted, non-null User.
     */

    private @NonNull User findOrCreateUser(String googleId, String email){

        // 1. Already linked to this Google account
        var byGoogleId = userRepository.findByGoogleId(googleId);
        if (byGoogleId.isPresent()) {
            User existing = byGoogleId.get();
            // NEW: Reject Google login for closed accounts
            if (existing.getAccountStatus() == UserStatus.CLOSED) {
                throw new AuthException("Account is closed.");
            }
            return Objects.requireNonNull(byGoogleId.get(), "Google ID lookup returned null");
        }

        // 2. Existing email account — link the Google ID to it
        var byEmail = userRepository.findByEmail(email);
        if (byEmail.isPresent()) {
            User existing = Objects.requireNonNull(byEmail.get(), "Email lookup returned null");
            // NEW: Reject Google login for closed accounts
            if (existing.getAccountStatus() == UserStatus.CLOSED) {
                throw new AuthException("Account is closed.");
            }
            existing.setGoogleId(googleId);
            existing.setEmailVerified(true);
            existing.setProvider(AuthProvider.GOOGLE);
            return Objects.requireNonNull(userRepository.save(existing), "User save must not return null");
        }

        // 3. Brand-new user — register via Google
        User newUser = User.builder()
                .email(email)
                .googleId(googleId)
                .provider(AuthProvider.GOOGLE)
                .ownerType(OwnerType.USER)
                .emailVerified(true)
                .build();
        return Objects.requireNonNull(userRepository.save(newUser), "User save must not return null");
    }

}
