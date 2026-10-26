-- 점수와 근거의 스냅샷을 보존하고 종합 설명만 나중에 완성한다.
ALTER TABLE portfolio_analyses
    ADD COLUMN overall_impact TEXT,
    ADD COLUMN summary_status VARCHAR(20) NOT NULL DEFAULT 'READY',
    ADD CONSTRAINT ck_portfolio_analyses_summary_status
        CHECK (summary_status IN ('PENDING', 'READY', 'FAILED'));
