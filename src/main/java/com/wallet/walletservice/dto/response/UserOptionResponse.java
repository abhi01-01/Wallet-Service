package com.wallet.walletservice.dto.response;

import com.wallet.walletservice.domain.entity.User;

public record UserOptionResponse(
        String userId,
        String email,
        String ldap,
        String ownerType
) {
    public static UserOptionResponse from(User user) {
        String email = user.getEmail();
        String ldap = email != null && email.contains("@")
                ? email.substring(0, email.indexOf("@"))
                : email;

        return new UserOptionResponse(
                user.getId().toString(),
                email,
                ldap,
                user.getOwnerType().name()
        );
    }
}