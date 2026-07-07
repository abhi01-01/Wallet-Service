package com.wallet.walletservice.controller;

import com.wallet.walletservice.dto.response.ApiResponse;
import com.wallet.walletservice.dto.response.UserOptionResponse;
import com.wallet.walletservice.service.admin.AdminUserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserQueryService adminUserQueryService;

    @GetMapping("/options")
    @PreAuthorize("hasRole('SYSTEM')")
    public ResponseEntity<ApiResponse<List<UserOptionResponse>>> getUserOptions(
            @RequestParam(required = false) String query
    ) {
        List<UserOptionResponse> users = adminUserQueryService.getUserOptions(query);
        return ResponseEntity.ok(ApiResponse.ok(users));
    }
}