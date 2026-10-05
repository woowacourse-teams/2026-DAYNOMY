\set ON_ERROR_STOP on
-- Isolated PostgreSQL benchmark. All created objects are rolled back at the end.
BEGIN;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
SELECT version();
SELECT '금리' AS keyword,
       to_tsvector('simple', '기준금리 추가 인하 가능성') AS vector,
       to_tsvector('simple', '기준금리 추가 인하 가능성') @@ plainto_tsquery('simple', '금리') AS fts_matches,
       LOWER('기준금리 추가 인하 가능성') LIKE '%금리%' AS substring_matches;
SELECT to_tsvector('simple', '금% 뉴스') @@ plainto_tsquery('simple', '금%') AS fts_literal,
       to_tsvector('simple', '금 뉴스') @@ plainto_tsquery('simple', '금%') AS fts_nonliteral;
CREATE TEMP TABLE news_search_benchmark (
  id BIGINT PRIMARY KEY, title TEXT NOT NULL, content TEXT NOT NULL,
  category VARCHAR(30) NOT NULL, status VARCHAR(20) NOT NULL,
  published_at TIMESTAMPTZ NOT NULL
);
INSERT INTO news_search_benchmark
SELECT i,
  CASE WHEN i % 19 = 0 THEN '기준금리 추가 인하 가능성'
       WHEN i % 17 = 0 THEN 'ETF 투자 수익률 분석'
       ELSE '산업 전망과 시장 변동' END,
  repeat('경제 동향과 정책 변화가 기업에 미치는 영향을 분석합니다. ', 30) ||
  CASE WHEN i % 11 = 0 THEN '기준금리 인하와 환율 변동'
       ELSE '실적 발표와 원자재 가격' END,
  CASE WHEN i % 3 = 0 THEN 'ETF' ELSE 'STOCK' END,
  CASE WHEN i % 4 = 0 THEN 'DRAFT' ELSE 'PUBLISHED' END,
  TIMESTAMPTZ '2026-01-01 00:00:00+00' + i * INTERVAL '1 minute'
FROM generate_series(1, 20000) AS i;
CREATE INDEX bench_title_trgm ON news_search_benchmark USING gin (lower(title) gin_trgm_ops);
CREATE INDEX bench_content_trgm ON news_search_benchmark USING gin (lower(content) gin_trgm_ops);
ANALYZE news_search_benchmark;
SELECT count(*) FILTER (WHERE lower(title) LIKE '%금리 인하%' OR lower(content) LIKE '%금리 인하%') AS phrase_matches,
       count(*) FILTER (WHERE (lower(title) LIKE '%금리%' OR lower(content) LIKE '%금리%')
                          AND (lower(title) LIKE '%인하%' OR lower(content) LIKE '%인하%')) AS all_terms_matches
FROM news_search_benchmark WHERE status = 'PUBLISHED';
CREATE FUNCTION pg_temp.measure_search(label TEXT, statement TEXT)
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
\echo Before ordering index
SELECT * FROM pg_temp.measure_search('short/latest/first', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('short/latest/ETF', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND category = 'ETF' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('short/latest/offset1000', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20 OFFSET 1000$$);
SELECT * FROM pg_temp.measure_search('long/latest/first', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%기준금리%' ESCAPE '!' OR lower(content) LIKE '%기준금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('short/count', $$SELECT count(*) FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!')$$);
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20;
CREATE INDEX bench_status_published_id ON news_search_benchmark (status, published_at DESC, id DESC);
ANALYZE news_search_benchmark;
\echo After ordering index
SELECT * FROM pg_temp.measure_search('short/latest/first', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('short/latest/ETF', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND category = 'ETF' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('short/latest/offset1000', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20 OFFSET 1000$$);
SELECT * FROM pg_temp.measure_search('long/latest/first', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%기준금리%' ESCAPE '!' OR lower(content) LIKE '%기준금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('short/count', $$SELECT count(*) FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!')$$);
SELECT * FROM pg_temp.measure_search('multi/latest', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') AND (lower(title) LIKE '%인하%' ESCAPE '!' OR lower(content) LIKE '%인하%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('multi/relevance', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') AND (lower(title) LIKE '%인하%' ESCAPE '!' OR lower(content) LIKE '%인하%' ESCAPE '!') ORDER BY ((CASE WHEN lower(title) LIKE '%금리%' ESCAPE '!' THEN 1 ELSE 0 END) + (CASE WHEN lower(title) LIKE '%인하%' ESCAPE '!' THEN 1 ELSE 0 END)) DESC, published_at DESC, id DESC LIMIT 20$$);
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20;
SELECT pg_size_pretty(pg_relation_size('bench_status_published_id')) AS added_index_size;
ROLLBACK;
