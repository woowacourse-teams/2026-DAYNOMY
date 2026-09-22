ALTER TABLE assets
    ADD COLUMN market VARCHAR(20),
    ADD COLUMN isin_code VARCHAR(12),
    ADD COLUMN listed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN stock_base_date DATE;

CREATE UNIQUE INDEX uk_assets_isin_code
    ON assets (isin_code)
    WHERE isin_code IS NOT NULL;

CREATE INDEX idx_assets_stock_listing
    ON assets (category, listed, market);
