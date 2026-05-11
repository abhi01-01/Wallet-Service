package com.wallet.walletservice.controller;

import com.wallet.walletservice.dto.request.BonusRequest;
import com.wallet.walletservice.dto.request.SpendRequest;
import com.wallet.walletservice.dto.request.TopUpRequest;
import com.wallet.walletservice.dto.response.ApiResponse;
import com.wallet.walletservice.dto.response.BalanceResponse;
import com.wallet.walletservice.dto.response.LedgerHistoryResponse;
import com.wallet.walletservice.dto.response.TransactionResponse;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallet API", description = "Internal Wallet Service")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService walletService;

    @Value("${wallet.security.authorized-system-ids:}")
    private Set<String> authorizedSystemIds;

    /**
     * USER: can only view their own balance.
     * SYSTEM: can view any user's balance.
     *
     * @AuthenticationPrincipal resolves to the userId (JWT subclaim).
     */

    @GetMapping("/{userId}/balance")
    @PreAuthorize("hasRole('SYSTEM') or (hasRole('USER') and #userId == #principal)")
    @Operation(summary = "Get all wallet balances for a user")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
            @PathVariable String userId,
            @AuthenticationPrincipal String principal
    ){
        return ResponseEntity.ok(ApiResponse.ok(walletService.getBalance(userId)));
    }

    /**
     * SYSTEM only — credits user wallet from system treasury.
     * Restricted to specific whitelisted SYSTEM account IDs.
     */
    @PostMapping("/topUp")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "Top-up: credit a user wallet (simulate real-money purchase) — SYSTEM only")
    public ResponseEntity<ApiResponse<TransactionResponse>> topUp(
            @Valid @RequestBody TopUpRequest req,
            @AuthenticationPrincipal String principal){
        validateSystemAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok("Top-up successful", walletService.topUp(req)));
    }

    /**
     * SYSTEM only — issues free bonus credits.
     * Restricted to specific whitelisted SYSTEM account IDs.
     */
    @PostMapping("/bonus")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "Bonus: system issues free credits to a user — SYSTEM only")
    public ResponseEntity<ApiResponse<TransactionResponse>> bonus(
            @Valid @RequestBody BonusRequest req,
            @AuthenticationPrincipal String principal){
        validateSystemAccess(principal);
        return ResponseEntity.ok(ApiResponse.ok("Bonus issued successfully", walletService.issueBonus(req)));
    }

    /**
     * USER only — spends from their own wallet.
     * Enforces that the spending userId matches the authenticated user.
     */
    @PostMapping("/spend")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Spend: user spends credits on an in-app service — USER only")
    public ResponseEntity<ApiResponse<TransactionResponse>> spend(
            @Valid @RequestBody SpendRequest req,
            @AuthenticationPrincipal String principal){
        // Prevent users from spending on behalf of another user
        if(!req.getUserId().equals(principal)){
             throw new AuthException("You can only spend from your own wallet");
        }
        return ResponseEntity.ok(ApiResponse.ok("Spend Successful", walletService.spend(req)));
    }

    /**
     * USER: own ledger only. SYSTEM: any ledger.
     */
    @GetMapping("/{userId}/ledger")
    @PreAuthorize("hasRole('SYSTEM') or (hasRole('USER') and #userId == #principal)")
    @Operation(summary = "Get ledger history for a user's specific asset wallet")
    public ResponseEntity<ApiResponse<List<LedgerHistoryResponse>>> getLedger(
            @PathVariable String userId,
            @RequestParam String assetCode,
            @AuthenticationPrincipal String principal){
        return ResponseEntity.ok(ApiResponse.ok(walletService.getLedgerHistory(userId, assetCode)));
    }

    private void validateSystemAccess(String principal) {
        if (authorizedSystemIds != null && !authorizedSystemIds.isEmpty() && !authorizedSystemIds.contains(principal)) {
            throw new AuthException("System account not authorized for this sensitive operation.");
        }
    }
}
