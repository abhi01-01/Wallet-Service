package com.wallet.walletservice.service;


import com.wallet.walletservice.domain.entity.AssetType;
import com.wallet.walletservice.domain.entity.LedgerEntry;
import com.wallet.walletservice.domain.entity.Transaction;
import com.wallet.walletservice.domain.entity.Wallet;
import com.wallet.walletservice.domain.enums.EntryType;
import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.domain.enums.TransactionStatus;
import com.wallet.walletservice.domain.enums.TransactionType;
import com.wallet.walletservice.dto.request.BonusRequest;
import com.wallet.walletservice.dto.request.TopUpRequest;
import com.wallet.walletservice.dto.request.SpendRequest;
import com.wallet.walletservice.dto.response.BalanceResponse;
import com.wallet.walletservice.dto.response.LedgerHistoryResponse;
import com.wallet.walletservice.dto.response.TransactionResponse;
import com.wallet.walletservice.exception.AssetTypeNotFoundException;
import com.wallet.walletservice.exception.InsufficientBalanceException;
import com.wallet.walletservice.exception.WalletNotFoundException;
import com.wallet.walletservice.repository.AssetTypeRepository;
import com.wallet.walletservice.repository.LedgerEntryRepository;
import com.wallet.walletservice.repository.TransactionRepository;
import com.wallet.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private static final String SYSTEM_TREASURY = "SYSTEM_TREASURY";

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AssetTypeRepository assetTypeRepository; //  Required for lazy initialization

    // ──────────────────────────────────────────────────────────────
    // 1. WALLET TOP-UP  (user purchases credits)
    // ──────────────────────────────────────────────────────────────

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse topUp(TopUpRequest req) {
        log.info("TopUp request: user={}, asset={}, amount={}, key={}",
                req.getUserId(), req.getAssetCode(), req.getAmount(), req.getIdempotencyKey());
        Transaction txn = processTransfer(
                req.getIdempotencyKey(),
                req.getAssetCode(),
                SYSTEM_TREASURY,
                req.getUserId(),
                req.getAmount(),
                TransactionType.TOPUP,
                req.getDescription(),
                () -> String.format("Top-up: %s %s for user %s", req.getAmount(), req.getAssetCode(), req.getUserId()),
                null,
                () -> log.info("Duplicate topUp request detected for key={}, returning cached response", req.getIdempotencyKey())
        );

        log.info("TopUp success: txn={}, user={}, +{} {}", txn.getId(), req.getUserId(), req.getAmount(), req.getAssetCode());
        return buildTransactionResponse(txn);
    }


    // ──────────────────────────────────────────────────────────────
    // 2. BONUS / INCENTIVE  (system issues free credits)
    // ──────────────────────────────────────────────────────────────

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse issueBonus(BonusRequest req) {
        log.info("Bonus request: user={}, asset={}, amount={}, key={}",
                req.getUserId(), req.getAssetCode(), req.getAmount(), req.getIdempotencyKey());
        Transaction txn = processTransfer(
                req.getIdempotencyKey(),
                req.getAssetCode(),
                SYSTEM_TREASURY,
                req.getUserId(),
                req.getAmount(),
                TransactionType.BONUS,
                req.getDescription(),
                () -> String.format("Bonus issued: %s %s to user %s", req.getAmount(), req.getAssetCode(), req.getUserId()),
                null,
                null
        );

        log.info("Bonus success: txn={}, user={}, +{} {}", txn.getId(), req.getUserId(), req.getAmount(), req.getAssetCode());
        return buildTransactionResponse(txn);
    }

    // ──────────────────────────────────────────────────────────────
    // 3. SPEND (user spends credits on a service)
    // ──────────────────────────────────────────────────────────────

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse spend(SpendRequest req) {
        log.info("Spend request: user={}, asset={}, amount={}, key={}",
                req.getUserId(), req.getAssetCode(), req.getAmount(), req.getIdempotencyKey());
        Transaction txn = processTransfer(
                req.getIdempotencyKey(),
                req.getAssetCode(),
                req.getUserId(),
                SYSTEM_TREASURY,
                req.getAmount(),
                TransactionType.SPEND,
                req.getDescription(),
                () -> String.format("Spend: %s %s by user %s", req.getAmount(), req.getAssetCode(), req.getUserId()),
                debitWallet -> ensureSufficientBalance(debitWallet, req),
                null
        );

        log.info("Spend success: txn={}, user={}, -{} {}", txn.getId(), req.getUserId(), req.getAmount(), req.getAssetCode());
        return buildTransactionResponse(txn);
    }

    // ──────────────────────────────────────────────────────────────
    // 4. BALANCE QUERY
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String userId) {
        List<Wallet> wallets = walletRepository.findAllByOwnerId(userId);
        if (wallets.isEmpty()) {
            throw new WalletNotFoundException(userId, "any");
        }
        List<BalanceResponse.WalletBalance> balances = wallets.stream()
                .map(w -> BalanceResponse.WalletBalance.builder()
                        .walletId(w.getId())
                        .assetCode(w.getAssetType().getCode())
                        .assetName(w.getAssetType().getName())
                        .balance(w.getBalance())
                        .build())
                .collect(Collectors.toList());

        return BalanceResponse.builder()
                .userId(userId)
                .wallets(balances)
                .build();
    }

    // ──────────────────────────────────────────────────────────────
    // 5. LEDGER HISTORY
    // ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<LedgerHistoryResponse> getLedgerHistory(String userId, String assetCode) {
        Wallet wallet = walletRepository.findByOwnerIdAndAssetCode(userId, assetCode)
                .orElseThrow(() -> new WalletNotFoundException(userId, assetCode));
        return ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId())
                .stream()
                .map(e -> LedgerHistoryResponse.builder()
                        .entryId(e.getId())
                        .transactionId(e.getTransaction().getId())
                        .transactionType(e.getTransaction().getTransactionType().name())
                        .entryType(e.getEntryType().name())
                        .amount(e.getAmount())
                        .balanceAfter(e.getBalanceAfter())
                        .createdAt(e.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────────────────────────

    private Wallet findWallet(String ownerId, String assetCode) {
        return walletRepository.findByOwnerIdAndAssetCode(ownerId, assetCode)
                .orElseGet(() -> createWallet(ownerId, assetCode));
    }

    /**
     * Lazy Initialization: Creates a 0-balance wallet just-in-time.
     */

    private Wallet createWallet(String ownerId, String assetCode){
        AssetType assetType = assetTypeRepository.findByCode(assetCode)
                .orElseThrow(() -> new AssetTypeNotFoundException(assetCode));

        OwnerType type = ownerId.equals(SYSTEM_TREASURY) ? OwnerType.SYSTEM : OwnerType.USER ;

        Wallet newWallet = Wallet.builder()
                .ownerId(ownerId)
                .ownerType(type)
                .assetType(assetType)
                .balance(BigDecimal.ZERO)
                .build();

        log.info("Lazily initialized new 0-balance wallet for owner={} with asset={}", ownerId, assetCode);

        // Use saveAndFlush to execute the insert immediately.
        // This ensures the row exists before the pessimistic locks (SELECT FOR UPDATE) are attempted in the main transaction.
        return walletRepository.saveAndFlush(newWallet);
    }

    /**
     * Deadlock Avoidance Strategy:
     * Always sort wallet IDs in ascending order before locking.
     * This guarantees all concurrent transactions acquire locks in the same order,
     * eliminating circular-wait conditions that cause deadlocks.
     */
    private List<Long> sortedIds(Long... ids) {
        return Arrays.stream(ids).sorted().collect(Collectors.toList());
    }

    private Map<Long, Wallet> lockWallets(List<Long> ids) {
        List<Wallet> wallets = walletRepository.findAllByIdForUpdate(ids);
        return wallets.stream().collect(Collectors.toMap(Wallet::getId, w -> w));
    }

    private Transaction processTransfer(String idempotencyKey,
                                        String assetCode,
                                        String debitOwnerId,
                                        String creditOwnerId,
                                        BigDecimal amount,
                                        TransactionType transactionType,
                                        String providedDescription,
                                        Supplier<String> defaultDescriptionSupplier,
                                        Consumer<Wallet> preDebitValidator,
                                        Runnable duplicateRequestLogger) {
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (duplicateRequestLogger != null) {
                duplicateRequestLogger.run();
            }
            return existing.get();
        }

        Wallet debitWallet = findWallet(debitOwnerId, assetCode);
        Wallet creditWallet = findWallet(creditOwnerId, assetCode);

        Map<Long, Wallet> lockedWallets = lockWallets(sortedIds(debitWallet.getId(), creditWallet.getId()));
        debitWallet = lockedWallets.get(debitWallet.getId());
        creditWallet = lockedWallets.get(creditWallet.getId());

        if (preDebitValidator != null) {
            preDebitValidator.accept(debitWallet);
        }

        debit(debitWallet, amount);
        credit(creditWallet, amount);
        walletRepository.saveAll(List.of(debitWallet, creditWallet));

        String description = providedDescription != null ? providedDescription : defaultDescriptionSupplier.get();
        return saveTransaction(idempotencyKey, transactionType, description, debitWallet, creditWallet, amount);
    }

    private void ensureSufficientBalance(Wallet debitWallet, SpendRequest req) {
        // Validate under lock to avoid concurrent overspend races.
        if (debitWallet.getBalance().compareTo(req.getAmount()) < 0) {
            throw new InsufficientBalanceException(
                    req.getUserId(), req.getAssetCode(), req.getAmount(), debitWallet.getBalance());
        }
    }

    private void debit(Wallet wallet, BigDecimal amount) {
        wallet.setBalance(wallet.getBalance().subtract(amount));
    }

    private void credit(Wallet wallet, BigDecimal amount) {
        wallet.setBalance(wallet.getBalance().add(amount));
    }

    /**
     * Double-Entry Ledger: creates exactly two LedgerEntry rows per transaction.
     * debitWallet -> DEBIT entry (money leaves)
     * creditWallet -> CREDIT entry (money arrives)
     */
    private Transaction saveTransaction(String idempotencyKey, TransactionType type,
                                        String description,
                                        Wallet debitWallet, Wallet creditWallet,
                                        BigDecimal amount) {
        Transaction txn = Transaction.builder()
                .idempotencyKey(idempotencyKey)
                .transactionType(type)
                .description(description)
                .status(TransactionStatus.SUCCESS)
                .build();
        txn = transactionRepository.save(txn);

        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(txn)
                .wallet(debitWallet)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .balanceAfter(debitWallet.getBalance())
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(txn)
                .wallet(creditWallet)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .balanceAfter(creditWallet.getBalance())
                .build();

        ledgerEntryRepository.saveAll(List.of(debitEntry, creditEntry));
        txn.getLedgerEntries().addAll(List.of(debitEntry, creditEntry));
        return txn;
    }

    private TransactionResponse buildTransactionResponse(Transaction txn) {
        List<TransactionResponse.LedgerEntryDto> entries = txn.getLedgerEntries().stream()
                .map(e -> TransactionResponse.LedgerEntryDto.builder()
                        .entryId(e.getId())
                        .walletId(e.getWallet().getId())
                        .ownerId(e.getWallet().getOwnerId())
                        .entryType(e.getEntryType().name())
                        .amount(e.getAmount())
                        .balanceAfter(e.getBalanceAfter())
                        .build())
                .collect(Collectors.toList());

        return TransactionResponse.builder()
                .transactionId(txn.getId())
                .idempotencyKey(txn.getIdempotencyKey())
                .type(txn.getTransactionType().name())
                .status(txn.getStatus().name())
                .description(txn.getDescription())
                .createdAt(txn.getCreatedAt())
                .ledgerEntries(entries)
                .build();
    }
}
