ALTER TABLE portfolio_analysis_checks
    ADD COLUMN last_outcome VARCHAR(30),
    ADD CONSTRAINT ck_portfolio_analysis_check_outcome
        CHECK (last_outcome IN ('UPDATED', 'UNCHANGED', 'UNCONFIRMED'));
