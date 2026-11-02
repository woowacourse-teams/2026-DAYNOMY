# 피드백 등록 API 부하 테스트

`POST /api/feedback`의 CSRF 처리와 PostgreSQL INSERT 경로를 고정 RPS로 측정한다. 공개 사용자 피드백 API이므로 로그인 계정은 사용하지 않는다.

## 주의사항

- 이 테스트는 실제 `feedbacks` 행을 생성한다.
- 100 RPS로 5분 실행하면 사전 검증 1건과 본 측정 최대 30,000건이 추가된다.
- 각 행의 `content`는 `[LOADTEST-FEEDBACK]`, `page_path`는 `/loadtest/feedback/{RUN}/{LOADTEST_ID}/` 접두어로 식별한다.
- 운영 환경에서는 실행하지 않는다.
- 스크립트는 데이터를 자동 삭제하지 않는다. 결과 확인 후 실행별 접두어로 대상 행을 조회하고 담당자가 범위를 확인한 뒤 정리한다.

## GitHub Actions 실행

`Actions → k6 development smoke test → Run workflow`에서 다음 값을 입력한다.

script:

```text
loadtest/feedback-load.js
```

30초 워밍업:

```json
{
  "RPS": 100,
  "VUS": 100,
  "MAX_VUS": 150,
  "DURATION": "30s",
  "RUN": "feedback-100-warmup"
}
```

공식 측정:

```json
{
  "RPS": 100,
  "VUS": 100,
  "MAX_VUS": 150,
  "DURATION": "5m",
  "RUN": "feedback-100-measure"
}
```

쓰기 테스트라서 30초 워밍업도 약 3,001건을 저장한다. 서버가 이미 준비된 상태라면 워밍업은 생략할 수 있다.

## 판정 기준

- p95 300ms 이하
- p99 500ms 이하
- HTTP 204 응답 검사 성공률 100%
- HTTP 오류율 0%
- `dropped_iterations` 0건
- 실제 요청 수가 `RPS × 측정 시간`과 일치

## 생성 데이터 확인

Actions 결과 JSON의 `pagePathPrefix`를 사용한다.

```sql
SELECT
    COUNT(*) AS feedback_count,
    MIN(created_at) AS first_created_at,
    MAX(created_at) AS last_created_at
FROM public.feedbacks
WHERE page_path LIKE :page_path_prefix || '%';
```

테스트 데이터를 삭제할 때는 위 조회 결과와 Actions 실행을 먼저 대조하고, 결과 JSON에 기록된 정확한 `pagePathPrefix`를 조건으로 사용한다. 광범위한 `[LOADTEST-FEEDBACK]` 조건만으로 삭제하지 않는다.

## 스크립트 검증

실제 HTTP 요청과 DB 쓰기 없이 실행한다.

```bash
node --test loadtest/feedback-load.test.mjs
```
