\set ON_ERROR_STOP on
-- Isolated PostgreSQL benchmark. All created objects are rolled back at the end.
-- Recorded 2026-10-06: PostgreSQL 16.15/aarch64, 20,000 synthetic rows, warm-up + 20 runs.
-- SQL timings only (not HTTP latency); results depend on data and hardware.
-- LIKE/trigram vs simple-config FTS with GIN, after the ordering index:
-- keyword       LIKE matches / p50 / p95 ms     FTS matches / p50 / p95 ms
-- 금리          2081 /   2.356 /   2.539         0 /  0.054 /  0.068
-- 기준금리      2081 /   2.365 /   2.424      2081 / 18.665 / 19.242
-- 금리 인하     2081 / 226.745 / 231.999         0 /  0.060 /  0.083
-- 기준금리 인하 2081 /  47.513 /  49.909       789 /  0.529 /  0.553
-- Decision: retain LIKE/pg_trgm to preserve Korean substring and literal-character matching.
-- simple FTS misses 금리 within 기준금리 and 인하 within 인하와; it also ignores % in 금%.
-- Faster FTS cases return different results, so they do not satisfy the current API contract.
-- Reconsider FTS if the search contract changes or a Korean tokenizer is separately evaluated.
-- Ordering index before -> after, p50/p95 ms:
-- first page:   210.492/260.864 ->   2.439/  2.820
-- ETF filter:    73.060/ 74.821 ->   2.039/  2.304
-- OFFSET 1000:  216.094/313.136 -> 231.405/320.705
-- short COUNT:  218.461/527.849 -> 217.935/340.939
-- EXPLAIN ANALYZE BUFFERS before -> after (execution ms; local buffer hits at Limit):
-- first page:  Seq Scan + top-N Sort, 225.938 ms/646 -> Index Scan, 2.250 ms/10
-- ETF filter:  Seq Scan + top-N Sort,  81.275 ms/646 -> Index Scan, 2.064 ms/23
-- OFFSET 1000: Seq Scan + top-N Sort, 227.279 ms/646 -> same plan, 211.862 ms/646
-- First-page scans use bench_status_published_id. The planner keeps a sequential scan for OFFSET.
-- The ordering index is 936 kB; deep pages, COUNT and relevance still scan many matches.
BEGIN;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
SELECT version();
-- Compare result semantics before timings: faster queries returning different rows are not replacements.
SELECT case_name, keyword,
       (SELECT bool_and(lower(title) LIKE '%' || replace(replace(replace(term, '!', '!!'), '%', '!%'), '_', '!_') || '%' ESCAPE '!'
                     OR lower(content) LIKE '%' || replace(replace(replace(term, '!', '!!'), '%', '!%'), '_', '!_') || '%' ESCAPE '!')
        FROM unnest(string_to_array(keyword, ' ')) AS terms(term)) AS substring_matches,
       to_tsvector('simple', title || ' ' || content) @@ plainto_tsquery('simple', keyword) AS fts_matches
FROM (VALUES
  ('short Korean', '기준금리 추가 인하 가능성', '일반 본문', '금리'),
  ('split terms', '기준금리 전망', '추가 인하 가능성', '금리 인하'),
  ('whole terms', '기준금리 전망', '추가 인하 가능성', '기준금리 인하'),
  ('literal present', '금% 뉴스', '본문', '금%'),
  ('literal absent', '금 뉴스', '본문', '금%')
) AS cases(case_name, title, content, keyword);
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
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND category = 'ETF' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20 OFFSET 1000;
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
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND category = 'ETF' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20 OFFSET 1000;
SELECT pg_size_pretty(pg_relation_size('bench_status_published_id')) AS added_index_size;
-- The FTS candidate uses a GIN index and the same data, status filter, order and page size.
-- Keep it after the before/after plans so it cannot affect the ordering-index comparison.
CREATE INDEX bench_fts ON news_search_benchmark USING gin (to_tsvector('simple', title || ' ' || content));
ANALYZE news_search_benchmark;
\echo LIKE/trigram versus PostgreSQL full-text search
SELECT keyword,
       count(*) FILTER (WHERE NOT EXISTS (
         SELECT FROM unnest(string_to_array(keyword, ' ')) AS terms(term)
         WHERE NOT (lower(title) LIKE '%' || term || '%' ESCAPE '!' OR lower(content) LIKE '%' || term || '%' ESCAPE '!')
       )) AS substring_matches,
       count(*) FILTER (WHERE to_tsvector('simple', title || ' ' || content) @@ plainto_tsquery('simple', keyword)) AS fts_matches
FROM news_search_benchmark CROSS JOIN (VALUES ('금리'), ('기준금리'), ('금리 인하'), ('기준금리 인하')) AS keywords(keyword)
WHERE status = 'PUBLISHED' GROUP BY keyword ORDER BY keyword;
SELECT * FROM pg_temp.measure_search('compare/LIKE/금리', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('compare/FTS/금리', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND to_tsvector('simple', title || ' ' || content) @@ plainto_tsquery('simple', '금리') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('compare/LIKE/기준금리', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%기준금리%' ESCAPE '!' OR lower(content) LIKE '%기준금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('compare/FTS/기준금리', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND to_tsvector('simple', title || ' ' || content) @@ plainto_tsquery('simple', '기준금리') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('compare/LIKE/금리 인하', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%금리%' ESCAPE '!' OR lower(content) LIKE '%금리%' ESCAPE '!') AND (lower(title) LIKE '%인하%' ESCAPE '!' OR lower(content) LIKE '%인하%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('compare/FTS/금리 인하', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND to_tsvector('simple', title || ' ' || content) @@ plainto_tsquery('simple', '금리 인하') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('compare/LIKE/기준금리 인하', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%기준금리%' ESCAPE '!' OR lower(content) LIKE '%기준금리%' ESCAPE '!') AND (lower(title) LIKE '%인하%' ESCAPE '!' OR lower(content) LIKE '%인하%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20$$);
SELECT * FROM pg_temp.measure_search('compare/FTS/기준금리 인하', $$SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND to_tsvector('simple', title || ' ' || content) @@ plainto_tsquery('simple', '기준금리 인하') ORDER BY published_at DESC, id DESC LIMIT 20$$);
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND (lower(title) LIKE '%기준금리%' ESCAPE '!' OR lower(content) LIKE '%기준금리%' ESCAPE '!') ORDER BY published_at DESC, id DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM news_search_benchmark WHERE status = 'PUBLISHED' AND to_tsvector('simple', title || ' ' || content) @@ plainto_tsquery('simple', '기준금리') ORDER BY published_at DESC, id DESC LIMIT 20;
ROLLBACK;
