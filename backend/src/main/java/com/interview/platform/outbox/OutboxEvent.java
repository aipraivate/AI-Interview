package com.interview.platform.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
class OutboxEvent {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 60)
    private String eventType;
    @Column(nullable = false, length = 36)
    private String aggregateId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private int attempts;
    @Column(nullable = false)
    private Instant availableAt;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant processedAt;
    @Column(length = 500)
    private String lastError;

    protected OutboxEvent() {}

    OutboxEvent(String eventType, String aggregateId) {
        this.id = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payloadJson = "{}";
        this.status = "PENDING";
        this.availableAt = Instant.now();
        this.createdAt = availableAt;
    }

    void complete() {
        status = "DONE";
        processedAt = Instant.now();
        lastError = null;
    }

    boolean retry(Throwable failure) {
        attempts++;
        lastError = failure.getClass().getSimpleName() + ": " + safeMessage(failure.getMessage());
        if (attempts >= 3) {
            status = "DEAD";
            processedAt = Instant.now();
            return false;
        }
        availableAt = Instant.now().plus((long) attempts * attempts, ChronoUnit.SECONDS);
        return true;
    }

    void requeue() {
        status = "PENDING";
        attempts = 0;
        availableAt = Instant.now();
        processedAt = null;
        lastError = null;
    }

    private String safeMessage(String value) {
        if (value == null) return "unknown";
        return value.length() > 450 ? value.substring(0, 450) : value;
    }

    String getEventType() { return eventType; }
    String getAggregateId() { return aggregateId; }
}
