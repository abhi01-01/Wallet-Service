package com.wallet.walletservice.service.admin;

import com.wallet.walletservice.domain.enums.OwnerType;
import com.wallet.walletservice.domain.enums.UserStatus;
import com.wallet.walletservice.dto.response.UserOptionResponse;
import com.wallet.walletservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserQueryService {

    private static final int MAX_OPTIONS = 100;

    private final UserRepository userRepository;

    public List<UserOptionResponse> getUserOptions(String query) {
        return userRepository.findUserOptions(
                        UserStatus.ACTIVE,
                        OwnerType.USER,
                        normalizeQuery(query),
                        PageRequest.of(0, MAX_OPTIONS)
                )
                .stream()
                .map(UserOptionResponse::from)
                .toList();
    }

    private String normalizeQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }

        return query.trim();
    }
}