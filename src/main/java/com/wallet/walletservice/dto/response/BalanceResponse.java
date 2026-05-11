package com.wallet.walletservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class BalanceResponse {
    private String userId;
    private List<WalletBalance> wallets;

    @Data
    @Builder
    public static class WalletBalance {
        private Long walletId;
        private String assetCode;
        private String assetName;
        private BigDecimal balance;
    }
}
