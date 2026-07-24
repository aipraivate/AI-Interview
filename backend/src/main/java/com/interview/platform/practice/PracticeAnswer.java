package com.interview.platform.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "practice_answers")
class PracticeAnswer {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 36) private String sessionId;
    @Column(nullable = false, length = 36) private String userId;
    @Column(nullable = false, length = 36) private String questionId;
    @Column(nullable = false, columnDefinition = "TEXT") private String selectedAnswerJson;
    @Column(nullable = false) private boolean correct;
    @Column(nullable = false) private int durationSeconds;
    @Column(nullable = false) private Instant answeredAt;

    protected PracticeAnswer() {}
    PracticeAnswer(String sessionId, String userId, String questionId, String selectedAnswerJson,
                   boolean correct, int durationSeconds) {
        this.id = UUID.randomUUID().toString();
        this.sessionId = sessionId;
        this.userId = userId;
        this.questionId = questionId;
        this.selectedAnswerJson = selectedAnswerJson;
        this.correct = correct;
        this.durationSeconds = durationSeconds;
        this.answeredAt = Instant.now();
    }

    String getQuestionId() { return questionId; }
    String getSelectedAnswerJson() { return selectedAnswerJson; }
    boolean isCorrect() { return correct; }
    int getDurationSeconds() { return durationSeconds; }
}
