package com.wallet.walletservice.config;

import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.service.auth.google.GoogleUserService;
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

@Component
@RequiredArgsConstructor
@Slf4j

/*
 * Called after successful Google OAuth2 login.
 * Finds or creates the user record, issues a JWT, and redirects with
 * the token as a query parameter, so the frontend can store it.
 */
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final GoogleUserService googleUserService;

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

        User user = googleUserService.findOrCreateGoogleUser(googleId, email);

        String token = jwtTokenProvider.generateToken(user);
        log.info("Google OAuth2 login success for user={}", user.getEmail());

        // Redirect to the frontend with token — adjust the base URL for your frontend
        String targetUrl = UriComponentsBuilder
                .fromUriString("/api/v1/auth/oauth2/success")
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

}
