package com.interview.platform.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "practice_questions")
class PracticeQuestion {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 36) private String categoryId;
    @Column(nullable = false, length = 30) private String questionType;
    @Column(nullable = false, length = 20) private String difficulty;
    @Column(nullable = false, columnDefinition = "TEXT") private String stem;
    @Column(nullable = false, columnDefinition = "TEXT") private String optionsJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String correctAnswerJson;
    @Column(nullable = false, columnDefinition = "TEXT") private String explanation;
    @Column(nullable = false, columnDefinition = "TEXT") private String tagsJson;
    @Column(nullable = false, length = 80) private String source;
    @Column(nullable = false, length = 30) private String version;
    @Column(nullable = false) private boolean enabled;
    @Column(nullable = false) private Instant createdAt;

    protected PracticeQuestion() {}
    String getId() { return id; }
    String getCategoryId() { return categoryId; }
    String getQuestionType() { return questionType; }
    String getDifficulty() { return difficulty; }
    String getStem() { return stem; }
    String getOptionsJson() { return optionsJson; }
    String getCorrectAnswerJson() { return correctAnswerJson; }
    String getExplanation() { return explanation; }
    String getTagsJson() { return tagsJson; }
    String getSource() { return source; }
    String getVersion() { return version; }
}
