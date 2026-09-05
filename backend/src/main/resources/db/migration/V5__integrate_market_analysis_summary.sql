ALTER TABLE news_market_analysis
    ADD COLUMN summary TEXT;

UPDATE news_market_analysis
SET summary = cause || E'\n\n' || importance;

ALTER TABLE news_market_analysis
    ALTER COLUMN summary SET NOT NULL,
    DROP COLUMN cause,
    DROP COLUMN importance;
