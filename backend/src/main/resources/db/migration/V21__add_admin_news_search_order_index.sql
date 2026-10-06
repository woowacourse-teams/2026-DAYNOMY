CREATE INDEX CONCURRENTLY idx_news_created_id
    ON news (created_at DESC, id DESC);
