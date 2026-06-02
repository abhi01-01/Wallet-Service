package com.wallet.walletservice.service.auth.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.wallet.walletservice.exception.GoogleAuthException;
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
        try {
            GoogleIdToken idToken = googleIdTokenVerifier().verify(idTokenValue);
            if (idToken == null) {
                throw new GoogleAuthException("Invalid Google ID token.");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            return new GoogleIdentity(payload.getSubject(), payload.getEmail());
        } catch (IllegalArgumentException e) {
            log.warn("Malformed Google ID token received");
            throw new GoogleAuthException("Invalid Google ID token.");
        } catch (GoogleAuthException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            throw new GoogleAuthException("Unable to verify Google ID token.");
        }
    }

    GoogleIdTokenVerifier googleIdTokenVerifier() {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }
}
