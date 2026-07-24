package com.interview.platform.report;

import com.interview.platform.ai.AiGateway;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "interview_reports")
class InterviewReport {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, unique = true, length = 36)
    private String sessionId;
    @Column(nullable = false, length = 36)
    private String userId;
    @Column(length = 160)
    private String targetRole;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;
    private Integer totalScore;
    @Column(columnDefinition = "TEXT")
    private String summary;
    @Column(columnDefinition = "TEXT")
    private String strengths;
    @Column(columnDefinition = "TEXT")
    private String improvements;
    @Column(columnDefinition = "TEXT")
    private String detailsJson;
    @Column(nullable = false, length = 40)
    private String rubricVersion;
    @Column(nullable = false, length = 40)
    private String scoreSchemaVersion;
    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;
    @Column(length = 100)
    private String modelVersion;
    @Column(length = 60)
    private String promptVersion;
    private Instant generatedAt;
    @Column(nullable = false)
    private Instant createdAt;

    protected InterviewReport() {}

    InterviewReport(String sessionId, String userId, String targetRole) {
        this.id = UUID.randomUUID().toString();
        this.sessionId = sessionId;
        this.userId = userId;
        this.targetRole = targetRole;
        this.status = ReportStatus.PENDING;
        this.rubricVersion = "mvp-rubric-v1";
        this.scoreSchemaVersion = "report-schema-v2";
        this.createdAt = Instant.now();
    }

    void complete(AiGateway.Evaluation evaluation, String detailsJson) {
        this.totalScore = evaluation.totalScore();
        this.summary = evaluation.summary();
        this.strengths = evaluation.strengths();
        this.improvements = evaluation.improvements();
        this.detailsJson = detailsJson;
        this.scoreSchemaVersion = evaluation.schemaVersion();
        this.confidence = BigDecimal.valueOf(evaluation.confidence());
        this.modelVersion = evaluation.modelVersion();
        this.promptVersion = evaluation.promptVersion();
        this.status = ReportStatus.READY;
        this.generatedAt = Instant.now();
    }

    void fail() { this.status = ReportStatus.FAILED; }
    void retry() { this.status = ReportStatus.PENDING; }

    String getId() { return id; }
    String getSessionId() { return sessionId; }
    String getTargetRole() { return targetRole; }
    ReportStatus getStatus() { return status; }
    Integer getTotalScore() { return totalScore; }
    String getSummary() { return summary; }
    String getStrengths() { return strengths; }
    String getImprovements() { return improvements; }
    String getDetailsJson() { return detailsJson; }
    String getRubricVersion() { return rubricVersion; }
    String getScoreSchemaVersion() { return scoreSchemaVersion; }
    Double getConfidence() { return confidence == null ? null : confidence.doubleValue(); }
    String getModelVersion() { return modelVersion; }
    String getPromptVersion() { return promptVersion; }
    Instant getGeneratedAt() { return generatedAt; }
}
