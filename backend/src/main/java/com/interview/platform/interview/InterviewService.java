package com.interview.platform.interview;

import com.interview.platform.ai.AiGateway;
import com.interview.platform.common.BusinessException;
import com.interview.platform.entitlement.EntitlementService;
import com.interview.platform.outbox.OutboxService;
import com.interview.platform.report.ReportService;
import com.interview.platform.resume.ResumeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class InterviewService {
    private static final String CREATE_SCOPE = "INTERVIEW_CREATE";
    private final InterviewSessionRepository sessions;
    private final InterviewMessageRepository messages;
    private final ProcessedCommandRepository commands;
    private final RequestIdempotencyRepository idempotency;
    private final ResumeService resumeService;
    private final EntitlementService entitlements;
    private final AiGateway aiGateway;
    private final ReportService reportService;
    private final OutboxService outbox;
    private final ObjectMapper objectMapper;

    public InterviewService(InterviewSessionRepository sessions, InterviewMessageRepository messages,
                            ProcessedCommandRepository commands, RequestIdempotencyRepository idempotency,
                            ResumeService resumeService,
                            EntitlementService entitlements, AiGateway aiGateway,
                            ReportService reportService, OutboxService outbox,
                            ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.messages = messages;
        this.commands = commands;
        this.idempotency = idempotency;
        this.resumeService = resumeService;
        this.entitlements = entitlements;
        this.aiGateway = aiGateway;
        this.reportService = reportService;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InterviewView create(String userId, String idempotencyKey, CreateInterview command) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "创建面试需要有效的 Idempotency-Key",
                    HttpStatus.BAD_REQUEST);
        }
        String requestHash = requestHash(command);
        RequestIdempotency existing = idempotency
                .findByUserIdAndScopeAndIdempotencyKey(userId, CREATE_SCOPE, idempotencyKey)
                .orElse(null);
        if (existing != null) return replayCreate(existing, requestHash, userId);

        ResumeService.ResumeSnapshot resume = resumeService.requireOwned(command.resumeId(), userId);
        String sessionId = UUID.randomUUID().toString();
        entitlements.reserve(userId, sessionId);

        // reserve() locks the account row until this transaction ends. Recheck after the lock so
        // concurrent retries cannot both create sessions or consume two reservations.
        existing = idempotency.findByUserIdAndScopeAndIdempotencyKey(
                userId, CREATE_SCOPE, idempotencyKey).orElse(null);
        if (existing != null) {
            entitlements.releaseReservation(userId, sessionId);
            return replayCreate(existing, requestHash, userId);
        }
        List<AiGateway.QuestionSpec> questions = aiGateway.generateQuestionSet(resume.targetRole(), command.jdText(),
                resume.content(), command.questionCount());
        InterviewSession session = new InterviewSession(sessionId, userId, resume.id(), resume.targetRole(),
                command.jdText().trim(), resume.content(), writeJson(questions), questions.size());
        sessions.save(session);
        idempotency.save(new RequestIdempotency(userId, CREATE_SCOPE, idempotencyKey,
                requestHash, sessionId));
        return toView(session, questions, null);
    }

    @Transactional
    public InterviewView start(String sessionId, String userId) {
        InterviewSession session = ownedForUpdate(sessionId, userId);
        List<AiGateway.QuestionSpec> questions = readQuestions(session.getQuestionPlan());
        if (session.getStatus() == InterviewStatus.READY) {
            session.start();
            messages.save(new InterviewMessage(sessionId, 1, "INTERVIEWER", questions.get(0).content(), null));
        } else if (session.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw BusinessException.conflict("INVALID_SESSION_STATE", "当前面试不能开始");
        }
        return toView(session, questions, questions.get(session.getCurrentQuestionIndex()).content());
    }

    @Transactional
    public InterviewView pause(String sessionId, String userId) {
        InterviewSession session = ownedForUpdate(sessionId, userId);
        try {
            session.pause();
        } catch (IllegalStateException exception) {
            throw BusinessException.conflict("INVALID_SESSION_STATE", "当前面试不能暂停");
        }
        return toView(session, readQuestions(session.getQuestionPlan()), null);
    }

    @Transactional
    public InterviewView resume(String sessionId, String userId) {
        InterviewSession session = ownedForUpdate(sessionId, userId);
        try {
            session.resume();
        } catch (IllegalStateException exception) {
            throw BusinessException.conflict("INVALID_SESSION_STATE", "当前面试不能恢复");
        }
        List<AiGateway.QuestionSpec> questions = readQuestions(session.getQuestionPlan());
        return toView(session, questions, questions.get(session.getCurrentQuestionIndex()).content());
    }

    @Transactional
    public AnswerReceipt answer(String sessionId, String userId, AnswerCommand command) {
        InterviewSession session = ownedForUpdate(sessionId, userId);
        ProcessedCommand existing = commands.findBySessionIdAndClientMessageId(
                sessionId, command.clientMessageId()).orElse(null);
        if (existing != null) return readJson(existing.getResponseJson(), AnswerReceipt.class);
        if (session.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw BusinessException.conflict("INVALID_SESSION_STATE", "当前面试不能提交回答");
        }

        int current = session.getCurrentQuestionIndex();
        if (!session.isConsumedCredit()) {
            entitlements.confirmReservation(userId, sessionId);
            session.markCreditConsumed();
        }
        messages.save(new InterviewMessage(sessionId, current * 2 + 2, "CANDIDATE",
                command.answer().trim(), command.clientMessageId()));
        List<AiGateway.QuestionSpec> questions = readQuestions(session.getQuestionPlan());
        AnswerReceipt receipt;
        if (current + 1 >= session.getQuestionCount()) {
            completeAndQueue(session, "QUESTION_LIMIT_REACHED");
            receipt = new AnswerReceipt(sessionId, session.getStatus().name(), current + 1,
                    session.getQuestionCount(), null, true);
        } else {
            session.moveToNextQuestion();
            String nextQuestion = questions.get(session.getCurrentQuestionIndex()).content();
            messages.save(new InterviewMessage(sessionId, session.getCurrentQuestionIndex() * 2 + 1,
                    "INTERVIEWER", nextQuestion, null));
            receipt = new AnswerReceipt(sessionId, session.getStatus().name(),
                    session.getCurrentQuestionIndex(), session.getQuestionCount(), nextQuestion, false);
        }
        commands.save(new ProcessedCommand(sessionId, command.clientMessageId(), writeJson(receipt)));
        return receipt;
    }

    @Transactional
    public AnswerReceipt skip(String sessionId, String userId, SkipCommand command) {
        InterviewSession session = ownedForUpdate(sessionId, userId);
        ProcessedCommand existing = commands.findBySessionIdAndClientMessageId(
                sessionId, command.clientMessageId()).orElse(null);
        if (existing != null) return readJson(existing.getResponseJson(), AnswerReceipt.class);
        if (session.getStatus() != InterviewStatus.IN_PROGRESS) {
            throw BusinessException.conflict("INVALID_SESSION_STATE", "当前面试不能跳过题目");
        }
        int current = session.getCurrentQuestionIndex();
        session.skip();
        AnswerReceipt receipt;
        if (current + 1 >= session.getQuestionCount()) {
            receipt = finishSession(session, userId, "QUESTION_LIMIT_REACHED");
        } else {
            session.moveToNextQuestion();
            List<AiGateway.QuestionSpec> questions = readQuestions(session.getQuestionPlan());
            String nextQuestion = questions.get(session.getCurrentQuestionIndex()).content();
            messages.save(new InterviewMessage(sessionId, session.getCurrentQuestionIndex() * 2 + 1,
                    "INTERVIEWER", nextQuestion, null));
            receipt = new AnswerReceipt(sessionId, session.getStatus().name(),
                    session.getCurrentQuestionIndex(), session.getQuestionCount(), nextQuestion, false);
        }
        commands.save(new ProcessedCommand(sessionId, command.clientMessageId(), writeJson(receipt)));
        return receipt;
    }

    @Transactional
    public AnswerReceipt finish(String sessionId, String userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "结束面试需要 Idempotency-Key",
                    HttpStatus.BAD_REQUEST);
        }
        InterviewSession session = ownedForUpdate(sessionId, userId);
        ProcessedCommand existing = commands.findBySessionIdAndClientMessageId(sessionId, idempotencyKey)
                .orElse(null);
        if (existing != null) return readJson(existing.getResponseJson(), AnswerReceipt.class);
        if (session.getStatus() != InterviewStatus.IN_PROGRESS && session.getStatus() != InterviewStatus.PAUSED) {
            throw BusinessException.conflict("INVALID_SESSION_STATE", "当前面试不能结束");
        }
        AnswerReceipt receipt = finishSession(session, userId, "USER_FINISHED");
        commands.save(new ProcessedCommand(sessionId, idempotencyKey, writeJson(receipt)));
        return receipt;
    }

    @Transactional
    public int expireReservations() {
        List<InterviewSession> expired = sessions.findTop100ByStatusAndReservationExpiresAtBefore(
                InterviewStatus.READY, Instant.now());
        expired.forEach(session -> {
            session.expire();
            entitlements.releaseReservation(session.getUserId(), session.getId());
        });
        return expired.size();
    }

    @Transactional(readOnly = true)
    public List<MessageView> transcript(String sessionId, String userId) {
        get(sessionId, userId);
        return messages.findBySessionIdOrderBySequenceNo(sessionId).stream()
                .map(value -> new MessageView(value.getSequenceNo(), value.getRole(),
                        value.getContent(), value.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public InterviewView get(String sessionId, String userId) {
        InterviewSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> BusinessException.notFound("面试不存在"));
        List<AiGateway.QuestionSpec> questions = readQuestions(session.getQuestionPlan());
        String current = (session.getStatus() == InterviewStatus.IN_PROGRESS
                || session.getStatus() == InterviewStatus.PAUSED)
                ? questions.get(session.getCurrentQuestionIndex()).content() : null;
        return toView(session, questions, current);
    }

    @Transactional(readOnly = true)
    public List<InterviewView> list(String userId) {
        return sessions.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(session -> toView(session, readQuestions(session.getQuestionPlan()), null))
                .toList();
    }

    private InterviewSession ownedForUpdate(String sessionId, String userId) {
        return sessions.findOwnedForUpdate(sessionId, userId)
                .orElseThrow(() -> BusinessException.notFound("面试不存在"));
    }

    private InterviewView replayCreate(RequestIdempotency existing, String requestHash, String userId) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw BusinessException.conflict("IDEMPOTENCY_KEY_REUSED",
                    "同一 Idempotency-Key 不能用于不同的创建请求");
        }
        InterviewSession session = sessions.findByIdAndUserId(existing.getResultId(), userId)
                .orElseThrow(() -> BusinessException.notFound("幂等请求对应的面试不存在"));
        List<AiGateway.QuestionSpec> questions = readQuestions(session.getQuestionPlan());
        String current = session.getStatus() == InterviewStatus.IN_PROGRESS
                ? questions.get(session.getCurrentQuestionIndex()).content() : null;
        return toView(session, questions, current);
    }

    private AnswerReceipt finishSession(InterviewSession session, String userId, String reason) {
        if (!session.isConsumedCredit()) {
            session.abort("NO_ANSWER");
            entitlements.releaseReservation(userId, session.getId());
        } else {
            completeAndQueue(session, reason);
        }
        return new AnswerReceipt(session.getId(), session.getStatus().name(),
                session.getCurrentQuestionIndex(), session.getQuestionCount(), null, true);
    }

    private void completeAndQueue(InterviewSession session, String reason) {
        session.complete(reason);
        reportService.queue(session);
        outbox.enqueueReport(session.getId());
    }

    private String requestHash(CreateInterview command) {
        String canonical = command.resumeId() + "\n" + command.jdText().trim()
                + "\n" + command.questionCount();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private InterviewView toView(InterviewSession session, List<AiGateway.QuestionSpec> questions,
                                 String currentQuestion) {
        return new InterviewView(session.getId(), session.getResumeId(), session.getTargetRole(),
                session.getStatus().name(), session.getQuestionCount(), session.getCurrentQuestionIndex(),
                currentQuestion, session.getStatus() == InterviewStatus.READY ? questions : List.of(),
                session.getCreatedAt(), session.getStartedAt(), session.getCompletedAt());
    }

    private List<AiGateway.QuestionSpec> readQuestions(String value) {
        try {
            tools.jackson.databind.JsonNode root = objectMapper.readTree(value);
            java.util.ArrayList<AiGateway.QuestionSpec> result = new java.util.ArrayList<>();
            int index = 0;
            for (tools.jackson.databind.JsonNode node : root) {
                index++;
                if (node.isTextual()) {
                    String content = node.asText();
                    result.add(new AiGateway.QuestionSpec(index, content, index == 1 ? "INTRODUCTION" : "BEHAVIORAL",
                            "INTERMEDIATE", List.of("事实证据", "个人行动", "结果复盘"),
                            "LEGACY", Integer.toHexString(content.hashCode())));
                } else result.add(objectMapper.treeToValue(node, AiGateway.QuestionSpec.class));
            }
            return result;
        } catch (JacksonException exception) {
            throw new BusinessException("SERIALIZATION_FAILED", "题集数据处理失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new BusinessException("SERIALIZATION_FAILED", "数据处理失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new BusinessException("SERIALIZATION_FAILED", "数据处理失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JacksonException exception) {
            throw new BusinessException("SERIALIZATION_FAILED", "数据处理失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public record CreateInterview(
            @NotNull String resumeId,
            @NotBlank @Size(min = 20, max = 12000) String jdText,
            @Min(5) @Max(15) int questionCount) {}
    public record AnswerCommand(
            @NotBlank @Size(max = 80) String clientMessageId,
            @NotBlank @Size(min = 2, max = 8000) String answer) {}
    public record SkipCommand(@NotBlank @Size(max = 80) String clientMessageId) {}
    public record InterviewView(String id, String resumeId, String targetRole, String status,
                                int questionCount, int currentQuestionIndex, String currentQuestion,
                                List<AiGateway.QuestionSpec> questionPlan,
                                Instant createdAt, Instant startedAt, Instant completedAt) {}
    public record AnswerReceipt(String sessionId, String status, int answeredCount,
                                int questionCount, String nextQuestion, boolean completed) {}
    public record MessageView(int sequence, String role, String content, Instant createdAt) {}
}
