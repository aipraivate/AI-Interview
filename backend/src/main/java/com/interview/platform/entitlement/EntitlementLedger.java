package com.interview.platform.entitlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "entitlement_ledger")
class EntitlementLedger {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 36)
    private String userId;
    @Column(nullable = false, length = 20)
    private String operation;
    @Column(nullable = false)
    private int amount;
    @Column(length = 36)
    private String referenceId;
    @Column(nullable = false)
    private Instant createdAt;

    protected EntitlementLedger() {}

    EntitlementLedger(String userId, String operation, int amount, String referenceId) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.operation = operation;
        this.amount = amount;
        this.referenceId = referenceId;
        this.createdAt = Instant.now();
    }

    String getId() { return id; }
    String getOperation() { return operation; }
    int getAmount() { return amount; }
    String getReferenceId() { return referenceId; }
    Instant getCreatedAt() { return createdAt; }
}
