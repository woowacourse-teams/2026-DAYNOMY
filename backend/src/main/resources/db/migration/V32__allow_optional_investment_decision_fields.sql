ALTER TABLE portfolio_transactions
    ALTER COLUMN expected_holding_period DROP NOT NULL,
    ALTER COLUMN maximum_acceptable_loss_rate DROP NOT NULL;
