-- Soft email verification: accounts work immediately but stay "unverified"
-- until the emailed code is entered. Some actions are gated on this flag.
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE users ADD COLUMN verification_code VARCHAR(10);
ALTER TABLE users ADD COLUMN verification_expires_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN verification_sent_at TIMESTAMPTZ;
