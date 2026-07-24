package com.interview.platform.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "data_requests")
class DataRequest {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 36)
    private String userId;
    @Column(nullable = false, length = 20)
    private String requestType;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 500)
    private String resultMessage;
    @Column(columnDefinition = "TEXT")
    private String resultPayload;
    private Instant availableUntil;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant completedAt;

    protected DataRequest() {}

    DataRequest(String userId, String requestType) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.requestType = requestType;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    String getId() { return id; }
    String getRequestType() { return requestType; }
    String getStatus() { return status; }
    String getResultMessage() { return resultMessage; }
    Instant getCreatedAt() { return createdAt; }
    Instant getCompletedAt() { return completedAt; }
    String getUserId() { return userId; }
    String getResultPayload() { return resultPayload; }
    Instant getAvailableUntil() { return availableUntil; }

    void processing() { status = "PROCESSING"; }
    void complete(String message, String payload, Instant availableUntil) {
        status = "COMPLETED"; resultMessage = message; resultPayload = payload;
        this.availableUntil = availableUntil; completedAt = Instant.now();
    }
    void fail(String message) {
        status = "FAILED"; resultMessage = message; completedAt = Instant.now();
    }
}
