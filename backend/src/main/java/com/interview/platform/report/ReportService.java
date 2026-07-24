package com.interview.platform.report;

import com.interview.platform.ai.AiGateway;
import com.interview.platform.common.BusinessException;
import com.interview.platform.interview.InterviewMessage;
import com.interview.platform.interview.InterviewMessageRepository;
import com.interview.platform.interview.InterviewSession;
import com.interview.platform.interview.InterviewSessionRepository;
import com.interview.platform.entitlement.EntitlementService;
import com.interview.platform.outbox.OutboxService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

@Service
public class ReportService {
    private final InterviewReportRepository reports;
    private final InterviewSessionRepository sessions;
    private final InterviewMessageRepository messages;
    private final AiGateway aiGateway;
    private final ObjectMapper objectMapper;
    private final EntitlementService entitlements;
    private final OutboxService outbox;
    private final ReportFeedbackRepository feedback;
    private final long reportDelayMs;

    public ReportService(InterviewReportRepository reports, InterviewSessionRepository sessions,
                         InterviewMessageRepository messages, AiGateway aiGateway, ObjectMapper objectMapper,
                         EntitlementService entitlements, OutboxService outbox,
                         ReportFeedbackRepository feedback,
                         @Value("${app.ai.report-delay-ms}") long reportDelayMs) {
        this.reports = reports;
        this.sessions = sessions;
        this.messages = messages;
        this.aiGateway = aiGateway;
        this.objectMapper = objectMapper;
        this.entitlements = entitlements;
        this.outbox = outbox;
        this.feedback = feedback;
        this.reportDelayMs = reportDelayMs;
    }

    public void queue(InterviewSession session) {
        reports.findBySessionId(session.getId())
                .orElseGet(() -> reports.save(new InterviewReport(
                        session.getId(), session.getUserId(), session.getTargetRole())));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void generate(String sessionId) {
        InterviewReport report = reports.findBySessionId(sessionId).orElseThrow();
        if (report.getStatus() == ReportStatus.READY) return;
        InterviewSession session = sessions.findById(sessionId).orElseThrow();
        try {
            if (reportDelayMs > 0) Thread.sleep(reportDelayMs);
            List<AiGateway.QuestionAnswer> answers = pairTranscript(
                    messages.findBySessionIdOrderBySequenceNo(sessionId));
            AiGateway.Evaluation evaluation = aiGateway.evaluate(session.getTargetRole(), answers);
            report.complete(evaluation, writeJson(evaluation));
            session.markReported();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Report generation interrupted", exception);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finalFailure(String sessionId) {
        reports.findBySessionId(sessionId).ifPresent(InterviewReport::fail);
        sessions.findById(sessionId).ifPresent(session -> {
            session.markReportFailed();
            if (session.isConsumedCredit()) {
                entitlements.compensate(session.getUserId(), session.getId(), "REPORT_FINAL_FAILURE");
            }
        });
    }

    @Transactional
    public ReportView retry(String sessionId, String userId) {
        InterviewReport report = reports.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> BusinessException.notFound("复盘报告不存在"));
        if (report.getStatus() != ReportStatus.FAILED) {
            throw BusinessException.conflict("REPORT_NOT_RETRYABLE", "当前报告无需重试");
        }
        InterviewSession session = sessions.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> BusinessException.notFound("面试不存在"));
        report.retry();
        session.retryReport();
        outbox.requeueReport(sessionId);
        return get(sessionId, userId);
    }

    @Transactional
    public FeedbackView feedback(String sessionId, String userId, FeedbackCommand command) {
        InterviewReport report = reports.findBySessionIdAndUserId(sessionId, userId)
                .filter(value -> value.getStatus() == ReportStatus.READY)
                .orElseThrow(() -> BusinessException.notFound("可反馈的报告不存在"));
        String reason = trim(command.reason(), 40);
        String comment = trim(command.comment(), 500);
        ReportFeedback value = feedback.findByReportIdAndUserId(report.getId(), userId)
                .orElseGet(() -> new ReportFeedback(report.getId(), userId,
                        command.helpful(), reason, comment));
        value.update(command.helpful(), reason, comment);
        feedback.save(value);
        return new FeedbackView(value.isHelpful(), value.getReason(), value.getComment(), value.getCreatedAt());
    }

    private String trim(String value, int max) {
        if (value == null) return null;
        String result = value.trim();
        return result.length() <= max ? result : result.substring(0, max);
    }

    @Transactional(readOnly = true)
    public ReportView get(String sessionId, String userId) {
        InterviewReport report = reports.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> BusinessException.notFound("复盘报告尚未生成"));
        AiGateway.Evaluation details = readDetails(report.getDetailsJson());
        Integer previousScore = reports.findByUserIdAndTargetRoleAndStatusOrderByGeneratedAtDesc(
                        userId, report.getTargetRole(), ReportStatus.READY).stream()
                .filter(value -> !value.getId().equals(report.getId()))
                .map(InterviewReport::getTotalScore).findFirst().orElse(null);
        Integer scoreDelta = previousScore == null || report.getTotalScore() == null
                ? null : report.getTotalScore() - previousScore;
        return new ReportView(report.getId(), report.getSessionId(), report.getStatus().name(),
                report.getTotalScore(), report.getSummary(), report.getStrengths(),
                report.getImprovements(), report.getRubricVersion(), report.getScoreSchemaVersion(),
                report.getConfidence(), report.getModelVersion(), report.getPromptVersion(),
                details == null ? List.of() : details.dimensions(),
                details == null ? List.of() : details.questionFeedback(),
                details == null ? List.of() : details.actionItems(), previousScore, scoreDelta,
                report.getGeneratedAt());
    }

    private List<AiGateway.QuestionAnswer> pairTranscript(List<InterviewMessage> transcript) {
        java.util.ArrayList<AiGateway.QuestionAnswer> result = new java.util.ArrayList<>();
        String currentQuestion = "";
        for (InterviewMessage message : transcript) {
            if ("INTERVIEWER".equals(message.getRole())) currentQuestion = message.getContent();
            if ("CANDIDATE".equals(message.getRole())) {
                result.add(new AiGateway.QuestionAnswer(result.size() + 1,
                        currentQuestion, message.getContent()));
            }
        }
        return result;
    }

    private String writeJson(AiGateway.Evaluation evaluation) {
        try {
            return objectMapper.writeValueAsString(evaluation);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to persist report details", exception);
        }
    }

    private AiGateway.Evaluation readDetails(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, AiGateway.Evaluation.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to read report details", exception);
        }
    }

    public record ReportView(String id, String sessionId, String status, Integer totalScore,
                             String summary, String strengths, String improvements,
                             String rubricVersion, String scoreSchemaVersion, Double confidence,
                             String modelVersion, String promptVersion,
                             List<AiGateway.DimensionScore> dimensions,
                             List<AiGateway.QuestionFeedback> questionFeedback,
                             List<String> actionItems, Integer previousScore, Integer scoreDelta,
                             Instant generatedAt) {}
    public record FeedbackCommand(boolean helpful, String reason, String comment) {}
    public record FeedbackView(boolean helpful, String reason, String comment, Instant createdAt) {}
}
