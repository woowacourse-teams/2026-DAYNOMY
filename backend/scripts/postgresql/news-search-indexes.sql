-- PostgreSQL에서 psql로 실행한다.
-- CREATE INDEX CONCURRENTLY는 트랜잭션 블록 안에서 실행할 수 없다.
-- 검색 패턴에서 trigram을 추출할 수 없는 짧은 검색어는 인덱스 효과가 제한된다.
\set ON_ERROR_STOP on

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_news_title_lower_trgm
    ON news USING gin (LOWER(title) gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_news_description_lower_trgm
    ON news USING gin (LOWER(description) gin_trgm_ops);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_news_content_lower_trgm
    ON news USING gin (LOWER(content) gin_trgm_ops);

ANALYZE news;
