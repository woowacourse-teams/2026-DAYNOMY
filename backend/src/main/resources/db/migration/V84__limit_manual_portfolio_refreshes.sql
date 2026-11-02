ALTER TABLE portfolio_analysis_checks
    ADD COLUMN manual_refresh_date DATE,
    ADD COLUMN manual_refresh_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_portfolio_analysis_manual_refresh_count
        CHECK (manual_refresh_count BETWEEN 0 AND 3);
