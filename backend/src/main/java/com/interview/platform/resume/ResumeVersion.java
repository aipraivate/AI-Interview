package com.interview.platform.resume;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resume_versions")
class ResumeVersion {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 36) private String resumeId;
    @Column(nullable = false, length = 36) private String userId;
    @Column(nullable = false) private long versionNo;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 120) private String targetRole;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(nullable = false, length = 20) private String changeType;
    @Column(nullable = false) private Instant createdAt;

    protected ResumeVersion() {}
    ResumeVersion(Resume resume, String changeType) {
        id = UUID.randomUUID().toString(); resumeId = resume.getId(); userId = resume.getUserId();
        versionNo = resume.getVersion(); title = resume.getTitle(); targetRole = resume.getTargetRole();
        content = resume.getContent(); this.changeType = changeType; createdAt = Instant.now();
    }
    long getVersionNo() { return versionNo; }
    String getTitle() { return title; }
    String getTargetRole() { return targetRole; }
    String getContent() { return content; }
    String getChangeType() { return changeType; }
    Instant getCreatedAt() { return createdAt; }
}
