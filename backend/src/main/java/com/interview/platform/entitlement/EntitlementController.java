package com.interview.platform.entitlement;

import com.interview.platform.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/entitlements")
class EntitlementController {
    private final EntitlementService entitlements;

    EntitlementController(EntitlementService entitlements) {
        this.entitlements = entitlements;
    }

    @GetMapping
    ApiResponse<EntitlementService.AccountView> account(@AuthenticationPrincipal String userId) {
        return ApiResponse.ok(entitlements.account(userId));
    }

    @GetMapping("/ledger")
    ApiResponse<List<EntitlementService.LedgerView>> ledger(@AuthenticationPrincipal String userId) {
        return ApiResponse.ok(entitlements.ledger(userId));
    }
}
