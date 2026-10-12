\set ON_ERROR_STOP on
-- PostgreSQL 16, synthetic 20,000 rows. SQL timings exclude HTTP and network costs.
-- Created objects and data are rolled back at the end.
-- Measured on 2026-10-05, PostgreSQL 16.15 aarch64, 20 samples after one warm-up:
-- case                       p50 before/after (ms)   p95 before/after (ms)
-- short/all/first             157.452 / 0.834         158.744 / 0.851
-- short/DRAFT/first            41.357 / 0.839          41.757 / 0.850
-- short/ETF/first              54.354 / 0.842          55.704 / 0.862
-- short/DRAFT+ETF/first        14.363 / 0.949          14.717 / 1.015
-- long/all/first               15.491 / 0.865          16.084 / 0.917
-- short/all/count             158.192 / 157.238       165.264 / 159.810
-- short/all/offset1000         160.748 / 159.884       165.154 / 163.892
-- multi/all/latest            175.411 / 0.929         195.318 / 0.951
-- multi/all/relevance         179.045 / 177.667       182.578 / 185.592
-- First-page plan: Seq Scan + top-N sort -> Index Scan; buffer hits 646 -> 7.
-- Index size: 632 kB. Phrase matches: 1,818; all-terms matches: 2,775.
-- COUNT, deep pages, and relevance still scan many rows. Timings vary by workload.
BEGIN;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
SELECT version();
CREATE TEMP TABLE admin_news_search_benchmark (
  id BIGINT PRIMARY KEY, title TEXT NOT NULL, content TEXT NOT NULL,
  category VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);
INSERT INTO admin_news_search_benchmark
SELECT i,
  CASE WHEN i % 19 = 0 THEN '기준금리 추가 인하 가능성'
       WHEN i % 17 = 0 THEN 'ETF 투자 수익률 분석'
       ELSE '산업 전망과 시장 변동' END,
  repeat('경제 동향과 정책 변화가 기업에 미치는 영향을 분석합니다. ', 30) ||
  CASE WHEN i % 11 = 0 THEN '기준금리 인하와 환율 변동'
       ELSE '실적 발표와 원자재 가격' END,
  CASE WHEN i % 3 = 0 THEN 'ETF' ELSE 'STOCK' END,
  (ARRAY['DRAFT', 'PUBLISHED', 'REJECTED', 'DELETED'])[i % 4 + 1],
  TIMESTAMPTZ '2026-01-01 00:00:00+00' + i * INTERVAL '1 minute'
FROM generate_series(1, 20000) AS i;
CREATE INDEX bench_admin_title_trgm ON admin_news_search_benchmark USING gin (lower(title) gin_trgm_ops);
CREATE INDEX bench_admin_content_trgm ON admin_news_search_benchmark USING gin (lower(content) gin_trgm_ops);
ANALYZE admin_news_search_benchmark;

SELECT count(*) FILTER (WHERE lower(title) LIKE '%금리 인하%' OR lower(content) LIKE '%금리 인하%') AS phrase_matches,
       count(*) FILTER (WHERE (lower(title) LIKE '%금리%' OR lower(content) LIKE '%금리%')
                          AND (lower(title) LIKE '%인하%' OR lower(content) LIKE '%인하%')) AS all_terms_matches
FROM admin_news_search_benchmark;

CREATE FUNCTION pg_temp.measure_admin_search(label TEXT, statement TEXT)
RETURNS TABLE (case_name TEXT, p50_ms NUMERIC, p95_ms NUMERIC) LANGUAGE plpgsql AS $$
DECLARE durations DOUBLE PRECISION[] := '{}'; started TIMESTAMPTZ; iteration INTEGER;
BEGIN
  EXECUTE statement;
  FOR iteration IN 1..20 LOOP
    started := clock_timestamp();
    EXECUTE statement;
    durations := array_append(durations, extract(epoch FROM clock_timestamp() - started) * 1000);
  END LOOP;
  RETURN QUERY SELECT label,
    round((percentile_cont(0.50) WITHIN GROUP (ORDER BY elapsed))::numeric, 3),
    round((percentile_cont(0.95) WITHIN GROUP (ORDER BY elapsed))::numeric, 3)
  FROM unnest(durations) AS elapsed;
END $$;

CREATE TEMP TABLE admin_search_cases (label TEXT, statement TEXT);
INSERT INTO admin_search_cases VALUES
('short/all/first', $$SELECT * FROM admin_news_search_benchmark WHERE lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!' ORDER BY created_at DESC, id DESC LIMIT 15$$),
('short/DRAFT/first', $$SELECT * FROM admin_news_search_benchmark WHERE status = 'DRAFT' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY created_at DESC, id DESC LIMIT 15$$),
('short/ETF/first', $$SELECT * FROM admin_news_search_benchmark WHERE category = 'ETF' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY created_at DESC, id DESC LIMIT 15$$),
('short/DRAFT+ETF/first', $$SELECT * FROM admin_news_search_benchmark WHERE status = 'DRAFT' AND category = 'ETF' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY created_at DESC, id DESC LIMIT 15$$),
('long/all/first', $$SELECT * FROM admin_news_search_benchmark WHERE lower(title) LIKE '%기준금리%' ESCAPE '!' OR lower(content) LIKE '%기준금리%' ESCAPE '!' ORDER BY created_at DESC, id DESC LIMIT 15$$),
('short/all/count', $$SELECT count(*) FROM admin_news_search_benchmark WHERE lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!'$$),
('short/all/offset1000', $$SELECT * FROM admin_news_search_benchmark WHERE lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!' ORDER BY created_at DESC, id DESC LIMIT 15 OFFSET 1000$$),
('multi/all/latest', $$SELECT * FROM admin_news_search_benchmark WHERE (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') AND (lower(title) LIKE '%인하%' ESCAPE '!' OR lower(content) LIKE '%인하%' ESCAPE '!') ORDER BY created_at DESC, id DESC LIMIT 15$$),
('multi/all/relevance', $$SELECT * FROM admin_news_search_benchmark WHERE (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') AND (lower(title) LIKE '%인하%' ESCAPE '!' OR lower(content) LIKE '%인하%' ESCAPE '!') ORDER BY ((CASE WHEN lower(title) LIKE '%금리%' ESCAPE '!' THEN 1 ELSE 0 END) + (CASE WHEN lower(title) LIKE '%인하%' ESCAPE '!' THEN 1 ELSE 0 END)) DESC, created_at DESC, id DESC LIMIT 15$$);

\echo Before created_at ordering index
SELECT timing.* FROM admin_search_cases cases
CROSS JOIN LATERAL pg_temp.measure_admin_search(cases.label, cases.statement) timing;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM admin_news_search_benchmark
WHERE lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!'
ORDER BY created_at DESC, id DESC LIMIT 15;

CREATE INDEX bench_admin_created_id ON admin_news_search_benchmark (created_at DESC, id DESC);
ANALYZE admin_news_search_benchmark;

\echo After created_at ordering index
SELECT timing.* FROM admin_search_cases cases
CROSS JOIN LATERAL pg_temp.measure_admin_search(cases.label, cases.statement) timing;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM admin_news_search_benchmark
WHERE lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!'
ORDER BY created_at DESC, id DESC LIMIT 15;
SELECT pg_size_pretty(pg_relation_size('bench_admin_created_id')) AS added_index_size;
ROLLBACK;
