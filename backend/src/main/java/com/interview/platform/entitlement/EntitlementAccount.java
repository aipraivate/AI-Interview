package com.interview.platform.entitlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "entitlement_accounts")
class EntitlementAccount {
    @Id
    @Column(length = 36)
    private String userId;
    @Column(nullable = false)
    private int availableCredits;
    @Column(nullable = false)
    private int reservedCredits;
    @Version
    private long version;
    @Column(nullable = false)
    private Instant updatedAt;

    protected EntitlementAccount() {}

    EntitlementAccount(String userId, int initialCredits) {
        this.userId = userId;
        this.availableCredits = initialCredits;
        this.reservedCredits = 0;
        this.updatedAt = Instant.now();
    }

    void reserve() {
        if (availableCredits < 1) throw new IllegalStateException("NO_CREDITS");
        availableCredits--;
        reservedCredits++;
        updatedAt = Instant.now();
    }

    void consumeReservation() {
        if (reservedCredits < 1) throw new IllegalStateException("NO_RESERVATION");
        reservedCredits--;
        updatedAt = Instant.now();
    }

    void releaseReservation() {
        if (reservedCredits < 1) return;
        reservedCredits--;
        availableCredits++;
        updatedAt = Instant.now();
    }

    void grant(int credits) {
        if (credits <= 0) throw new IllegalArgumentException("credits must be positive");
        availableCredits += credits;
        updatedAt = Instant.now();
    }

    void deduct(int credits) {
        if (credits <= 0 || availableCredits < credits) throw new IllegalStateException("CREDITS_ALREADY_USED");
        availableCredits -= credits;
        updatedAt = Instant.now();
    }

    int getAvailableCredits() { return availableCredits; }
    int getReservedCredits() { return reservedCredits; }
}
