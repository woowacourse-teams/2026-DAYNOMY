CREATE TABLE revoked_auth_sessions (
    session_id UUID PRIMARY KEY,
    revoked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
