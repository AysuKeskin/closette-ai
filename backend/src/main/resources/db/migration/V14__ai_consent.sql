ALTER TABLE users ADD COLUMN ai_consent_version VARCHAR(64);
ALTER TABLE users ADD COLUMN ai_consent_at TIMESTAMPTZ;
