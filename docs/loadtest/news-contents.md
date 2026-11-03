# 소식 외부 콘텐츠 API 부하 테스트

대상은 `GET /api/contents` 하나다. 실제 `/news` 화면 전체 흐름이나 외부 API 호출, 콘텐츠 반응 쓰기는 포함하지 않는다.

## 데이터 조건

- 개발 DB의 공개 외부 콘텐츠가 최소 10만 건이어야 한다.
- 대상 행은 `news_id IS NULL`이고 `source_type`이 `YOUTUBE`, `THREADS`, `X` 중 하나여야 한다.
- 적재 후 `ANALYZE public.stock_related_contents;`를 실행한다.

스크립트의 preflight가 첫 페이지 응답과 전체 개수를 확인한다. `MIDDLE`, `LAST`는 첫 페이지의 `totalPages`로 실제 대상 페이지를 계산하고 본 측정 전에 한 번 더 확인한다.

## 측정 조건

| RPS | VUS | MAX_VUS | 5분 요청 수 |
| --: | --: | ------: | ----------: |
| 100 |  75 |     100 |      30,000 |
| 300 | 225 |     300 |      90,000 |
| 500 | 375 |     500 |     150,000 |

합격 기준은 다음과 같다.

- p95 300ms 이하
- p99 500ms 이하
- HTTP 오류율 0%
- 응답 검사 성공률 100%
- `dropped_iterations` 0건
- 실제 요청 수가 `RPS × 측정 시간`과 일치

`dropped_iterations`가 있으면 서버 한계로 판정하지 않는다. k6 실행기의 CPU와 VU 용량을 먼저 확인한다.

## CASE

| CASE     | 측정 페이지        |
| -------- | ------------------ |
| `FIRST`  | 첫 페이지          |
| `MIDDLE` | 전체 페이지의 중간 |
| `LAST`   | 마지막 페이지      |

각 CASE와 RPS는 섞지 않고 별도 실행한다. 같은 조건을 두 번 실행해 첫 실행은 워밍업으로 제외하고 두 번째 결과만 기록한다.

## GitHub Actions 실행

`k6 development smoke test`에서 script로 `loadtest/news-contents-load.js`를 선택한다.

100 RPS:

```json
{
  "CASE": "FIRST",
  "RPS": 100,
  "VUS": 75,
  "MAX_VUS": 100,
  "DURATION": "5m",
  "RUN": "first-100-01"
}
```

300 RPS:

```json
{
  "CASE": "FIRST",
  "RPS": 300,
  "VUS": 225,
  "MAX_VUS": 300,
  "DURATION": "5m",
  "RUN": "first-300-01"
}
```

500 RPS:

```json
{
  "CASE": "FIRST",
  "RPS": 500,
  "VUS": 375,
  "MAX_VUS": 500,
  "DURATION": "5m",
  "RUN": "first-500-01"
}
```

`FIRST`가 끝나면 같은 순서로 `MIDDLE`, `LAST`를 실행한다. 300 RPS는 통과하고 500 RPS는 실패한다면 400 RPS부터 측정해 50 RPS 단위로 최대 통과 구간을 좁힌다.

결과는 `result-news-contents-<case>-<rps>rps-<run>.json`으로 Actions artifact에 보관된다.

## 직접 실행

저장소 루트에서 실제 개발 환경 origin으로 변경한다.

```bash
k6 run \
  -e BASE_URL=https://dev.example.invalid \
  -e CASE=FIRST \
  -e RPS=100 \
  -e VUS=75 \
  -e MAX_VUS=100 \
  -e DURATION=5m \
  -e RUN=first-100-01 \
  loadtest/news-contents-load.js
```

스크립트 회귀 테스트는 실제 HTTP 요청 없이 실행한다.

```bash
node --test loadtest/news-load.test.mjs
```

## 결과 기록

각 실행에서 다음을 함께 기록한다.

- 배포 SHA와 실행 시각
- CASE, RPS, VUS, 지속 시간, 예정·실제 요청 수
- p95, p99, 오류율, 검사 성공률, 누락 iteration
- 애플리케이션 CPU·메모리, JVM Heap·GC, HTTP 스레드
- HikariCP active·pending connection
- DB CPU·connection과 대상 쿼리 실행 시간

최대 통과 RPS와 최초 실패 RPS를 분리하고, 실패 기준이 지연 시간·HTTP 오류·누락 요청 중 무엇인지 명시한다.
