package com.interview.platform.practice;

import com.interview.platform.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/practice")
class PracticeController {
    private final PracticeService practice;
    PracticeController(PracticeService practice) { this.practice = practice; }

    @GetMapping("/dashboard")
    ApiResponse<PracticeService.DashboardView> dashboard(@AuthenticationPrincipal String userId) {
        return ApiResponse.ok(practice.dashboard(userId));
    }

    @GetMapping("/questions")
    ApiResponse<List<PracticeService.QuestionView>> questions(
            @AuthenticationPrincipal String userId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "ALL") String collection) {
        return ApiResponse.ok(practice.library(userId, category, type, collection));
    }

    @PostMapping("/sessions")
    ApiResponse<PracticeService.SessionView> create(@AuthenticationPrincipal String userId,
                                                     @Valid @RequestBody CreateSessionRequest request) {
        return ApiResponse.ok(practice.createSession(userId, new PracticeService.CreateSessionCommand(
                request.mode(), request.categoryCode(), request.questionCount())));
    }

    @GetMapping("/sessions/{sessionId}")
    ApiResponse<PracticeService.SessionView> session(@AuthenticationPrincipal String userId,
                                                      @PathVariable String sessionId) {
        return ApiResponse.ok(practice.getSession(sessionId, userId));
    }

    @GetMapping("/sessions/{sessionId}/review")
    ApiResponse<List<PracticeService.ReviewItem>> review(@AuthenticationPrincipal String userId,
                                                          @PathVariable String sessionId) {
        return ApiResponse.ok(practice.review(sessionId, userId));
    }

    @PostMapping("/sessions/{sessionId}/answers")
    ApiResponse<PracticeService.AnswerResult> answer(@AuthenticationPrincipal String userId,
                                                      @PathVariable String sessionId,
                                                      @Valid @RequestBody AnswerRequest request) {
        return ApiResponse.ok(practice.answer(sessionId, userId, new PracticeService.AnswerCommand(
                request.questionId(), request.answers(), request.durationSeconds())));
    }

    @PostMapping("/questions/{questionId}/favorite")
    ApiResponse<PracticeService.FavoriteView> favorite(@AuthenticationPrincipal String userId,
                                                        @PathVariable String questionId) {
        return ApiResponse.ok(practice.toggleFavorite(questionId, userId));
    }

    @PostMapping("/sessions/{sessionId}/share")
    ApiResponse<PracticeService.ShareCreated> share(@AuthenticationPrincipal String userId,
                                                     @PathVariable String sessionId) {
        return ApiResponse.ok(practice.createShare(sessionId, userId));
    }

    record CreateSessionRequest(@NotBlank String mode, @Size(max = 40) String categoryCode,
                                @Min(1) @Max(50) Integer questionCount) {}
    record AnswerRequest(@NotBlank String questionId, @NotNull @Size(max = 10) List<String> answers,
                         @Min(0) @Max(3600) int durationSeconds) {}
}
