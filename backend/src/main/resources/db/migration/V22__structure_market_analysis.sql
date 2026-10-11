ALTER TABLE news_market_analysis
    ADD COLUMN interpretation TEXT NOT NULL DEFAULT '',
    ADD COLUMN risk TEXT NOT NULL DEFAULT '',
    ADD COLUMN watch_point TEXT NOT NULL DEFAULT '',
    ADD COLUMN limitation TEXT NOT NULL DEFAULT '';

ALTER TABLE news_market_analysis
    ALTER COLUMN interpretation DROP DEFAULT,
    ALTER COLUMN risk DROP DEFAULT,
    ALTER COLUMN watch_point DROP DEFAULT,
    ALTER COLUMN limitation DROP DEFAULT;
