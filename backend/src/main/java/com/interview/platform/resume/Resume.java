package com.interview.platform.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resumes")
class Resume {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 36)
    private String userId;
    @Column(nullable = false, length = 120)
    private String title;
    @Column(nullable = false, length = 120)
    private String targetRole;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false, length = 20)
    private String sourceType;
    @Column(length = 255)
    private String originalFilename;
    @Column(precision = 5, scale = 4)
    private java.math.BigDecimal parseConfidence;
    private Instant confirmedAt;
    @Column(nullable = false)
    private boolean isDefault;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected Resume() {}

    Resume(String userId, String title, String targetRole, String content) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.title = title;
        this.targetRole = targetRole;
        this.content = content;
        this.status = "CONFIRMED";
        this.sourceType = "MANUAL";
        this.parseConfidence = java.math.BigDecimal.ONE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
        this.confirmedAt = createdAt;
    }

    static Resume parsed(String userId, String filename, String title, String targetRole,
                         String content, double confidence) {
        Resume resume = new Resume(userId, title, targetRole, content);
        resume.status = "PARSED";
        resume.sourceType = "UPLOAD";
        resume.originalFilename = filename;
        resume.parseConfidence = java.math.BigDecimal.valueOf(confidence);
        resume.confirmedAt = null;
        return resume;
    }

    void confirm(String title, String targetRole, String content) {
        this.title = title;
        this.targetRole = targetRole;
        this.content = content;
        this.status = "CONFIRMED";
        this.confirmedAt = Instant.now();
        this.updatedAt = confirmedAt;
    }

    void makeDefault() { this.isDefault = true; this.updatedAt = Instant.now(); }
    void clearDefault() { this.isDefault = false; this.updatedAt = Instant.now(); }

    String getId() { return id; }
    String getUserId() { return userId; }
    String getTitle() { return title; }
    String getTargetRole() { return targetRole; }
    String getContent() { return content; }
    String getStatus() { return status; }
    String getSourceType() { return sourceType; }
    String getOriginalFilename() { return originalFilename; }
    Double getParseConfidence() { return parseConfidence == null ? null : parseConfidence.doubleValue(); }
    Instant getConfirmedAt() { return confirmedAt; }
    boolean isDefault() { return isDefault; }
    Instant getCreatedAt() { return createdAt; }
    Instant getUpdatedAt() { return updatedAt; }
    long getVersion() { return version; }
}
