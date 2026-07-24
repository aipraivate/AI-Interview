ALTER TABLE interview_reports ADD COLUMN details_json TEXT;
ALTER TABLE interview_reports ADD COLUMN score_schema_version VARCHAR(40) NOT NULL DEFAULT 'report-schema-v1';
ALTER TABLE interview_reports ADD COLUMN confidence DECIMAL(5,4);
ALTER TABLE interview_reports ADD COLUMN model_version VARCHAR(100);
ALTER TABLE interview_reports ADD COLUMN prompt_version VARCHAR(60);
