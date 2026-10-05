CREATE INDEX idx_news_status_published_id
    ON news (status, published_at DESC, id DESC);
