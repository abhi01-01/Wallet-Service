package com.wallet.walletservice.exception;

import com.wallet.walletservice.dto.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GlobalExceptionHandlerTest {

    @Test
    void handleGoogleAuth_ReturnsUnauthorized() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiResponse<?>> response = handler.handleGoogleAuth(
                new GoogleAuthException("Invalid Google ID token."));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid Google ID token.", response.getBody().getMessage());
    }
}
