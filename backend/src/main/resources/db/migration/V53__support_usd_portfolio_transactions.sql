ALTER TABLE portfolio_holding_histories
 ADD COLUMN exchange_rate NUMERIC(19,6) NOT NULL DEFAULT 1,
 ALTER COLUMN unit_price TYPE NUMERIC(21,4);
ALTER TABLE portfolio_holding_histories ADD CONSTRAINT ck_history_positive_exchange_rate CHECK (exchange_rate > 0);
CREATE TABLE usd_krw_rates (
 requested_date DATE PRIMARY KEY,
 base_date DATE NOT NULL,
 rate NUMERIC(19,6) NOT NULL CHECK (rate > 0),
 fetched_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT ck_usd_krw_rate_date CHECK (base_date <= requested_date)
);
