-- Forgot-password: a short-lived reset code, delivered like the verification code.
ALTER TABLE users ADD COLUMN reset_code VARCHAR(10);
ALTER TABLE users ADD COLUMN reset_expires_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN reset_sent_at TIMESTAMPTZ;
