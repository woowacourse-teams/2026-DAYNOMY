# 콘텐츠 통합 검색 API 부하 테스트

대상은 `GET /api/search/contents`이며, [content-search-load.js](../../loadtest/content-search-load.js)를 사용한다.

## 실행 기준

| 항목 | 기준 |
| --- | --- |
| 검색 조건 | `source=ALL`, `sort=LATEST`, `page=1`, `size=12` |
| 요청률 / VU | **100 RPS / 50 VU** (`VU = ceil(RPS × 0.5초)`) |
| 지속 시간 | **5분**, 예정 요청 30,000건 |
| 응답 시간 | **p95 ≤ 300ms, p99 ≤ 500ms** |
| 오류 / 검사 / 누락 | HTTP 오류 0건, 응답 검사 성공률 100%, 누락 요청 0건 |
| 추후 측정 | 300 RPS / 150 VU → 500 RPS / 250 VU, 각각 별도 실행 |

`constant-arrival-rate`로 iteration당 한 번 호출한다. 사전 조회와 종료 대기는 본 측정 5분에서 제외하며, VU 산식은 초기 할당 기준이다. 요청이 누락되면 목표 RPS를 달성한 것으로 판단하지 않는다.

## 실행 전 확인

- 결과가 있는 검색어를 `QUERY`로 지정한다. 리그 시드는 검색 콘텐츠를 생성하지 않으며, 검색에 리그 참가자 1만 명 조건을 적용하지 않는다.
- 검색 결과가 없거나 응답 형식이 다르면 본 측정을 시작하지 않는다.
- 개선 전후에는 같은 데이터와 검색 조건을 사용하고, 검색어와 실제 검색 결과 건수를 기록한다.

## GitHub Actions

**k6 development smoke test**에서 스크립트가 반영된 브랜치를 선택한다. `DEV_BASE_URL`과 Prometheus 설정은 기존 값을 사용한다.

| script | params |
| --- | --- |
| `loadtest/content-search-load.js` | `{"QUERY":"금리","RPS":100,"VUS":50}` |

Actions를 **같은 script·params로 직접 두 번 실행**한다. 첫 실행 5분은 워밍업으로 제외하고 두 번째 실행 5분만 본 측정으로 사용한다. 워밍업의 성능 기준 초과가 Actions 실패로 표시돼도 본 측정 결과에는 합산하지 않는다. 사전 검증·실행 오류는 해결한 뒤 다시 시작한다.

## 직접 실행

저장소 루트에서 예시 origin과 검색어를 실제 값으로 바꾼다.

```bash
k6 run -e BASE_URL=https://dev.example.invalid -e QUERY=금리 \
  -e RPS=100 -e VUS=50 loadtest/content-search-load.js
```

직접 실행은 자동 반복하지 않는다. 같은 명령을 연속 두 번 실행하고 두 번째 결과를 기록한다.

## 결과 확인

- 사전 조회 후 고정 100 RPS로 5분간 30,000건을 요청한다. 종료 대기는 최대 10초다.
- 로그에서 예정/실제 요청 수, 실제 RPS, p95·p99, HTTP 오류율, 검사 성공률과 누락 요청 수를 확인한다. 별도 결과 JSON은 생성하지 않는다.
- Grafana는 실행 시간과 `testid=<Actions run ID>-<attempt>`로 조회한다. 본 측정은 두 번째 실행 ID를 사용하며 첫 실행 데이터는 합산하지 않는다.
- 배포 SHA, 검색어·검색 결과 건수·데이터 규모, 목표/실제 RPS·요청 수, VU, p95·p99, 오류·누락과 서버 CPU·메모리·DB 연결풀 상태를 기록한다.
- 증량은 `RPS=300,VUS=150`, `RPS=500,VUS=250`으로 각각 별도 실행한다. 실행 간 서버 회복·데이터 상태를 확인한다.

## 스크립트 검증

실제 서버 호출 없이 검색 스크립트의 입력·응답 검사와 실행 설정, Actions 연동을 확인한다.

```bash
node --test loadtest/league-load.test.mjs
node --test loadtest/k6-dev-workflow.test.mjs
```

검색 스크립트 검증은 기존 `league-load.test.mjs`에 포함되어 있다. 위 명령의 통과는 실제 서버의 성능 기준 충족을 뜻하지 않는다.
