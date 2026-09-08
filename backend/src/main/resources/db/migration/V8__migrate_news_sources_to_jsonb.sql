ALTER TABLE news
    ADD COLUMN sources JSONB;

UPDATE news
SET sources = jsonb_build_array(
    jsonb_build_object(
        'name',
        CASE source
            WHEN 'DART' THEN 'DART'
            WHEN 'KOSIS' THEN '국가통계포털'
            WHEN 'BOK' THEN '한국은행'
            ELSE '직접 입력'
        END,
        'url', source_url
    )
);

ALTER TABLE news
    ALTER COLUMN sources SET NOT NULL;

ALTER TABLE news
    DROP COLUMN source_url;
