package com.interview.platform.analytics;

import com.interview.platform.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/analytics/events")
class AnalyticsController {
    private final AnalyticsService analytics;
    AnalyticsController(AnalyticsService analytics) { this.analytics = analytics; }
    @PostMapping ApiResponse<Void> record(@AuthenticationPrincipal String userId,
                                          @RequestBody AnalyticsService.Command request) {
        analytics.record(userId, request); return ApiResponse.ok(null);
    }
}
