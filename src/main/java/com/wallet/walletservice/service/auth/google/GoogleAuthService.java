package com.wallet.walletservice.service.auth.google;

import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.dto.request.GoogleAuthRequest;
import com.wallet.walletservice.dto.response.AuthResponse;
import com.wallet.walletservice.service.auth.session.AuthSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthService {

    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final GoogleUserService googleUserService;
    private final AuthSessionService authSessionService;

    public AuthResponse googleLogin(GoogleAuthRequest request) {
        GoogleIdentity identity = googleIdentityVerifier.verify(request.getIdToken());
        User user = googleUserService.findOrCreateGoogleUser(identity.getGoogleId(), identity.getEmail());

        log.info("React Google login success for user: {}", user.getEmail());
        return authSessionService.buildAuthResponse(user);
    }
}
