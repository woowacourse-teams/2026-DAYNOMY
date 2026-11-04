CREATE TABLE email_portfolio_analysis_runs (
    id UUID PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    run_date DATE NOT NULL,
    signals JSONB NOT NULL CHECK (jsonb_typeof(signals) = 'array' AND jsonb_array_length(signals) > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    result_analysis_id BIGINT REFERENCES portfolio_analyses(id) ON DELETE SET NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    UNIQUE(member_id, run_date)
);
CREATE INDEX idx_email_portfolio_analysis_runs_date ON email_portfolio_analysis_runs(run_date);
