ALTER TABLE investment_plans
    ADD COLUMN existing_deposit_savings BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN investment_assets BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN other_assets BIGINT NOT NULL DEFAULT 0;
