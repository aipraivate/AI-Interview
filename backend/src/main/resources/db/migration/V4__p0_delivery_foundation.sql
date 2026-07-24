ALTER TABLE resumes ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED';
ALTER TABLE resumes ADD COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE resumes ADD COLUMN original_filename VARCHAR(255);
ALTER TABLE resumes ADD COLUMN parse_confidence DECIMAL(5,4);
ALTER TABLE resumes ADD COLUMN confirmed_at TIMESTAMP(6);
ALTER TABLE resumes ADD COLUMN is_default BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE resume_versions (
    id VARCHAR(36) PRIMARY KEY,
    resume_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    version_no BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    target_role VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_resume_version_resume FOREIGN KEY (resume_id) REFERENCES resumes(id),
    CONSTRAINT fk_resume_version_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT uk_resume_version UNIQUE (resume_id, version_no)
);

ALTER TABLE interview_sessions ADD COLUMN ended_reason VARCHAR(30);
ALTER TABLE interview_sessions ADD COLUMN skipped_count INT NOT NULL DEFAULT 0;
ALTER TABLE interview_sessions ADD COLUMN reservation_expires_at TIMESTAMP(6);

CREATE TABLE report_feedback (
    id VARCHAR(36) PRIMARY KEY,
    report_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    helpful BOOLEAN NOT NULL,
    reason VARCHAR(40),
    comment VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_feedback_report FOREIGN KEY (report_id) REFERENCES interview_reports(id),
    CONSTRAINT fk_feedback_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT uk_feedback_report_user UNIQUE (report_id, user_id)
);

CREATE TABLE analytics_events (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36),
    event_name VARCHAR(60) NOT NULL,
    properties_json VARCHAR(2000) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_analytics_name_time ON analytics_events(event_name, occurred_at);

ALTER TABLE data_requests ADD COLUMN result_payload TEXT;
ALTER TABLE data_requests ADD COLUMN available_until TIMESTAMP(6);

ALTER TABLE purchase_orders ADD COLUMN refunded_at TIMESTAMP(6);
ALTER TABLE purchase_orders ADD COLUMN refund_reason VARCHAR(200);
