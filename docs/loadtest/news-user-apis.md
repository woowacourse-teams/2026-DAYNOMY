# 소식 사용자 API 개별 부하 테스트

실제 `/news` 사용자 흐름을 한 번에 재현하지 않고 API를 하나씩 고정 RPS로 측정한다. 외부 API, 관리자 API, 콘텐츠 반응·피드백 쓰기는 포함하지 않는다.

공통 기준은 다음과 같다.

- 100 → 300 → 500 RPS를 각각 별도로 실행한다.
- 같은 조건의 첫 5분 실행은 워밍업으로 제외하고 두 번째 5분 실행을 기록한다.
- p95 300ms 이하, p99 500ms 이하
- HTTP 오류율 0%, 응답 검사 성공률 100%
- `dropped_iterations` 0건
- 실제 요청 수가 `RPS × 측정 시간`과 일치

| RPS | VUS | MAX_VUS | 5분 요청 수 |
| --: | --: | ------: | ----------: |
| 100 |  75 |     100 |      30,000 |
| 300 | 225 |     300 |      90,000 |
| 500 | 375 |     500 |     150,000 |

## 대상과 스크립트

| API                                  | script                              | 필수·주요 입력                 |
| ------------------------------------ | ----------------------------------- | ------------------------------ |
| `GET /api/contents`                  | `loadtest/news-contents-load.js`    | `CASE=FIRST\|MIDDLE\|LAST`     |
| `GET /api/assets/{assetId}/contents` | `loadtest/asset-contents-load.js`   | `ASSET_ID`, 기본 최소 500건    |
| `GET /api/content-rankings`          | `loadtest/content-rankings-load.js` | `PERIOD_DAYS=7`, 기본 최소 1건 |
| `GET /api/news`                      | `loadtest/news-list-load.js`        | `CASE=FIRST\|MIDDLE\|LAST`     |
| `GET /api/news/{id}`                 | `loadtest/news-detail-load.js`      | 발행된 `NEWS_ID`               |
| `GET /api/people`                    | `loadtest/people-load.js`           | 기본 최소 1명                  |

목록 API의 `MIDDLE`, `LAST`는 첫 페이지 preflight의 `totalPages`를 기준으로 실제 페이지를 계산한다. 데이터가 부족하거나 응답 계약이 다르면 본 측정을 시작하지 않는다.

## 입력 데이터 확인

인기 종목 ID:

```sql
SELECT asset_id, COUNT(*) AS content_count
FROM public.stock_related_contents
WHERE url LIKE 'https://loadtest.daynomy.invalid/skewed-v1/%'
GROUP BY asset_id
ORDER BY content_count DESC, asset_id
LIMIT 1;
```

발행 뉴스 ID:

```sql
SELECT id, title
FROM public.news
WHERE status = 'PUBLISHED'
ORDER BY published_at DESC NULLS LAST, id DESC
LIMIT 1;
```

인기 점수 API는 최근 측정 기간에 `content_interactions`가 최소 1건 있어야 한다. 쓰기 부하와 데이터 생성은 이번 범위에 포함하지 않는다.

## GitHub Actions 예시

`k6 development smoke test`에서 각 스크립트를 하나씩 선택한다.

인기 종목 콘텐츠 100 RPS:

```json
{
  "ASSET_ID": 123,
  "RPS": 100,
  "VUS": 75,
  "MAX_VUS": 100,
  "DURATION": "5m",
  "RUN": "asset-100-01"
}
```

콘텐츠 인기순위 100 RPS:

```json
{
  "PERIOD_DAYS": 7,
  "RPS": 100,
  "VUS": 75,
  "MAX_VUS": 100,
  "DURATION": "5m",
  "RUN": "rankings-100-01"
}
```

뉴스 목록 100 RPS:

```json
{
  "CASE": "FIRST",
  "RPS": 100,
  "VUS": 75,
  "MAX_VUS": 100,
  "DURATION": "5m",
  "RUN": "news-first-100-01"
}
```

뉴스 상세 100 RPS:

```json
{
  "NEWS_ID": 123,
  "RPS": 100,
  "VUS": 75,
  "MAX_VUS": 100,
  "DURATION": "5m",
  "RUN": "news-detail-100-01"
}
```

주요 인물 100 RPS:

```json
{
  "RPS": 100,
  "VUS": 75,
  "MAX_VUS": 100,
  "DURATION": "5m",
  "RUN": "people-100-01"
}
```

각 API에서 300 RPS는 통과하고 500 RPS는 실패하면 400 RPS부터 실행해 50 RPS 단위로 최대 통과 구간을 좁힌다. 결과 JSON은 `loadtest/result-*.json` Actions artifact에 보관된다.

## 스크립트 검증

실제 HTTP 요청 없이 실행한다.

```bash
node --test \
  loadtest/news-load.test.mjs \
  loadtest/news-user-api-load.test.mjs \
  loadtest/k6-dev-workflow.test.mjs
```
