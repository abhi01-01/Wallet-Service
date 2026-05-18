package com.wallet.walletservice.service.wallet;

import com.wallet.walletservice.domain.entity.Transaction;
import com.wallet.walletservice.domain.entity.Wallet;
import com.wallet.walletservice.domain.enums.TransactionType;
import com.wallet.walletservice.dto.request.BonusRequest;
import com.wallet.walletservice.dto.request.ForfeitRequest;
import com.wallet.walletservice.dto.request.SpendRequest;
import com.wallet.walletservice.dto.request.TopUpRequest;
import com.wallet.walletservice.dto.response.BalanceResponse;
import com.wallet.walletservice.dto.response.LedgerHistoryResponse;
import com.wallet.walletservice.dto.response.TransactionResponse;
import com.wallet.walletservice.exception.AccountClosureException;
import com.wallet.walletservice.exception.WalletNotFoundException;
import com.wallet.walletservice.repository.LedgerEntryRepository;
import com.wallet.walletservice.repository.WalletRepository;
import com.wallet.walletservice.service.wallet.mapper.TransactionResponseMapper;
import com.wallet.walletservice.service.wallet.operation.WalletOperation;
import com.wallet.walletservice.service.wallet.operation.WalletOperationRegistry;
import com.wallet.walletservice.service.wallet.transfer.WalletTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletTransferService walletTransferService;
    private final WalletOperationRegistry operationRegistry;
    private final TransactionResponseMapper transactionResponseMapper;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse topUp(TopUpRequest req) {
        log.info("TopUp request: user={}, asset={}, amount={}, key={}",
                req.getUserId(), req.getAssetCode(), req.getAmount(), req.getIdempotencyKey());
        return execute(TransactionType.TOPUP, req);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse issueBonus(BonusRequest req) {
        log.info("Bonus request: user={}, asset={}, amount={}, key={}",
                req.getUserId(), req.getAssetCode(), req.getAmount(), req.getIdempotencyKey());
        return execute(TransactionType.BONUS, req);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse spend(SpendRequest req) {
        log.info("Spend request: user={}, asset={}, amount={}, key={}",
                req.getUserId(), req.getAssetCode(), req.getAmount(), req.getIdempotencyKey());
        return execute(TransactionType.SPEND, req);
    }

    /*
     * Extension point for transfer-style wallet features.
     * Add a WalletOperation bean for a new TransactionType, then call this method with
     * that request object. The transfer engine and this facade should not need changes.
     */
    public <T> TransactionResponse execute(TransactionType transactionType, T request) {
        WalletOperation<T> operation = operationRegistry.get(transactionType);
        Transaction txn = walletTransferService.transfer(
                operation.toCommand(request),
                operation.policies(),
                operation.contextLabel()
        );
        return transactionResponseMapper.toResponse(txn);
    }

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

    /*
     * Account closure is an orchestration use case: it checks whether funds exist,
     * requires explicit forfeiture consent, then delegates each positive balance to
     * the same transfer pipeline used by normal wallet movement.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleAccountClosure(String userId, Boolean confirmForfeit) {
        List<Wallet> userWallets = walletRepository.findAllByOwnerId(userId);

        boolean hasPositiveBalance = userWallets.stream()
                .anyMatch(w -> w.getBalance().compareTo(BigDecimal.ZERO) > 0);

        if (hasPositiveBalance && !confirmForfeit) {
            throw new AccountClosureException(
                    "Account holds a positive balance. Please withdraw funds or explicitly confirm forfeiture to proceed.");
        }

        if (hasPositiveBalance) {
            String closureTraceId = "closure_" + UUID.randomUUID() + "_";

            for (Wallet wallet : userWallets) {
                if (wallet.getBalance().compareTo(BigDecimal.ZERO) > 0) {
                    ForfeitRequest request = ForfeitRequest.builder()
                            .idempotencyKey(closureTraceId + wallet.getAssetType().getCode())
                            .userId(userId)
                            .assetCode(wallet.getAssetType().getCode())
                            .amount(wallet.getBalance())
                            .description("Account Closure Balance Forfeiture")
                            .build();

                    execute(TransactionType.FORFEIT, request);
                    log.info("Forfeited {} {} from user {} to SYSTEM_TREASURY",
                            wallet.getBalance(),
                            wallet.getAssetType().getCode(),
                            userId);
                }
            }
        }
    }
}
