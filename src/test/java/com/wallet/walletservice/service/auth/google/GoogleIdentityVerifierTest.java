package com.wallet.walletservice.service.auth.google;

import com.wallet.walletservice.exception.GoogleAuthException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleIdentityVerifierTest {

    @Test
    void verify_WhenTokenIsMalformed_ThrowsGoogleAuthException() {
        GoogleIdentityVerifier verifier = new GoogleIdentityVerifier("client-id");

        GoogleAuthException exception = assertThrows(
                GoogleAuthException.class,
                () -> verifier.verify("not-a-google-id-token"));

        assertEquals("Invalid Google ID token.", exception.getMessage());
    }
}
