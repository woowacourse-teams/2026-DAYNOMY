ALTER TABLE portfolio_analysis_checks
    ADD COLUMN request_token UUID,
    ADD COLUMN request_expires_at TIMESTAMP(6) WITH TIME ZONE,
    ADD CONSTRAINT ck_portfolio_analysis_checks_request
        CHECK ((request_token IS NULL) = (request_expires_at IS NULL));

ALTER TABLE portfolio_analysis_evidence ALTER COLUMN asset_name DROP NOT NULL;
