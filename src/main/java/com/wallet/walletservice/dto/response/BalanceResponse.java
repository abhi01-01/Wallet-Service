package com.wallet.walletservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "Response containing wallet balances for a user")
public class BalanceResponse {
    @Schema(description = "User ID", example = "user123")
    private String userId;

    @Schema(description = "List of asset wallets and their balances")
    private List<WalletBalance> wallets;

    @Data
    @Builder
    @Schema(description = "Details of a specific asset wallet")
    public static class WalletBalance {
        @Schema(description = "Internal wallet ID", example = "1")
        private Long walletId;

        @Schema(description = "Code of the asset", example = "GOLD")
        private String assetCode;

        @Schema(description = "Name of the asset", example = "Gold Credits")
        private String assetName;

        @Schema(description = "Current balance in this wallet", example = "150.75")
        private BigDecimal balance;
    }
}
