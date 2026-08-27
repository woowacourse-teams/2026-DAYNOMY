-- 인덱스 적용 전후에 같은 조건으로 실행 계획과 버퍼 사용량을 비교한다.
-- keyword에는 NewsSearchService에서 이스케이프한 검색어와 같은 값을 입력한다.
\set ON_ERROR_STOP on
\set keyword '기준금리'
\set short_keyword '금리'
\set category 'BOND'
\set page_size 20
\set offset 0

\timing on

-- 전체 카테고리 검색 결과 조회
EXPLAIN (ANALYZE, BUFFERS)
SELECT n.*
FROM news n
WHERE LOWER(n.title) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!'
   OR LOWER(n.description) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!'
   OR LOWER(n.content) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!'
ORDER BY n.published_at DESC, n.id DESC
LIMIT :page_size OFFSET :offset;

-- 전체 카테고리 검색 개수 조회
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*)
FROM news n
WHERE LOWER(n.title) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!'
   OR LOWER(n.description) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!'
   OR LOWER(n.content) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!';

-- 특정 카테고리 검색 결과 조회
EXPLAIN (ANALYZE, BUFFERS)
SELECT n.*
FROM news n
WHERE n.category = :'category'
  AND (
    LOWER(n.title) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!'
    OR LOWER(n.description) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!'
    OR LOWER(n.content) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!'
  )
ORDER BY n.published_at DESC, n.id DESC
LIMIT :page_size OFFSET :offset;

-- 특정 카테고리 검색 개수 조회
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*)
FROM news n
WHERE n.category = :'category'
  AND (
    LOWER(n.title) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!'
    OR LOWER(n.description) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!'
    OR LOWER(n.content) LIKE LOWER(CONCAT('%', :'keyword', '%')) ESCAPE '!'
  );

-- trigram을 추출하지 못하는 짧은 검색어의 실행 계획도 별도로 확인한다.
EXPLAIN (ANALYZE, BUFFERS)
SELECT n.*
FROM news n
WHERE LOWER(n.title) LIKE LOWER(CONCAT('%', :'short_keyword', '%')) ESCAPE '!'
   OR LOWER(n.description) LIKE LOWER(CONCAT('%', :'short_keyword', '%')) ESCAPE '!'
   OR LOWER(n.content) LIKE LOWER(CONCAT('%', :'short_keyword', '%')) ESCAPE '!'
ORDER BY n.published_at DESC, n.id DESC
LIMIT :page_size OFFSET :offset;
