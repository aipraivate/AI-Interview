ALTER TABLE user_accounts ADD COLUMN email VARCHAR(190);
ALTER TABLE user_accounts ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE user_accounts ADD COLUMN user_type VARCHAR(20) NOT NULL DEFAULT 'INDIVIDUAL';
ALTER TABLE user_accounts ADD COLUMN member_level VARCHAR(20) NOT NULL DEFAULT 'FREE';
ALTER TABLE user_accounts ADD COLUMN timezone VARCHAR(40) NOT NULL DEFAULT 'Asia/Shanghai';
ALTER TABLE user_accounts ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);
ALTER TABLE user_accounts ADD COLUMN deleted_at TIMESTAMP(6);
CREATE UNIQUE INDEX uk_user_email ON user_accounts(email);

ALTER TABLE auth_tokens ADD COLUMN token_type VARCHAR(20) NOT NULL DEFAULT 'ACCESS';
ALTER TABLE auth_tokens ADD COLUMN revoked_at TIMESTAMP(6);
CREATE INDEX idx_auth_user_type ON auth_tokens(user_id, token_type, expires_at);

CREATE TABLE policy_consents (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    policy_type VARCHAR(30) NOT NULL,
    policy_version VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    consented_at TIMESTAMP(6) NOT NULL,
    withdrawn_at TIMESTAMP(6),
    CONSTRAINT fk_consent_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT uk_consent_version UNIQUE (user_id, policy_type, policy_version)
);

CREATE TABLE request_idempotency (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    scope VARCHAR(40) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    result_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT uk_idempotency_scope UNIQUE (user_id, scope, idempotency_key)
);

CREATE TABLE outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    event_type VARCHAR(60) NOT NULL,
    aggregate_id VARCHAR(36) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    available_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    processed_at TIMESTAMP(6),
    last_error VARCHAR(500),
    CONSTRAINT uk_outbox_event UNIQUE (event_type, aggregate_id)
);
CREATE INDEX idx_outbox_pending ON outbox_events(status, available_at);

CREATE TABLE purchase_orders (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(40) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    credits INT NOT NULL,
    amount_cents INT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(80) NOT NULL,
    provider_trade_no VARCHAR(100),
    created_at TIMESTAMP(6) NOT NULL,
    paid_at TIMESTAMP(6),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT uk_order_idempotency UNIQUE (user_id, idempotency_key),
    CONSTRAINT uk_order_provider_trade UNIQUE (provider_trade_no)
);
CREATE INDEX idx_order_user ON purchase_orders(user_id, created_at);

CREATE TABLE data_requests (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    request_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_message VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    completed_at TIMESTAMP(6),
    CONSTRAINT fk_data_request_user FOREIGN KEY (user_id) REFERENCES user_accounts(id)
);
CREATE INDEX idx_data_request_user ON data_requests(user_id, created_at);

CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    actor_user_id VARCHAR(36),
    action VARCHAR(60) NOT NULL,
    resource_type VARCHAR(40) NOT NULL,
    resource_id VARCHAR(36),
    trace_id VARCHAR(64),
    metadata_json TEXT,
    created_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_audit_resource ON audit_logs(resource_type, resource_id, created_at);
