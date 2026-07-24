package com.interview.platform.resume;

import com.interview.platform.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/resumes")
public class ResumeController {
    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ResumeService.ResumeView> create(@AuthenticationPrincipal String userId,
                                                 @Valid @RequestBody ResumeService.CreateResume request) {
        return ApiResponse.ok(resumeService.create(userId, request));
    }

    @GetMapping
    ApiResponse<List<ResumeService.ResumeView>> list(@AuthenticationPrincipal String userId) {
        return ApiResponse.ok(resumeService.list(userId));
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ResumeService.ResumeView> upload(@AuthenticationPrincipal String userId,
                                                 @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(resumeService.upload(userId, file));
    }

    @GetMapping("/{resumeId}")
    ApiResponse<ResumeService.ResumeView> get(@AuthenticationPrincipal String userId,
                                              @PathVariable String resumeId) {
        return ApiResponse.ok(resumeService.get(resumeId, userId));
    }

    @PostMapping("/{resumeId}/confirm")
    ApiResponse<ResumeService.ResumeView> confirm(@AuthenticationPrincipal String userId,
                                                  @PathVariable String resumeId,
                                                  @Valid @RequestBody ResumeService.ConfirmResume request) {
        return ApiResponse.ok(resumeService.confirm(resumeId, userId, request));
    }

    @PostMapping("/{resumeId}/default")
    ApiResponse<ResumeService.ResumeView> makeDefault(@AuthenticationPrincipal String userId,
                                                      @PathVariable String resumeId) {
        return ApiResponse.ok(resumeService.makeDefault(resumeId, userId));
    }

    @GetMapping("/{resumeId}/versions")
    ApiResponse<List<ResumeService.ResumeVersionView>> versions(@AuthenticationPrincipal String userId,
                                                                @PathVariable String resumeId) {
        return ApiResponse.ok(resumeService.versions(resumeId, userId));
    }
}
