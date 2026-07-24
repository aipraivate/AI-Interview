package com.interview.platform.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
class AuditLog {
    @Id
    @Column(length = 36)
    private String id;
    @Column(length = 36)
    private String actorUserId;
    @Column(nullable = false, length = 60)
    private String action;
    @Column(nullable = false, length = 40)
    private String resourceType;
    @Column(length = 36)
    private String resourceId;
    @Column(length = 64)
    private String traceId;
    @Column(columnDefinition = "TEXT")
    private String metadataJson;
    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLog() {}

    AuditLog(String actorUserId, String action, String resourceType,
             String resourceId, String traceId, String metadataJson) {
        this.id = UUID.randomUUID().toString();
        this.actorUserId = actorUserId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.traceId = traceId;
        this.metadataJson = metadataJson;
        this.createdAt = Instant.now();
    }
}
