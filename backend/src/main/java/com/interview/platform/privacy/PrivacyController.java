package com.interview.platform.privacy;

import com.interview.platform.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@RequestMapping("/api/v1/privacy/requests")
class PrivacyController {
    private final PrivacyService privacy;

    PrivacyController(PrivacyService privacy) { this.privacy = privacy; }

    @PostMapping
    ApiResponse<PrivacyService.RequestView> create(@AuthenticationPrincipal String userId,
                                                   @Valid @RequestBody CreateRequest request) {
        return ApiResponse.ok(privacy.create(userId, request.type(), request.password()));
    }

    @GetMapping
    ApiResponse<List<PrivacyService.RequestView>> list(@AuthenticationPrincipal String userId) {
        return ApiResponse.ok(privacy.list(userId));
    }

    @GetMapping("/{requestId}/download")
    ResponseEntity<String> download(@AuthenticationPrincipal String userId,
                                    @PathVariable String requestId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .header("Content-Disposition", "attachment; filename=personal-data.json")
                .body(privacy.download(requestId, userId));
    }

    record CreateRequest(@NotBlank String type, String password) {}
}
