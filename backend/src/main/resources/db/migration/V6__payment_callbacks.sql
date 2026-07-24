ALTER TABLE purchase_orders ADD COLUMN refund_idempotency_key VARCHAR(80) NULL;

CREATE TABLE payment_callbacks (
    id VARCHAR(36) PRIMARY KEY,
    provider VARCHAR(30) NOT NULL,
    event_id VARCHAR(100) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6) NULL,
    CONSTRAINT uk_payment_callback_event UNIQUE (provider, event_id)
);

CREATE INDEX idx_payment_callback_order ON payment_callbacks(order_id);
