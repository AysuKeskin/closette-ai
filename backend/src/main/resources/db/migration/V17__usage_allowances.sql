-- What each account may spend on AI, and what it has spent.
--
-- Separate from the Redis rate limiter on purpose. That one is a safety brake
-- against bursts and forgets everything on restart, which is right for a brake
-- and wrong for an entitlement: an allowance someone paid for cannot evaporate
-- because a cache restarted, and it has to survive being counted from two
-- servers at once. This lives in Postgres and is counted under a row lock.

-- Which plan an account is on. Billing does not exist yet, so everyone is FREE;
-- the column exists now so the quota code has one place to ask, rather than
-- growing a second source of truth when subscriptions arrive.
ALTER TABLE users ADD COLUMN plan VARCHAR(16) NOT NULL DEFAULT 'FREE';

-- One row per account, operation and period. `used` moves under a row lock, so
-- two devices spending the last unit at the same moment cannot both win.
CREATE TABLE usage_grants (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    operation    VARCHAR(32) NOT NULL,
    amount       INTEGER NOT NULL,
    used         INTEGER NOT NULL DEFAULT 0,
    period_start TIMESTAMPTZ NOT NULL,
    period_end   TIMESTAMPTZ NOT NULL,
    -- Why this allowance exists: the recurring monthly grant, a welcome bonus, a
    -- paid period. Part of the key so a replayed webhook or a reinstall cannot
    -- mint the same month twice.
    source       VARCHAR(32) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_usage_grant UNIQUE (user_id, operation, source, period_start)
);
CREATE INDEX idx_usage_grants_lookup ON usage_grants (user_id, operation, period_end);

-- Every attempt to spend, so a lost mobile response cannot be charged twice and
-- an abandoned reservation can be found and released.
CREATE TABLE usage_operations (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    operation       VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    units           INTEGER NOT NULL,
    state           VARCHAR(16) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    settled_at      TIMESTAMPTZ,
    CONSTRAINT uq_usage_operation UNIQUE (user_id, idempotency_key)
);
CREATE INDEX idx_usage_operations_open ON usage_operations (state, created_at);
