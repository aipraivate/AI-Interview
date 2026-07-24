package com.interview.platform.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_question_progress")
class UserQuestionProgress {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 36) private String userId;
    @Column(nullable = false, length = 36) private String questionId;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private int correctCount;
    @Column(nullable = false) private int wrongCount;
    @Column(nullable = false) private boolean lastCorrect;
    @Column(nullable = false, columnDefinition = "TEXT") private String lastAnswerJson;
    @Column(nullable = false) private Instant answeredAt;

    protected UserQuestionProgress() {}
    UserQuestionProgress(String userId, String questionId) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.questionId = questionId;
        this.lastAnswerJson = "[]";
        this.answeredAt = Instant.now();
    }
    void record(boolean correct, String answerJson) {
        attempts++;
        if (correct) correctCount++; else wrongCount++;
        lastCorrect = correct;
        lastAnswerJson = answerJson;
        answeredAt = Instant.now();
    }
    String getQuestionId() { return questionId; }
    int getAttempts() { return attempts; }
    int getCorrectCount() { return correctCount; }
    int getWrongCount() { return wrongCount; }
    boolean isLastCorrect() { return lastCorrect; }
}
