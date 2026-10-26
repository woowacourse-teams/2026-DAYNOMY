# 리그 조회 API 부하 테스트 실행

기준: **100 RPS · 50 VU · 5분 · p95 ≤ 300ms · p99 ≤ 500ms**.
전체 대상·데이터·합격 기준은 [공통 문서](league-user-apis.md)를 따른다.

## 실행 전 확인

- `daynomy_dev`에 참가자 최소 1만 명과 연관 데이터가 있어야 한다.
- 순위: 현재 주차 `confirmed=false`, `asOfDate` 존재, `totalCount >= 10000`. 주차가 바뀌면 다시 확인한다.
- 상세: 프로필·상세 공개 동의, 보유 종목·투자 판단 각각 1개 이상인 `PUBLIC_ID`를 준비한다.
- 팔로잉 요약: 유효한 토큰을 가진 계정 50명과 계정별 공개 팔로우 대상 1명 이상을 준비한다.
- 기존 `seed-league-rankings.sql`의 1,000명 데이터만으로는 최소 1만 명 조건을 충족하지 않는다.

## GitHub Actions

**k6 development smoke test**에서 스크립트가 반영된 브랜치를 선택한다. `DEV_BASE_URL`과 Prometheus 설정은 기존 값을 사용한다.

순위·상세·검색 부하 테스트는 같은 설정으로 연속 두 번 실행한다. **첫 5분은 워밍업, 두 번째 5분만 본 측정**이다. 워밍업의 성능 기준 초과는 본 측정 결과에 합산하지 않으며, 사전 검증·실행 오류가 나면 중단한다.

| 대상 | script | params |
| --- | --- | --- |
| 순위 | `loadtest/league-rankings-load.js` | `{"RPS":100,"VUS":50}` |
| 공개 투자자 상세 | `loadtest/league-investor-details-load.js` | `{"PUBLIC_ID":"<공개 ID>","RPS":100,"VUS":50}` |

팔로잉 요약용 인증 파일은 Actions에서 자동 생성하지 않는다. 파일을 준비한 로컬·러너에서 아래 명령으로 실행한다.

## 직접 실행

저장소 루트에서 예시 origin·공개 ID·파일 경로를 실제 값으로 바꾼다. API별로 하나씩 실행한다.

```bash
# 순위
k6 run -e BASE_URL=https://dev.example.invalid \
  -e RPS=100 -e VUS=50 loadtest/league-rankings-load.js

# 공개 투자자 상세
k6 run -e BASE_URL=https://dev.example.invalid -e PUBLIC_ID=replace-with-public-id \
  -e RPS=100 -e VUS=50 loadtest/league-investor-details-load.js

# 팔로잉 요약
k6 run -e BASE_URL=https://dev.example.invalid \
  -e LEAGUE_FIXTURE_FILE=/private/path/accounts-league.json \
  -e RPS=100 -e VUS=50 loadtest/league-follow-summary-load.js
```

인증 파일 형식: `{ "users": [{ "accessToken": "<유효 토큰>" }] }`. 앞에서 최대 `VUS`명을 사용한다. 파일은 Git에 포함하지 않고 토큰을 명령행·Actions params에 넣지 않는다. 토큰은 사전 조회와 본 측정 동안 유효해야 한다.

## 결과 확인

- 사전 조회 후 고정 100 RPS로 5분간 30,000건을 요청한다. 종료 대기는 최대 10초다.
- 로그: 예정/실제 요청 수, 실제 RPS, p95·p99, HTTP 오류율, 검사 성공률, 누락 요청 수. 별도 결과 JSON은 생성하지 않는다.
- Grafana: 실행 시간과 `testid=<Actions run ID>-<attempt>`로 조회한다.
- Actions 워밍업은 `testid=<Actions run ID>-<attempt>-warmup`, `run_phase=warmup`으로 구분한다. 본 측정만 조회할 때는 `run_phase=measurement`를 사용한다. 2회차도 100 RPS · 50 VU · 5분, p95 ≤ 300ms · p99 ≤ 500ms · HTTP 오류/누락 0건 · 30,000건 기준을 그대로 적용한다.
- 직접 실행은 자동 반복하지 않는다. 같은 명령을 연속 두 번 실행하고 두 번째 결과를 기록한다.
- 순위는 사전 조회한 주차, 상세는 지정한 공개 ID로 고정한다. 개선 전후는 동일한 데이터·입력으로 비교한다.
- 추후 증량은 `RPS=300,VUS=150`, `RPS=500,VUS=250`으로 각각 별도 실행한다. 실행 간 서버 회복·쓰기 데이터 복원을 확인한다.

## 스모크·스크립트 검증

연결 확인용 스모크는 `loadtest/league-rankings-smoke.js`, params `{}`를 사용한다. 기존 5 VU·5분(1분 1 RPS → 3분 5 RPS → 마지막 1분 5→0 RPS)을 유지하며 본 측정과 분리한다.

실제 서버 호출 없이 스크립트 검증:

```bash
node --test loadtest/league-load.test.mjs
node --test loadtest/k6-dev-workflow.test.mjs
```

## 로컬 투자자 상세 측정

Docker와 k6가 필요하다. 격리된 PostgreSQL에 참가자 1만 명과 연관 데이터를 적재하고, 일반 투자자 상세(종목 5개·판단 20개)를 워밍업 5분 + 본 측정 5분으로 실행한다.

```bash
cd backend
LEAGUE_INVESTOR_DETAILS_BENCHMARK=true ./gradlew test --rerun-tasks \
  --tests org.grit.daynomy.league.acceptance.LeagueRankingsLoadtestSeedTest.investorDetailsMeetLoadThresholdsWithTenThousandParticipants
```

결과는 JVM 임시 디렉터리의 `daynomy-investor-details-benchmark/measurement.log`와 `measurement.json`에 저장한다. dev에서 비교할 때는 V58 참가 이력 인덱스까지 적용한 버전을 사용한다.
