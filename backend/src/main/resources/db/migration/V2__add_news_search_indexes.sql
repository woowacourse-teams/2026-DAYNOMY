CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_news_title_lower_trgm
    ON news USING gin (LOWER(title) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_news_description_lower_trgm
    ON news USING gin (LOWER(description) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_news_content_lower_trgm
    ON news USING gin (LOWER(content) gin_trgm_ops);

ANALYZE news;
