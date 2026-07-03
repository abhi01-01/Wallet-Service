package com.wallet.walletservice.service.wallet.transfer;

import com.wallet.walletservice.domain.entity.AssetType;
import com.wallet.walletservice.domain.entity.Transaction;
import com.wallet.walletservice.domain.entity.Wallet;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.domain.enums.TransactionStatus;
import com.wallet.walletservice.domain.enums.TransactionType;
import com.wallet.walletservice.messaging.outbox.OutboxEventService;
import com.wallet.walletservice.repository.LedgerEntryRepository;
import com.wallet.walletservice.repository.TransactionRepository;
import com.wallet.walletservice.repository.WalletRepository;
import com.wallet.walletservice.service.wallet.support.WalletProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletTransferServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;
    @Mock
    private WalletProvider walletProvider;
    @Mock
    private OutboxEventService outboxEventService;

    private WalletTransferService walletTransferService;

    @BeforeEach
    void setUp() {
        walletTransferService = new WalletTransferService(
                        walletRepository,
                        transactionRepository,
                        ledgerEntryRepository,
                        walletProvider,
                new SimpleMeterRegistry(),
                outboxEventService
                );
    }

    @Test
    void transfer_WhenIdempotencyKeyExists_ReturnsExistingTransactionWithoutMutation() {
        TransferCommand command = command();
        Transaction existing = Transaction.builder()
                .idempotencyKey(command.getIdempotencyKey())
                .transactionType(TransactionType.TOPUP)
                .status(TransactionStatus.SUCCESS)
                .build();
        when(transactionRepository.findByIdempotencyKey(command.getIdempotencyKey()))
                .thenReturn(Optional.of(existing));

        Transaction actual = walletTransferService.transfer(command, List.of(), "TopUp");

        assertSame(existing, actual);
        verifyNoInteractions(walletProvider, walletRepository, ledgerEntryRepository, outboxEventService);
    }

    @Test
    void transfer_LocksWalletsInSortedOrderAndWritesDoubleEntryLedger() {
        TransferCommand command = command();
        Wallet debitWallet = wallet(2L, "SYSTEM_TREASURY", "100.0000");
        Wallet creditWallet = wallet(1L, "user-1", "10.0000");

        when(transactionRepository.findByIdempotencyKey(command.getIdempotencyKey()))
                .thenReturn(Optional.empty());
        when(walletProvider.findOrCreate(command.getDebitOwnerId(), command.getAssetCode()))
                .thenReturn(debitWallet);
        when(walletProvider.findOrCreate(command.getCreditOwnerId(), command.getAssetCode()))
                .thenReturn(creditWallet);
        when(walletRepository.findAllByIdForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(creditWallet, debitWallet));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerEntryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Transaction actual = walletTransferService.transfer(command, List.of(), "TopUp");

        assertEquals(new BigDecimal("95.0000"), debitWallet.getBalance());
        assertEquals(new BigDecimal("15.0000"), creditWallet.getBalance());
        assertEquals(TransactionType.TOPUP, actual.getTransactionType());
        assertEquals(2, actual.getLedgerEntries().size());
        verify(outboxEventService).recordWalletTransactionPosted(actual);
    }

    private TransferCommand command() {
        return TransferCommand.builder()
                .idempotencyKey("key-1")
                .assetCode("GOLD")
                .debitOwnerId("SYSTEM_TREASURY")
                .creditOwnerId("user-1")
                .amount(new BigDecimal("5.0000"))
                .transactionType(TransactionType.TOPUP)
                .defaultDescriptionSupplier(() -> "Top-up")
                .build();
    }

    private Wallet wallet(Long id, String ownerId, String balance) {
        return Wallet.builder()
                .id(id)
                .ownerId(ownerId)
                .ownerType(ownerId.equals("SYSTEM_TREASURY") ? OwnerType.SYSTEM : OwnerType.USER)
                .assetType(AssetType.builder().id(100L).name("Gold").code("GOLD").build())
                .balance(new BigDecimal(balance))
                .build();
    }
}
