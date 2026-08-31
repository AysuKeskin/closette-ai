-- Wrong-code attempts on email verification. Without a counter, a 6-digit code
-- with a 15-minute window is brute-forceable; after MAX_ATTEMPTS the code is
-- cleared and the user has to request a new one.
ALTER TABLE users ADD COLUMN verification_attempts INT NOT NULL DEFAULT 0;
