package com.wallet.walletservice.service.auth.support;

import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.exception.UserNotFoundException;
import com.wallet.walletservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserLookupService {

    private final UserRepository userRepository;

    public User findByEmailOrThrow(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException("No account found with this email."));
    }

    public User findByIdOrThrow(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));
    }
}
