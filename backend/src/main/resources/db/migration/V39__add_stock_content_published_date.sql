ALTER TABLE stock_related_contents ADD COLUMN published_date DATE;
COMMENT ON COLUMN stock_related_contents.published_date IS '원문 게시일. 확인되지 않은 경우 NULL; 데이노미 등록일과 구분';
