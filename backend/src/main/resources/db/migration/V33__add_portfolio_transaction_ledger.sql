ALTER TABLE portfolio_holding_histories
    ADD COLUMN effective_date DATE,
    ADD COLUMN transaction_quantity BIGINT,
    ADD COLUMN unit_price NUMERIC(19, 2),
    ADD COLUMN fee NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN tax NUMERIC(19, 2) NOT NULL DEFAULT 0;

UPDATE portfolio_holding_histories
SET effective_date = CAST(created_at AT TIME ZONE 'Asia/Seoul' AS DATE);

ALTER TABLE portfolio_holding_histories
    ALTER COLUMN effective_date SET NOT NULL;

CREATE INDEX idx_portfolio_holding_histories_portfolio_effective
    ON portfolio_holding_histories (portfolio_id, effective_date ASC, created_at ASC, id ASC);

ALTER TABLE portfolio_holding_histories
    ADD CONSTRAINT ck_portfolio_holding_histories_transaction_quantity_positive
        CHECK (transaction_quantity IS NULL OR transaction_quantity > 0),
    ADD CONSTRAINT ck_portfolio_holding_histories_unit_price_positive
        CHECK (unit_price IS NULL OR unit_price > 0),
    ADD CONSTRAINT ck_portfolio_holding_histories_transaction_fields
        CHECK (change_type NOT IN ('BUY', 'SELL') OR
               (transaction_quantity IS NOT NULL AND unit_price IS NOT NULL)),
    ADD CONSTRAINT ck_portfolio_holding_histories_fee_non_negative CHECK (fee >= 0),
    ADD CONSTRAINT ck_portfolio_holding_histories_tax_non_negative CHECK (tax >= 0);
