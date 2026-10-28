CREATE TABLE email_deliveries (
    id UUID PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id),
    delivery_date DATE NOT NULL,
    as_of TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    subject TEXT NOT NULL,
    body TEXT NOT NULL,
    analysis_ids JSONB NOT NULL CHECK (jsonb_typeof(analysis_ids) = 'array' AND jsonb_array_length(analysis_ids) BETWEEN 1 AND 3),
    status VARCHAR(20) NOT NULL CHECK (status IN ('READY', 'SENDING', 'ACCEPTED', 'RETRY_PENDING', 'UNKNOWN', 'FAILED', 'CANCELLED', 'EXPIRED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
    next_attempt_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    last_attempt_at TIMESTAMP(6) WITH TIME ZONE,
    accepted_at TIMESTAMP(6) WITH TIME ZONE,
    provider_message_id VARCHAR(255),
    UNIQUE(member_id, delivery_date),
    CHECK ((status = 'ACCEPTED' AND accepted_at IS NOT NULL AND provider_message_id IS NOT NULL)
        OR (status <> 'ACCEPTED' AND accepted_at IS NULL AND provider_message_id IS NULL))
);
