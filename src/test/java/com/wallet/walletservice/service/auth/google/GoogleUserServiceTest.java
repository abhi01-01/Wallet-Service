package com.wallet.walletservice.service.auth.google;

import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.domain.enums.AuthProvider;
import com.wallet.walletservice.domain.enums.UserStatus;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GoogleUserService googleUserService;

    @Test
    void findOrCreateGoogleUser_WhenEmailAccountExists_LinksGoogleAccount() {
        User existing = User.builder()
                .email("user@example.com")
                .provider(AuthProvider.EMAIL)
                .emailVerified(false)
                .build();

        when(userRepository.findByGoogleId("google-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        User actual = googleUserService.findOrCreateGoogleUser("google-1", "user@example.com");

        assertEquals("google-1", actual.getGoogleId());
        assertEquals(AuthProvider.GOOGLE, actual.getProvider());
        assertTrue(actual.isEmailVerified());
        verify(userRepository).save(existing);
    }

    @Test
    void findOrCreateGoogleUser_WhenExistingGoogleAccountIsClosed_RejectsLogin() {
        User closed = User.builder()
                .email("closed@example.com")
                .googleId("google-1")
                .provider(AuthProvider.GOOGLE)
                .accountStatus(UserStatus.CLOSED)
                .build();

        when(userRepository.findByGoogleId("google-1")).thenReturn(Optional.of(closed));

        assertThrows(
                AuthException.class,
                () -> googleUserService.findOrCreateGoogleUser("google-1", "closed@example.com"));
    }
}
