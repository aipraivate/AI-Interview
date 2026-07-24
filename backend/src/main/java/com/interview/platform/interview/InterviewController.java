package com.interview.platform.interview;

import com.interview.platform.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.http.MediaType;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/interviews")
public class InterviewController {
    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<InterviewService.InterviewView> create(@AuthenticationPrincipal String userId,
                                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                       @Valid @RequestBody InterviewService.CreateInterview request) {
        return ApiResponse.ok(interviewService.create(userId, idempotencyKey, request));
    }

    @GetMapping
    ApiResponse<List<InterviewService.InterviewView>> list(@AuthenticationPrincipal String userId) {
        return ApiResponse.ok(interviewService.list(userId));
    }

    @GetMapping("/{sessionId}")
    ApiResponse<InterviewService.InterviewView> get(@AuthenticationPrincipal String userId,
                                                    @PathVariable String sessionId) {
        return ApiResponse.ok(interviewService.get(sessionId, userId));
    }

    @PostMapping("/{sessionId}/start")
    ApiResponse<InterviewService.InterviewView> start(@AuthenticationPrincipal String userId,
                                                      @PathVariable String sessionId) {
        return ApiResponse.ok(interviewService.start(sessionId, userId));
    }

    @PostMapping("/{sessionId}/pause")
    ApiResponse<InterviewService.InterviewView> pause(@AuthenticationPrincipal String userId,
                                                      @PathVariable String sessionId) {
        return ApiResponse.ok(interviewService.pause(sessionId, userId));
    }

    @PostMapping("/{sessionId}/resume")
    ApiResponse<InterviewService.InterviewView> resume(@AuthenticationPrincipal String userId,
                                                       @PathVariable String sessionId) {
        return ApiResponse.ok(interviewService.resume(sessionId, userId));
    }

    @PostMapping("/{sessionId}/answers")
    ApiResponse<InterviewService.AnswerReceipt> answer(@AuthenticationPrincipal String userId,
                                                       @PathVariable String sessionId,
                                                       @Valid @RequestBody InterviewService.AnswerCommand request) {
        return ApiResponse.ok(interviewService.answer(sessionId, userId, request));
    }

    @PostMapping("/{sessionId}/skip")
    ApiResponse<InterviewService.AnswerReceipt> skip(@AuthenticationPrincipal String userId,
                                                     @PathVariable String sessionId,
                                                     @Valid @RequestBody InterviewService.SkipCommand request) {
        return ApiResponse.ok(interviewService.skip(sessionId, userId, request));
    }

    @PostMapping("/{sessionId}/finish")
    ApiResponse<InterviewService.AnswerReceipt> finish(@AuthenticationPrincipal String userId,
                                                       @PathVariable String sessionId,
                                                       @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ApiResponse.ok(interviewService.finish(sessionId, userId, idempotencyKey));
    }

    @GetMapping("/{sessionId}/messages")
    ApiResponse<List<InterviewService.MessageView>> transcript(@AuthenticationPrincipal String userId,
                                                               @PathVariable String sessionId) {
        return ApiResponse.ok(interviewService.transcript(sessionId, userId));
    }

    @GetMapping(value = "/{sessionId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@AuthenticationPrincipal String userId, @PathVariable String sessionId,
                      @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        int lastSequence = 0;
        try { if (lastEventId != null) lastSequence = Integer.parseInt(lastEventId); }
        catch (NumberFormatException ignored) { lastSequence = 0; }
        SseEmitter emitter = new SseEmitter(15_000L);
        try {
            emitter.send(SseEmitter.event().name("heartbeat").data("{}"));
            for (InterviewService.MessageView message : interviewService.transcript(sessionId, userId)) {
                if (message.sequence() > lastSequence) {
                    emitter.send(SseEmitter.event().id(String.valueOf(message.sequence()))
                            .name("message").data(message));
                }
            }
            emitter.complete();
        } catch (IOException exception) { emitter.completeWithError(exception); }
        return emitter;
    }
}
