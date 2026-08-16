-- Every account gets a unique, required username (a public handle).
ALTER TABLE users ADD COLUMN username VARCHAR(30);

-- Backfill existing rows with a unique handle derived from the email local-part,
-- suffixed with a slice of the id so no two rows collide.
UPDATE users
SET username = left(regexp_replace(split_part(email, '@', 1), '[^a-zA-Z0-9._]', '', 'g'), 22)
               || '_' || left(id::text, 6)
WHERE username IS NULL;

ALTER TABLE users ALTER COLUMN username SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_username UNIQUE (username);
