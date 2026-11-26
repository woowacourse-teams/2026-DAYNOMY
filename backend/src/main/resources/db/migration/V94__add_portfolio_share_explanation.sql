ALTER TABLE portfolio_analysis_shares
    ADD COLUMN additional_explanation VARCHAR(2000),
    ADD COLUMN additional_explanation_updated_at TIMESTAMP(6) WITH TIME ZONE,
    ADD CONSTRAINT portfolio_analysis_shares_explanation_check
        CHECK (
            (additional_explanation IS NULL AND additional_explanation_updated_at IS NULL)
            OR (additional_explanation IS NOT NULL
                AND char_length(additional_explanation) BETWEEN 1 AND 2000
                AND additional_explanation_updated_at IS NOT NULL)
        );
