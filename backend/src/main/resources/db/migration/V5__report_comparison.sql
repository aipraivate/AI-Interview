ALTER TABLE interview_reports ADD COLUMN target_role VARCHAR(160) NULL;

CREATE INDEX idx_report_user_role_status_generated
    ON interview_reports(user_id, target_role, status, generated_at);
