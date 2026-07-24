package com.interview.platform.report;

import com.interview.platform.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/v1/interviews/{sessionId}/report")
public class ReportController {
    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    ApiResponse<ReportService.ReportView> get(@AuthenticationPrincipal String userId,
                                              @PathVariable String sessionId) {
        return ApiResponse.ok(reportService.get(sessionId, userId));
    }

    @PostMapping("/retry")
    ApiResponse<ReportService.ReportView> retry(@AuthenticationPrincipal String userId,
                                                @PathVariable String sessionId) {
        return ApiResponse.ok(reportService.retry(sessionId, userId));
    }

    @PostMapping("/feedback")
    ApiResponse<ReportService.FeedbackView> feedback(@AuthenticationPrincipal String userId,
                                                      @PathVariable String sessionId,
                                                      @RequestBody ReportService.FeedbackCommand request) {
        return ApiResponse.ok(reportService.feedback(sessionId, userId, request));
    }
}
