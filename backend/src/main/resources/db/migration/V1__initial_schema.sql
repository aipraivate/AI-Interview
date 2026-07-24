CREATE TABLE user_accounts (
    id VARCHAR(36) PRIMARY KEY,
    nickname VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE auth_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_auth_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_token_user FOREIGN KEY (user_id) REFERENCES user_accounts(id)
);

CREATE TABLE resumes (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(120) NOT NULL,
    target_role VARCHAR(120) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES user_accounts(id)
);
CREATE INDEX idx_resume_user ON resumes(user_id, updated_at);

CREATE TABLE entitlement_accounts (
    user_id VARCHAR(36) PRIMARY KEY,
    available_credits INT NOT NULL,
    reserved_credits INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_entitlement_user FOREIGN KEY (user_id) REFERENCES user_accounts(id)
);

CREATE TABLE entitlement_ledger (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    operation VARCHAR(20) NOT NULL,
    amount INT NOT NULL,
    reference_id VARCHAR(36),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_ledger_user FOREIGN KEY (user_id) REFERENCES user_accounts(id)
);
CREATE INDEX idx_ledger_user ON entitlement_ledger(user_id, created_at);

CREATE TABLE interview_sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    resume_id VARCHAR(36) NOT NULL,
    status VARCHAR(30) NOT NULL,
    target_role VARCHAR(120) NOT NULL,
    jd_snapshot TEXT NOT NULL,
    resume_snapshot TEXT NOT NULL,
    question_plan TEXT NOT NULL,
    question_count INT NOT NULL,
    current_question_index INT NOT NULL,
    consumed_credit BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL,
    started_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT fk_session_resume FOREIGN KEY (resume_id) REFERENCES resumes(id)
);
CREATE INDEX idx_session_user ON interview_sessions(user_id, created_at);

CREATE TABLE interview_messages (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    sequence_no INT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    client_message_id VARCHAR(80),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id),
    CONSTRAINT uk_session_sequence UNIQUE (session_id, sequence_no),
    CONSTRAINT uk_session_client_message UNIQUE (session_id, client_message_id)
);

CREATE TABLE processed_commands (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    client_message_id VARCHAR(80) NOT NULL,
    response_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_command_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id),
    CONSTRAINT uk_processed_command UNIQUE (session_id, client_message_id)
);

CREATE TABLE interview_reports (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_score INT,
    summary TEXT,
    strengths TEXT,
    improvements TEXT,
    rubric_version VARCHAR(40) NOT NULL,
    generated_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_report_session UNIQUE (session_id),
    CONSTRAINT fk_report_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id),
    CONSTRAINT fk_report_user FOREIGN KEY (user_id) REFERENCES user_accounts(id)
);
