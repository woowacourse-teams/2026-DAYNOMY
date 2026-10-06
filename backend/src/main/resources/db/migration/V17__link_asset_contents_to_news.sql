ALTER TABLE stock_related_contents
    ADD COLUMN news_id BIGINT;

ALTER TABLE stock_related_contents
    ADD CONSTRAINT fk_stock_related_contents_news
        FOREIGN KEY (news_id) REFERENCES news (id);

CREATE INDEX idx_stock_related_contents_news
    ON stock_related_contents (news_id);
