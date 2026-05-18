package com.wallet.walletservice.dto.request;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class ForfeitRequest {
    String userId;
    String assetCode;
    BigDecimal amount;
    String idempotencyKey;
    String description;
}
