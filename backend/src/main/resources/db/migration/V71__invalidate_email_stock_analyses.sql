ALTER TABLE email_common_stock_analyses
    ADD COLUMN invalidated_at TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN invalidation_reason VARCHAR(500);

ALTER TABLE email_common_stock_analyses DROP CONSTRAINT email_common_stock_analyses_status_check;
ALTER TABLE email_common_stock_analyses ADD CONSTRAINT email_common_stock_analyses_status_check
    CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED', 'INVALIDATED'));

ALTER TABLE email_common_stock_analyses DROP CONSTRAINT ck_email_common_stock_analyses_verification;
ALTER TABLE email_common_stock_analyses ADD CONSTRAINT ck_email_common_stock_analyses_verification
    CHECK ((status = 'PENDING' AND verification IS NULL)
        OR (status IN ('VERIFIED', 'REJECTED') AND verification IS NOT NULL)
        OR status = 'INVALIDATED');

ALTER TABLE email_common_stock_analyses ADD CONSTRAINT ck_email_common_stock_analyses_invalidation
    CHECK ((status = 'INVALIDATED' AND invalidated_at IS NOT NULL AND invalidated_at >= analyzed_at
            AND invalidation_reason IS NOT NULL AND length(trim(invalidation_reason)) BETWEEN 1 AND 500)
        OR (status <> 'INVALIDATED' AND invalidated_at IS NULL AND invalidation_reason IS NULL));
