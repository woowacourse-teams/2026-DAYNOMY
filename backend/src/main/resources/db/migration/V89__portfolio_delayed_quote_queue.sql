ALTER TABLE portfolio_delayed_quotes ADD COLUMN queued_at timestamptz;

UPDATE portfolio_delayed_quotes
SET queued_at = GREATEST(requested_at, attempted_at);

ALTER TABLE portfolio_delayed_quotes ALTER COLUMN queued_at SET NOT NULL;
