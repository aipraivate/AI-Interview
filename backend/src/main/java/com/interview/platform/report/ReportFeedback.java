package com.interview.platform.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "report_feedback")
class ReportFeedback {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 36) private String reportId;
    @Column(nullable = false, length = 36) private String userId;
    @Column(nullable = false) private boolean helpful;
    @Column(length = 40) private String reason;
    @Column(length = 500) private String comment;
    @Column(nullable = false) private Instant createdAt;
    protected ReportFeedback() {}
    ReportFeedback(String reportId, String userId, boolean helpful, String reason, String comment) {
        id = UUID.randomUUID().toString(); this.reportId = reportId; this.userId = userId;
        this.helpful = helpful; this.reason = reason; this.comment = comment; createdAt = Instant.now();
    }
    void update(boolean helpful, String reason, String comment) {
        this.helpful = helpful; this.reason = reason; this.comment = comment; createdAt = Instant.now();
    }
    boolean isHelpful() { return helpful; }
    String getReason() { return reason; }
    String getComment() { return comment; }
    Instant getCreatedAt() { return createdAt; }
}
