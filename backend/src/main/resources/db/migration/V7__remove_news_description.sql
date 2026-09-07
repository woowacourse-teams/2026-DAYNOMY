DROP INDEX IF EXISTS idx_news_description_lower_trgm;

ALTER TABLE news
    DROP COLUMN IF EXISTS description;
