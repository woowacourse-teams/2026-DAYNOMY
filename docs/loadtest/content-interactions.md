# 콘텐츠 반응 저장 API 부하 테스트

`POST /api/content-interactions`의 CSRF 처리, 익명 방문자 식별, 요청 제한, 공개 콘텐츠 검증, 중복 방지와 PostgreSQL INSERT 경로를 고정 RPS로 측정한다.

## 주의사항

- 이 테스트는 실제 `content_interactions` 행을 생성한다.
- 100 RPS로 5분 실행하면 사전 검증 1건과 본 측정 최대 30,000건이 추가된다.
- 각 요청은 새로운 익명 방문자로 전송한다. 동일 방문자를 재사용하면 분당 60회 제한과 일일 중복 제거 때문에 실제 INSERT 부하를 측정할 수 없기 때문이다.
- 결과는 콘텐츠 인기순위에 반영된다. 운영 환경에서는 실행하지 않는다.
- 테스트 전후 행 수와 테스트 시간을 기록하고, 결과 확인이 끝난 뒤 담당자가 범위를 확인해 데이터를 정리한다. 스크립트는 데이터를 자동 삭제하지 않는다.

## 테스트 콘텐츠 선택

실제 사용자 콘텐츠 대신 부하 테스트용으로 생성한 공개 종목 콘텐츠 하나를 사용한다.

```sql
SELECT id, asset_id, title, url
FROM public.stock_related_contents
WHERE url LIKE 'https://loadtest.daynomy.invalid/%'
ORDER BY id
LIMIT 10;
```

선택한 `id`를 `CONTENT_ID`에 입력한다. API가 공개 상태를 검증하므로 존재하지 않거나 공개되지 않은 콘텐츠는 사전 검증에서 중단된다.

테스트 전 행 수:

```sql
SELECT COUNT(*)
FROM public.content_interactions
WHERE content_type = 'ASSET_CONTENT'
  AND content_id = :content_id;
```

## GitHub Actions 실행

`Actions → k6 development smoke test → Run workflow`에서 다음 값을 입력한다.

script:

```text
loadtest/content-interactions-load.js
```

데이터 생성을 줄이기 위한 30초 워밍업 예시:

```json
{
  "CONTENT_TYPE": "ASSET_CONTENT",
  "CONTENT_ID": 123,
  "INTERACTION_TYPE": "OPEN",
  "RPS": 100,
  "VUS": 100,
  "MAX_VUS": 150,
  "DURATION": "30s",
  "RUN": "content-interactions-123-100-warmup"
}
```

공식 측정은 `DURATION`을 `5m`, `RUN`을 `content-interactions-123-100-measure`로 바꾼다. 30초 워밍업도 약 3,000건을 저장하므로 서버가 이미 준비된 상태라면 생략할 수 있다.

## 판정 기준

- p95 300ms 이하
- p99 500ms 이하
- HTTP 204 응답과 익명 방문자 쿠키 검증 성공률 100%
- HTTP 오류율 0%
- `dropped_iterations` 0건
- 실제 요청 수가 `RPS × 측정 시간`과 일치

## 테스트 후 확인

```sql
SELECT
    COUNT(*) AS interaction_count,
    MIN(created_at) AS first_created_at,
    MAX(created_at) AS last_created_at
FROM public.content_interactions
WHERE content_type = 'ASSET_CONTENT'
  AND content_id = :content_id;
```

테스트 데이터를 삭제할 때는 위 결과와 실행 시간을 먼저 확인하고, 선택한 테스트 콘텐츠 ID와 생성 시간 범위를 모두 조건으로 사용한다. 다른 반응 데이터를 포함할 수 있는 광범위한 삭제 조건은 사용하지 않는다.

## 스크립트 검증

실제 HTTP 요청과 DB 쓰기 없이 실행한다.

```bash
node --test loadtest/content-interactions-load.test.mjs
```
