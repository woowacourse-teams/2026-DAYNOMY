ALTER TABLE email_deliveries ADD COLUMN unsubscribe_url TEXT;

CREATE INDEX idx_email_deliveries_due
    ON email_deliveries(delivery_date, status, next_attempt_at, id);
