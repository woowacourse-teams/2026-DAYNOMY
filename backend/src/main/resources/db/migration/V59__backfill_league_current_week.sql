ALTER TABLE league_participations ADD COLUMN performance_started_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE shared_holding_histories ADD COLUMN effective_at TIMESTAMP WITH TIME ZONE;
CREATE INDEX idx_shared_holding_histories_effective
    ON shared_holding_histories (portfolio_id, COALESCE(effective_at, created_at), id);
