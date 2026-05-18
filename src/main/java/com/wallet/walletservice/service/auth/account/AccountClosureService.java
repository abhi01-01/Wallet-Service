package com.wallet.walletservice.service.auth.account;

import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.domain.enums.UserStatus;
import com.wallet.walletservice.dto.request.AccountCloseRequest;
import com.wallet.walletservice.repository.RefreshTokenRepository;
import com.wallet.walletservice.repository.UserRepository;
import com.wallet.walletservice.service.auth.support.UserLookupService;
import com.wallet.walletservice.service.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountClosureService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserLookupService userLookupService;
    private final WalletService walletService;

    public void closeAccount(String userIdStr, AccountCloseRequest request) {
        UUID userId = UUID.fromString(userIdStr);
        User user = userLookupService.findByIdOrThrow(userId);

        if (user.getAccountStatus() == UserStatus.CLOSED) {
            throw new AccessDeniedException("Account is already closed.");
        }

        // Delegate financial invariant checks and forfeiture to the wallet domain.
        walletService.handleAccountClosure(userIdStr, request.getConfirmForfeitBalance());

        // State transition and PII anonymization for account closure.
        user.setAccountStatus(UserStatus.CLOSED);
        user.setClosedAt(OffsetDateTime.now());
        user.setEmail("closed_" + userId + "@walletService.internal");
        user.setPasswordHash(null);
        user.setGoogleId(null);

        userRepository.save(user);

        // Revoke active refresh tokens immediately after closure.
        refreshTokenRepository.deleteByUserId(userId);

        log.info("Account permanently closed and data scrubbed for user ID: {}", userId);
    }
}
