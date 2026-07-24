package com.interview.platform.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "interview_sessions")
public class InterviewSession {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 36)
    private String userId;
    @Column(nullable = false, length = 36)
    private String resumeId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InterviewStatus status;
    @Column(nullable = false, length = 120)
    private String targetRole;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String jdSnapshot;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String resumeSnapshot;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String questionPlan;
    @Column(nullable = false)
    private int questionCount;
    @Column(nullable = false)
    private int currentQuestionIndex;
    @Column(nullable = false)
    private boolean consumedCredit;
    @Column(length = 30)
    private String endedReason;
    @Column(nullable = false)
    private int skippedCount;
    private Instant reservationExpiresAt;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected InterviewSession() {}

    InterviewSession(String id, String userId, String resumeId, String targetRole,
                     String jdSnapshot, String resumeSnapshot, String questionPlan, int questionCount) {
        this.id = id;
        this.userId = userId;
        this.resumeId = resumeId;
        this.targetRole = targetRole;
        this.jdSnapshot = jdSnapshot;
        this.resumeSnapshot = resumeSnapshot;
        this.questionPlan = questionPlan;
        this.questionCount = questionCount;
        this.currentQuestionIndex = 0;
        this.status = InterviewStatus.READY;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.reservationExpiresAt = createdAt.plus(15, ChronoUnit.MINUTES);
    }

    void start() {
        if (status != InterviewStatus.READY) return;
        status = InterviewStatus.IN_PROGRESS;
        startedAt = Instant.now();
        updatedAt = startedAt;
    }

    void markCreditConsumed() {
        consumedCredit = true;
        updatedAt = Instant.now();
    }

    void pause() {
        if (status != InterviewStatus.IN_PROGRESS) throw new IllegalStateException("INVALID_STATE");
        status = InterviewStatus.PAUSED;
        updatedAt = Instant.now();
    }

    void resume() {
        if (status != InterviewStatus.PAUSED) throw new IllegalStateException("INVALID_STATE");
        status = InterviewStatus.IN_PROGRESS;
        updatedAt = Instant.now();
    }

    void moveToNextQuestion() {
        currentQuestionIndex++;
        updatedAt = Instant.now();
    }

    void complete(String reason) {
        status = InterviewStatus.REPORTING;
        endedReason = reason;
        completedAt = Instant.now();
        updatedAt = completedAt;
    }

    void skip() {
        if (status != InterviewStatus.IN_PROGRESS) throw new IllegalStateException("INVALID_STATE");
        skippedCount++;
        updatedAt = Instant.now();
    }

    void abort(String reason) {
        if (status != InterviewStatus.READY && status != InterviewStatus.IN_PROGRESS
                && status != InterviewStatus.PAUSED) throw new IllegalStateException("INVALID_STATE");
        status = InterviewStatus.ABORTED;
        endedReason = reason;
        completedAt = Instant.now();
        updatedAt = completedAt;
    }

    void expire() {
        if (status != InterviewStatus.READY) return;
        status = InterviewStatus.EXPIRED;
        endedReason = "RESERVATION_EXPIRED";
        completedAt = Instant.now();
        updatedAt = completedAt;
    }

    public void markReported() {
        status = InterviewStatus.REPORTED;
        updatedAt = Instant.now();
    }

    public void markReportFailed() {
        status = InterviewStatus.REPORT_FAILED;
        updatedAt = Instant.now();
    }

    public void retryReport() {
        if (status != InterviewStatus.REPORT_FAILED) throw new IllegalStateException("INVALID_STATE");
        status = InterviewStatus.REPORTING;
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getResumeId() { return resumeId; }
    public InterviewStatus getStatus() { return status; }
    public String getTargetRole() { return targetRole; }
    public String getQuestionPlan() { return questionPlan; }
    public int getQuestionCount() { return questionCount; }
    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public boolean isConsumedCredit() { return consumedCredit; }
    public String getEndedReason() { return endedReason; }
    public int getSkippedCount() { return skippedCount; }
    public Instant getReservationExpiresAt() { return reservationExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
