CREATE TABLE email_subscriptions (
    member_id BIGINT PRIMARY KEY REFERENCES members (id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL CHECK (status IN ('UNDECIDED', 'DECLINED', 'SUBSCRIBED', 'UNSUBSCRIBED')),
    subscribed_at TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_email_subscriptions_subscribed_at
        CHECK (status <> 'SUBSCRIBED' OR subscribed_at IS NOT NULL)
);
