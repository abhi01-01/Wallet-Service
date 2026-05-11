package com.wallet.walletservice.service;

import com.wallet.walletservice.exception.WalletNotFoundException;
import com.wallet.walletservice.repository.AssetTypeRepository;
import com.wallet.walletservice.repository.LedgerEntryRepository;
import com.wallet.walletservice.repository.TransactionRepository;
import com.wallet.walletservice.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private AssetTypeRepository assetTypeRepository;

    @InjectMocks
    private WalletService walletService;

    @Test
    void getLedgerHistory_WalletNotFound_ThrowsException() {
        // Arrange
        String userId = "user123";
        String assetCode = "BTC";
        when(walletRepository.findByOwnerIdAndAssetCode(userId, assetCode)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(WalletNotFoundException.class, () -> {
            walletService.getLedgerHistory(userId, assetCode);
        });

        // Verify that findWallet (and thus createWallet) was NOT called in a way that would trigger an insert
        // Since we mocked walletRepository, we can verify it was only called for lookup
        verify(walletRepository, times(1)).findByOwnerIdAndAssetCode(userId, assetCode);
        verifyNoMoreInteractions(walletRepository);
        verifyNoInteractions(ledgerEntryRepository);
    }
}
