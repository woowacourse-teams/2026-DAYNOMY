CREATE TABLE email_analysis_jobs (
    id UUID PRIMARY KEY,
    run_date DATE NOT NULL,
    asset_id BIGINT NOT NULL REFERENCES assets(id),
    as_of TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 2),
    started_at TIMESTAMP(6) WITH TIME ZONE,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    analysis_id BIGINT REFERENCES email_common_stock_analyses(id),
    UNIQUE(run_date, asset_id)
);
CREATE INDEX idx_email_analysis_jobs_queue ON email_analysis_jobs(run_date, status);

CREATE TABLE email_analysis_calls (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES email_analysis_jobs(id),
    phase VARCHAR(30) NOT NULL,
    model VARCHAR(100) NOT NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_FLIGHT'
        CHECK (status IN ('IN_FLIGHT', 'COMPLETED', 'FAILED', 'UNKNOWN')),
    reserved_usd NUMERIC(20, 10) NOT NULL CHECK (reserved_usd > 0),
    charged_usd NUMERIC(20, 10) NOT NULL CHECK (charged_usd >= 0),
    estimated_usd NUMERIC(20, 10),
    input_tokens BIGINT,
    cached_tokens BIGINT,
    output_tokens BIGINT,
    search_calls BIGINT,
    http_status INTEGER
);
CREATE INDEX idx_email_analysis_calls_started ON email_analysis_calls(started_at);
