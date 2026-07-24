package com.interview.platform.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question_favorites")
class QuestionFavorite {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 36) private String userId;
    @Column(nullable = false, length = 36) private String questionId;
    @Column(nullable = false) private Instant createdAt;
    protected QuestionFavorite() {}
    QuestionFavorite(String userId, String questionId) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.questionId = questionId;
        this.createdAt = Instant.now();
    }
    String getQuestionId() { return questionId; }
}
