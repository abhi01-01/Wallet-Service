package com.wallet.walletservice.service.auth.account;

import com.wallet.walletservice.domain.entity.AssetType;
import com.wallet.walletservice.domain.entity.RefreshToken;
import com.wallet.walletservice.domain.entity.User;
import com.wallet.walletservice.domain.entity.Wallet;
import com.wallet.walletservice.domain.enums.AuthProvider;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.domain.enums.TransactionType;
import com.wallet.walletservice.domain.enums.UserStatus;
import com.wallet.walletservice.dto.request.AccountCloseRequest;
import com.wallet.walletservice.repository.AssetTypeRepository;
import com.wallet.walletservice.repository.LedgerEntryRepository;
import com.wallet.walletservice.repository.RefreshTokenRepository;
import com.wallet.walletservice.repository.TransactionRepository;
import com.wallet.walletservice.repository.UserRepository;
import com.wallet.walletservice.repository.WalletRepository;
import com.wallet.walletservice.service.auth.AuthService;
import com.wallet.walletservice.service.wallet.support.WalletProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:accountclosuretest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
class AccountClosureServiceIntegrationTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private AssetTypeRepository assetTypeRepository;
    @Autowired
    private WalletRepository walletRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void closeAccount_WhenForfeitConfirmed_MovesBalanceToTreasuryScrubsPiiAndRevokesSessions() {
        User user = userRepository.save(User.builder()
                .email("closable@example.com")
                .passwordHash("hashed-password")
                .provider(AuthProvider.EMAIL)
                .ownerType(OwnerType.USER)
                .emailVerified(true)
                .accountStatus(UserStatus.ACTIVE)
                .build());

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token("refresh-token-for-closure")
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build());

        AssetType gold = assetTypeRepository.save(AssetType.builder()
                .name("Gold Coins")
                .code("GOLD")
                .description("Test asset")
                .build());

        walletRepository.save(Wallet.builder()
                .ownerId(WalletProvider.SYSTEM_TREASURY)
                .ownerType(OwnerType.SYSTEM)
                .assetType(gold)
                .balance(new BigDecimal("1000.0000"))
                .build());

        walletRepository.save(Wallet.builder()
                .ownerId(user.getId().toString())
                .ownerType(OwnerType.USER)
                .assetType(gold)
                .balance(new BigDecimal("25.0000"))
                .build());

        AccountCloseRequest request = new AccountCloseRequest();
        request.setConfirmForfeitBalance(true);

        authService.closeAccount(user.getId().toString(), request);

        User closedUser = userRepository.findById(user.getId()).orElseThrow();
        assertEquals(UserStatus.CLOSED, closedUser.getAccountStatus());
        assertEquals("closed_" + user.getId() + "@walletService.internal", closedUser.getEmail());
        assertNull(closedUser.getPasswordHash());
        assertNull(closedUser.getGoogleId());
        assertTrue(refreshTokenRepository.findByToken("refresh-token-for-closure").isEmpty());

        Wallet userWallet = walletRepository.findByOwnerIdAndAssetCode(user.getId().toString(), "GOLD").orElseThrow();
        Wallet treasuryWallet = walletRepository.findByOwnerIdAndAssetCode(WalletProvider.SYSTEM_TREASURY, "GOLD").orElseThrow();
        assertEquals(0, userWallet.getBalance().compareTo(new BigDecimal("0.0000")));
        assertEquals(0, treasuryWallet.getBalance().compareTo(new BigDecimal("1025.0000")));
        assertEquals(1, transactionRepository.findAll().stream()
                .filter(txn -> txn.getTransactionType() == TransactionType.FORFEIT)
                .count());
        assertEquals(2, ledgerEntryRepository.count());
    }
}
