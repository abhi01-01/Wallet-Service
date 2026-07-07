package com.wallet.walletservice.service.wallet.transfer;

import com.wallet.walletservice.domain.entity.LedgerEntry;
import com.wallet.walletservice.domain.entity.Transaction;
import com.wallet.walletservice.domain.entity.Wallet;
import com.wallet.walletservice.domain.enums.EntryType;
import com.wallet.walletservice.domain.enums.TransactionStatus;
import com.wallet.walletservice.domain.enums.TransactionType;
import com.wallet.walletservice.messaging.outbox.OutboxEventService;
import com.wallet.walletservice.repository.LedgerEntryRepository;
import com.wallet.walletservice.repository.TransactionRepository;
import com.wallet.walletservice.repository.WalletRepository;
import com.wallet.walletservice.service.wallet.policy.WalletTransferPolicy;
import com.wallet.walletservice.service.wallet.support.WalletProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletTransferService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletProvider walletProvider;
    // 1. Inject the Telemetry Engine
    private final MeterRegistry meterRegistry;
    private final OutboxEventService outboxEventService;

    public Transaction transfer(
            TransferCommand command,
            List<WalletTransferPolicy> policies,
            String contextLabel
    ) {

        try {
            Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(command.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Duplicate {} request detected for key={}, returning cached response",
                        contextLabel, command.getIdempotencyKey());
                return existing.get();
            }

            Wallet debitWallet = walletProvider.findOrCreate(command.getDebitOwnerId(), command.getAssetCode());
            Wallet creditWallet = walletProvider.findOrCreate(command.getCreditOwnerId(), command.getAssetCode());

            /*
             * Deadlock avoidance: always acquire wallet locks in ascending ID order.
             * Concurrent transfers touching the same wallet pair then wait in the same
             * order instead of forming a circular wait.
             */
            Map<Long, Wallet> lockedWallets = lockWallets(sortedIds(debitWallet.getId(), creditWallet.getId()));
            debitWallet = lockedWallets.get(debitWallet.getId());
            creditWallet = lockedWallets.get(creditWallet.getId());

            for (WalletTransferPolicy policy : policies) {
                policy.validate(command, debitWallet, creditWallet);
            }

            debit(debitWallet, command.getAmount());
            credit(creditWallet, command.getAmount());
            walletRepository.saveAll(List.of(debitWallet, creditWallet));

            String description = command.getProvidedDescription() != null
                    ? command.getProvidedDescription()
                    : command.getDefaultDescriptionSupplier().get();
            Transaction txn = saveTransaction(command, description, debitWallet, creditWallet);

            String sign = command.getTransactionType() == TransactionType.SPEND ? "-" : "+";
            String user = command.getTransactionType() == TransactionType.SPEND
                    ? command.getDebitOwnerId()
                    : command.getCreditOwnerId();
            log.info("{} success: txn={}, user={}, {}{} {}",
                    contextLabel, txn.getId(), user, sign, command.getAmount(), command.getAssetCode());

            // 2. Symmetric Success Metric
            meterRegistry.counter("business.ledger.transfers",
                    "operation", "transfer",
                    "status", "success",
                    "error", "none"
            ).increment();

            return txn;
        }catch (Exception e) {
            // 3. Symmetric Failure Metric (Captures exact exceptions like InsufficientBalanceException)
            meterRegistry.counter("business.ledger.transfers",
                    "operation", "transfer",
                    "status", "failed",
                    "error", e.getClass().getSimpleName()
            ).increment();

            throw e;
        }
    }

    private List<Long> sortedIds(Long... ids) {
        return Arrays.stream(ids).sorted().collect(Collectors.toList());
    }

    private Map<Long, Wallet> lockWallets(List<Long> ids) {
        // 4. Start the Micrometer Timer precisely before the DB call
        Timer.Sample lockTimer = Timer.start(meterRegistry);
        try {
            List<Wallet> wallets = walletRepository.findAllByIdForUpdate(ids);
            return wallets.stream().collect(Collectors.toMap(Wallet::getId, w -> w));
        }finally {
            // 5. Stop the timer and record the duration, attaching dimensions
            lockTimer.stop(Timer.builder("db.lock.wait")
                    .description("Time spent waiting for Postgres row-level pessimistic locks")
                    .tag("lock_type", "db_row")
                    .tag("entity", "wallet")
                    .register(meterRegistry));
        }
    }

    private void debit(Wallet wallet, BigDecimal amount) {
        wallet.setBalance(wallet.getBalance().subtract(amount));
    }

    private void credit(Wallet wallet, BigDecimal amount) {
        wallet.setBalance(wallet.getBalance().add(amount));
    }

    private Transaction saveTransaction(
            TransferCommand command,
            String description,
            Wallet debitWallet,
            Wallet creditWallet
    ) {
        /*
         * Double-entry ledger invariant: each successful transfer creates exactly
         * one debit entry and one credit entry tied to the same transaction.
         */
        Transaction txn = Transaction.builder()
                .idempotencyKey(command.getIdempotencyKey())
                .transactionType(command.getTransactionType())
                .description(description)
                .status(TransactionStatus.SUCCESS)
                .build();
        txn = transactionRepository.save(txn);

        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(txn)
                .wallet(debitWallet)
                .entryType(EntryType.DEBIT)
                .amount(command.getAmount())
                .balanceAfter(debitWallet.getBalance())
                .build();

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(txn)
                .wallet(creditWallet)
                .entryType(EntryType.CREDIT)
                .amount(command.getAmount())
                .balanceAfter(creditWallet.getBalance())
                .build();

        ledgerEntryRepository.saveAll(List.of(debitEntry, creditEntry));
        txn.getLedgerEntries().addAll(List.of(debitEntry, creditEntry));

        outboxEventService.recordWalletTransactionPosted(txn);
        return txn;
    }
}
