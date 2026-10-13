UPDATE stock_related_contents
SET published_date = (created_at AT TIME ZONE 'Asia/Seoul')::DATE
WHERE news_id IS NULL
  AND source_type IN ('YOUTUBE', 'THREADS', 'X')
  AND published_date IS NULL;

CREATE INDEX idx_stock_related_contents_public_published_date
    ON stock_related_contents (published_date DESC, created_at DESC, id DESC)
    WHERE news_id IS NULL
      AND source_type IN ('YOUTUBE', 'THREADS', 'X');
