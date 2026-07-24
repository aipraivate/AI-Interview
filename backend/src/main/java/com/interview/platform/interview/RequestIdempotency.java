package com.interview.platform.interview;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "request_idempotency")
class RequestIdempotency {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 36)
    private String userId;
    @Column(nullable = false, length = 40)
    private String scope;
    @Column(nullable = false, length = 80)
    private String idempotencyKey;
    @Column(nullable = false, length = 64)
    private String requestHash;
    @Column(nullable = false, length = 36)
    private String resultId;
    @Column(nullable = false)
    private Instant createdAt;

    protected RequestIdempotency() {}

    RequestIdempotency(String userId, String scope, String idempotencyKey,
                       String requestHash, String resultId) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.scope = scope;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.resultId = resultId;
        this.createdAt = Instant.now();
    }

    String getRequestHash() { return requestHash; }
    String getResultId() { return resultId; }
}
