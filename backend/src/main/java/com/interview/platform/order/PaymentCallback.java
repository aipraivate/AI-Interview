package com.interview.platform.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_callbacks")
class PaymentCallback {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 30)
    private String provider;
    @Column(nullable = false, length = 100)
    private String eventId;
    @Column(nullable = false, length = 36)
    private String orderId;
    @Column(nullable = false, length = 64)
    private String payloadHash;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant processedAt;

    protected PaymentCallback() {}

    PaymentCallback(String provider, String eventId, String orderId, String payloadHash) {
        this.id = UUID.randomUUID().toString();
        this.provider = provider;
        this.eventId = eventId;
        this.orderId = orderId;
        this.payloadHash = payloadHash;
        this.status = "RECEIVED";
        this.createdAt = Instant.now();
    }

    void complete() { status = "PROCESSED"; processedAt = Instant.now(); }
    String getOrderId() { return orderId; }
}
