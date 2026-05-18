package com.wallet.walletservice.service.payment.verification;

import com.wallet.walletservice.domain.enums.UserStatus;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentUserGuard {

    private final UserRepository userRepository;

    public void ensureUserCanReceivePaymentCredit(String userId) {
        userRepository.findById(UUID.fromString(userId))
                .map(user -> {
                    if (user.getAccountStatus() == UserStatus.CLOSED) {
                        throw new AuthException("Account is closed." + userId);
                    }
                    return user;
                })
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
    }
}
