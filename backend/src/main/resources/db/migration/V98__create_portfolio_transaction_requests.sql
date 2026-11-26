-- V1/V2 trade creation share a member-scoped key. Keep successful keys until member deletion.
-- Deliberately do not reference portfolios/histories: deleting a trade must not permit its retry.
CREATE TABLE portfolio_transaction_requests (
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_version SMALLINT NOT NULL DEFAULT 1,
    response_body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, request_key)
);
