package com.interview.platform.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "policy_consents")
class PolicyConsent {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 36)
    private String userId;
    @Column(nullable = false, length = 30)
    private String policyType;
    @Column(nullable = false, length = 30)
    private String policyVersion;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private Instant consentedAt;
    private Instant withdrawnAt;

    protected PolicyConsent() {}

    PolicyConsent(String userId, String policyType, String policyVersion) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.policyType = policyType;
        this.policyVersion = policyVersion;
        this.status = "ACCEPTED";
        this.consentedAt = Instant.now();
    }
}
