package com.interview.platform.practice;

import com.interview.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shares")
class PracticeShareController {
    private final PracticeService practice;
    PracticeShareController(PracticeService practice) { this.practice = practice; }

    @GetMapping("/{token}")
    ApiResponse<PracticeService.ShareView> get(@PathVariable String token) {
        return ApiResponse.ok(practice.getShare(token));
    }
}
