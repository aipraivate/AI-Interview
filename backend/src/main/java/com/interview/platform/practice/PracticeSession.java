package com.interview.platform.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "practice_sessions")
class PracticeSession {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 36) private String userId;
    @Column(nullable = false, length = 30) private String mode;
    @Column(length = 40) private String categoryCode;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false, columnDefinition = "TEXT") private String questionIdsJson;
    @Column(nullable = false) private int totalCount;
    @Column(nullable = false) private int currentIndex;
    @Column(nullable = false) private int answeredCount;
    @Column(nullable = false) private int correctCount;
    @Column(nullable = false) private Instant createdAt;
    private Instant completedAt;

    protected PracticeSession() {}
    PracticeSession(String userId, String mode, String categoryCode, String questionIdsJson, int totalCount) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.mode = mode;
        this.categoryCode = categoryCode;
        this.questionIdsJson = questionIdsJson;
        this.totalCount = totalCount;
        this.status = "IN_PROGRESS";
        this.createdAt = Instant.now();
    }

    void record(boolean correct) {
        answeredCount++;
        if (correct) correctCount++;
        currentIndex++;
        if (currentIndex >= totalCount) {
            status = "COMPLETED";
            completedAt = Instant.now();
        }
    }

    String getId() { return id; }
    String getUserId() { return userId; }
    String getMode() { return mode; }
    String getCategoryCode() { return categoryCode; }
    String getStatus() { return status; }
    String getQuestionIdsJson() { return questionIdsJson; }
    int getTotalCount() { return totalCount; }
    int getCurrentIndex() { return currentIndex; }
    int getAnsweredCount() { return answeredCount; }
    int getCorrectCount() { return correctCount; }
    Instant getCreatedAt() { return createdAt; }
    Instant getCompletedAt() { return completedAt; }
}
