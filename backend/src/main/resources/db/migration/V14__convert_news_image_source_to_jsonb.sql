ALTER TABLE news
    ALTER COLUMN image_source DROP DEFAULT;

ALTER TABLE news
    ALTER COLUMN image_source TYPE jsonb
    USING CASE
        WHEN btrim(image_source) = '' THEN '{"name":"","url":""}'::jsonb
        WHEN image_source ~* '^https?://' THEN jsonb_build_object('name', '', 'url', image_source)
        ELSE jsonb_build_object('name', image_source, 'url', '')
    END;

ALTER TABLE news
    ALTER COLUMN image_source SET DEFAULT '{"name":"","url":""}'::jsonb;
