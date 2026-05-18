package com.wallet.walletservice.service.auth.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.wallet.walletservice.exception.AuthException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@Slf4j
public class GoogleIdentityVerifier {

    private final String googleClientId;

    public GoogleIdentityVerifier(
            @Value("${spring.security.oauth2.client.registration.google.client-id}") String googleClientId
    ) {
        this.googleClientId = googleClientId;
    }

    public GoogleIdentity verify(String idTokenValue) {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        try {
            GoogleIdToken idToken = verifier.verify(idTokenValue);
            if (idToken == null) {
                throw new AuthException("Invalid Google ID token.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            return new GoogleIdentity(payload.getSubject(), payload.getEmail());
        } catch (Exception e) {
            log.error("Google token verification failed", e);
            if (e instanceof AuthException) {
                throw (AuthException) e;
            }
            throw new AuthException("Google authentication failed: " + e.getMessage());
        }
    }
}
