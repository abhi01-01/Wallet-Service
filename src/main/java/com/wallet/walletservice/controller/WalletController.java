package com.wallet.walletservice.controller;

import com.wallet.walletservice.dto.request.BonusRequest;
import com.wallet.walletservice.dto.request.SpendRequest;
import com.wallet.walletservice.dto.request.TopUpRequest;
import com.wallet.walletservice.dto.response.BalanceResponse;
import com.wallet.walletservice.dto.response.LedgerHistoryResponse;
import com.wallet.walletservice.dto.response.TransactionResponse;
import com.wallet.walletservice.exception.AuthException;
import com.wallet.walletservice.service.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
@Tag(name = "Wallet", description = "Endpoints for managing user wallets, balances, and transactions")
public class WalletController {

    private final WalletService walletService;

    @Value("${wallet.security.authorized-system-ids:}")
    private Set<String> authorizedSystemIds;

    @GetMapping("/{userId}/balance")
    @PreAuthorize("hasRole('USER') and #userId == authentication.name")
    @Operation(summary = "Get wallet balances", description = "Retrieve all asset balances for the authenticated user.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Balances retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Unauthorized access to user balance"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Wallet not found")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<BalanceResponse>> getBalance(
            @PathVariable String userId
    ){
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok(walletService.getBalance(userId)));
    }

    @PostMapping("/topUp")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "Top-up wallet (System Only)", description = "Credit a user's wallet from the system treasury. Whitelisted system accounts only.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Top-up successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - System account not authorized")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<TransactionResponse>> topUp(
            @Valid @RequestBody TopUpRequest req,
            @AuthenticationPrincipal String principal){
        validateSystemAccess(principal);
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok("Top-up successful", walletService.topUp(req)));
    }

    @PostMapping("/bonus")
    @PreAuthorize("hasRole('SYSTEM')")
    @Operation(summary = "Issue bonus (System Only)", description = "Issue free credits to a user. Whitelisted system accounts only.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Bonus issued successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - System account not authorized")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<TransactionResponse>> bonus(
            @Valid @RequestBody BonusRequest req,
            @AuthenticationPrincipal String principal){
        validateSystemAccess(principal);
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok("Bonus issued successfully", walletService.issueBonus(req)));
    }

    @PostMapping("/spend")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Spend credits", description = "Spend credits from the authenticated user's wallet for in-app services.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Spend successful"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Insufficient balance"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Forbidden - Cannot spend from another user's wallet")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<TransactionResponse>> spend(
            @Valid @RequestBody SpendRequest req,
            @AuthenticationPrincipal String principal){
        if(!req.getUserId().equals(principal)){
             throw new AuthException("You can only spend from your own wallet");
        }
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok("Spend Successful", walletService.spend(req)));
    }

    @GetMapping("/{userId}/ledger")
    @PreAuthorize("hasRole('SYSTEM') or (hasRole('USER') and #userId == authentication.name)")
    @Operation(summary = "Get ledger history", description = "Retrieve the transaction history for a specific asset in a user's wallet.")
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ledger history retrieved successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Asset or Wallet not found")
    })
    public ResponseEntity<com.wallet.walletservice.dto.response.ApiResponse<List<LedgerHistoryResponse>>> getLedger(
            @PathVariable String userId,
            @RequestParam String assetCode
    ){
        return ResponseEntity.ok(com.wallet.walletservice.dto.response.ApiResponse.ok(walletService.getLedgerHistory(userId, assetCode)));
    }

    private void validateSystemAccess(String principal) {
        if (authorizedSystemIds != null && !authorizedSystemIds.isEmpty() && !authorizedSystemIds.contains(principal)) {
            throw new AuthException("System account not authorized for this sensitive operation.");
        }
    }
}
