ALTER TABLE news
    DROP CONSTRAINT uk_news_source_external_id;

ALTER TABLE news
    DROP COLUMN source,
    DROP COLUMN external_id;
