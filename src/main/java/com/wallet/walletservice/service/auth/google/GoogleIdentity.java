package com.wallet.walletservice.service.auth.google;

import lombok.Value;

@Value
public class GoogleIdentity {
    String googleId;
    String email;
}
