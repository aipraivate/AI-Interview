package com.interview.platform.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_tokens")
class AuthToken {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 36)
    private String userId;
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;
    @Column(nullable = false)
    private Instant expiresAt;
    @Column(nullable = false, length = 20)
    private String tokenType;
    private Instant revokedAt;
    @Column(nullable = false)
    private Instant createdAt;

    protected AuthToken() {}

    AuthToken(String userId, String tokenHash, String tokenType, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    String getUserId() { return userId; }
    void revoke() { this.revokedAt = Instant.now(); }
}
