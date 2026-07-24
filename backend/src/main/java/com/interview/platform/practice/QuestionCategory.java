package com.interview.platform.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_categories")
class QuestionCategory {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, unique = true, length = 40) private String code;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false, length = 240) private String description;
    @Column(nullable = false, length = 20) private String icon;
    @Column(nullable = false, length = 20) private String color;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean enabled;

    protected QuestionCategory() {}
    String getId() { return id; }
    String getCode() { return code; }
    String getName() { return name; }
    String getDescription() { return description; }
    String getIcon() { return icon; }
    String getColor() { return color; }
}
