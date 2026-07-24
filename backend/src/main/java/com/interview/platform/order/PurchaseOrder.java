package com.interview.platform.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders")
class PurchaseOrder {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 36)
    private String userId;
    @Column(nullable = false, length = 40)
    private String productId;
    @Column(nullable = false, length = 100)
    private String productName;
    @Column(nullable = false)
    private int credits;
    @Column(nullable = false)
    private int amountCents;
    @Column(nullable = false, length = 10)
    private String currency;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false, length = 80)
    private String idempotencyKey;
    @Column(length = 100)
    private String providerTradeNo;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant paidAt;
    private Instant refundedAt;
    @Column(length = 200)
    private String refundReason;
    @Column(length = 80)
    private String refundIdempotencyKey;

    protected PurchaseOrder() {}

    PurchaseOrder(String userId, Product product, String idempotencyKey) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.productId = product.id();
        this.productName = product.name();
        this.credits = product.credits();
        this.amountCents = product.amountCents();
        this.currency = "CNY";
        this.status = "PENDING";
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    boolean markPaid(String tradeNo) {
        if ("PAID".equals(status)) return false;
        if (!"PENDING".equals(status)) throw new IllegalStateException("ORDER_NOT_PAYABLE");
        status = "PAID";
        providerTradeNo = tradeNo;
        paidAt = Instant.now();
        return true;
    }

    boolean refund(String reason, String idempotencyKey) {
        if ("REFUNDED".equals(status) && idempotencyKey.equals(refundIdempotencyKey)) return false;
        if ("REFUNDED".equals(status)) throw new IllegalStateException("REFUND_KEY_CONFLICT");
        if (!"PAID".equals(status)) throw new IllegalStateException("ORDER_NOT_REFUNDABLE");
        status = "REFUNDED";
        refundedAt = Instant.now();
        refundReason = reason;
        refundIdempotencyKey = idempotencyKey;
        return true;
    }

    String getId() { return id; }
    String getUserId() { return userId; }
    String getProductId() { return productId; }
    String getProductName() { return productName; }
    int getCredits() { return credits; }
    int getAmountCents() { return amountCents; }
    String getCurrency() { return currency; }
    String getStatus() { return status; }
    Instant getCreatedAt() { return createdAt; }
    Instant getPaidAt() { return paidAt; }
    Instant getRefundedAt() { return refundedAt; }
    String getRefundReason() { return refundReason; }

    record Product(String id, String name, int credits, int amountCents) {}
}
