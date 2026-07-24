package com.interview.platform.practice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

@Entity
@Table(name = "practice_shares")
class PracticeShare {
    private static final SecureRandom RANDOM = new SecureRandom();
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, unique = true, length = 48) private String shareToken;
    @Column(nullable = false, length = 36) private String userId;
    @Column(nullable = false, length = 36) private String sessionId;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, columnDefinition = "TEXT") private String payloadJson;
    @Column(nullable = false) private int viewCount;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;

    protected PracticeShare() {}
    PracticeShare(String userId, String sessionId, String title, String payloadJson) {
        byte[] token = new byte[18]; RANDOM.nextBytes(token);
        this.id = UUID.randomUUID().toString();
        this.shareToken = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        this.userId = userId;
        this.sessionId = sessionId;
        this.title = title;
        this.payloadJson = payloadJson;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plus(30, ChronoUnit.DAYS);
    }
    void viewed() { viewCount++; }
    String getShareToken() { return shareToken; }
    String getUserId() { return userId; }
    String getSessionId() { return sessionId; }
    String getTitle() { return title; }
    String getPayloadJson() { return payloadJson; }
    int getViewCount() { return viewCount; }
    Instant getCreatedAt() { return createdAt; }
    Instant getExpiresAt() { return expiresAt; }
}
