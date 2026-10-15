# 과거 거래 등록 V1/V2 부하테스트 (#496)

Actions에서는 `script`로 V1/V2를 선택하고 `params`로 날짜·RPS·시간을 입력한다.
아래 Actions 예시는 기준일 `2026-10-16`이며, SQL 적재와 원본 보관도 같은 날짜로 맞춘다.
로컬 명령의 기본값은 `loadtest-config.json`에 있고, 실행 관리는 `run-loadtest.py`가 담당한다.
기본 실행은 **통신 없는 계획 확인**이다. dev 접근은 명시적 옵션에서만 발생한다.

## 구성

```text
backend/loadtest/
├── loadtest-config.json      # 바꿀 테스트 조건
├── run-loadtest.py           # 단계 실행·준비 도구 호출·자원 감시·중단
├── transactions-v1.js        # V1 선택용 k6 진입점
├── transactions-v2.js        # V2 선택용 k6 진입점
├── support/
│   ├── scenario.js           # 두 버전이 공유하는 요청·워밍업·본 측정
│   ├── prepare.js            # 설정·날짜·계정·토큰 검증 (HTTP 호출 없음)
│   └── report.js             # 지표·판정 기준·결과 집계
├── sql/
│   └── seed-portfolios.sql   # 사람이 직접 실행하는 초기 적재
└── tests/
    ├── transactions.test.mjs
    └── test_run_loadtest.py
```

토큰 발급·DB 복원 Java 도구와 그 테스트는 `src/test/java/org/grit/daynomy/loadtest/`에 분리한다.
`portfolioLoadtest` Gradle 작업이 기존 JWT 발급 로직을 재사용하며, 도구는 애플리케이션 JAR에 포함되지 않는다.
실행할 때 `transactions-v1.js` 또는 `transactions-v2.js`를 선택한다. 공통 동작은 `support/`에서 공유한다.
`run-loadtest.py`가 GitHub Actions의 `params` 입력도 직접 받는다. 별도 Python 연결 파일은 두지 않는다.
날짜별로 스크립트를 복제하지 않는다. Python 실행 관리·SQL 적재·Java 준비 도구는 기존 역할을 유지한다.

`.local/`은 실행 준비·결과를 위한 Git 제외 경로다. 이전 로컬 실험 자료는 제거했다.
`dev.env`는 사람이 준비하고, `baseline.json`, `users.json`, `warmup-users.json`, 결과는 도구가 생성한다.

## 설정

| 키                      | 의미 / 초기값                                                         |
| ----------------------- | --------------------------------------------------------------------- |
| `baseUrl`               | HTTPS dev API origin                                                  |
| `version`               | 로컬 관리자 실행용 `v1`/`v2`. Actions에서는 선택한 파일로 결정                    |
| `referenceDate`         | 비교 기준일 고정. `tradeMonthsAgo`만큼 달력 월을 빼고 월말 보정       |
| `tradeMonthsAgo`        | 1개월 전(주 시나리오) / 6개월 전(추가 시나리오), 각각 실행            |
| `rpsSteps`              | 전체 거래 POST 시작 요청률 `0.1, 0.2, 0.5, 1, 2, 3`                   |
| `stageSeconds`          | 단계별 탐색 120초. 예정 요청 `12, 24, 60, 120, 240, 360`건            |
| `warmupSeconds`         | 측정 전 별도 3계정을 순환해 최소 120초 워밍업                         |
| `vus`                   | 미리 확보할 실행 슬롯 50개. 50계정이 항상 동시에 요청한다는 뜻은 아님 |
| `requestTimeoutSeconds` | 클라이언트 타임아웃 10초                                              |
| `targetP95Ms`           | 측정 POST 경과시간 p95 목표 3,000ms                                   |
| `monitoring`            | Prometheus 주소, 앱·DB instance 라벨, CPU·메모리 중단 기준            |

namespace는 기준일에서 `lt496-YYYYMMDD`로 자동 생성한다. 파일 경로도 `.local/`로 고정하여 설정하지 않는다.
일상적으로 수정할 조건만 JSON에 두며, 모니터링 조회 15초·최대 지표 나이 45초·안정 대기 30초·최대 대기 180초는 코드 기본값이다.
필요할 때 `monitoring`에 `pollSeconds`, `maxMetricAgeSeconds`, `cooldownSeconds`, `recoveryTimeoutSeconds`를 추가해 재정의할 수 있다.

RPS는 분당 정수, 단계 요청 수도 정수여야 하고 한 단계 최대 5,000건이다.
계정 요청이 겹치지 않도록 `RPS × (타임아웃+1초) < 50`도 검사한다. 범위를 넘는 요청률은 별도 계정 확장 또는 타임아웃 조정이 필요하다.
토큰 유효기간과 거래 이력·스냅샷 범위도 검사한다. 서버는 설정·데이터가 준비돼 있어야 하며 이 도구는 배포하지 않는다.

## 사용자 데이터

| 그룹        |     측정 회원 수 | 회원당 현재 종목 수 | 회원당 BUY/SELL 이력 |
| ----------- | ---------------: | ------------------: | -------------------: |
| light       |               20 |                   5 |                   50 |
| standard    |               20 |                  15 |                  200 |
| heavy       |               10 |                  30 |                  800 |
| 별도 워밍업 | 그룹별 1명, 총 3 |             5/15/30 |           50/200/800 |

53명 합계 보유 행 750개, BUY/SELL 14,050건, 초기 잔고 포함 이력 14,800건이다.
실제 종가 1년 및 직전 거래일이 충분한 주식이 30개 이상 필요하며, 부족하면 적재를 취소한다. 종목·종가는 생성하거나 수정하지 않는다.
고정 시드로 최근 거래 비중, 수량·단가·종목 조합을 다양화한다. 전량 매도 후 재매수를 포함하되 음수 잔고와 잔고 조정 이력은 만들지 않는다.
스냅샷은 최근 6개월 구간에 생성한다. 같은 namespace가 이미 있으면 기존 데이터를 삭제하지 않고 중단한다.

측정 회원 1~~50번은 `light/standard/light/standard/heavy`를 반복하며, 51~~53번은 그룹별 워밍업 회원이다.
VU 자동 선택과 분리된 **전역 요청 순번**으로 50계정을 순회한다. V1/V2는 같은 계정·종목·수량을 같은 순서로 요청한다.
50건 완주 시 그룹 요청 비중은 40/40/20이고, 짧은 단계에서는 일부 회원이 0건일 수 있다. 실제 계정별 건수도 기록한다.

## 준비 및 실행

아래 명령은 `backend/loadtest`에서 실행한다. k6·Python 3·Java/Gradle이 필요하다.
k6 부하 발생기는 가급적 앱·DB와 다른 머신에서 실행한다. 실제 dev 실행 전 배포 버전·V2 API·공유 사용 시간을 확인한다.

### 1. 계획 확인 — 통신 없음

```bash
python3 run-loadtest.py --plan
```

명령만 실행해도 기본값은 계획 확인이다. Gradle·DB·Prometheus·API에 접근하지 않는다.
개인 환경 설정은 `.local/config.json`에 복사하고 `--config .local/config.json`으로 지정할 수 있다.

### 2. 초기 SQL 직접 실행

DB 도구에서 **dev PostgreSQL**에 접속하고 `sql/seed-portfolios.sql`을 연다.
SQL의 기본 기준일은 실행 당일(KST)이다. 아래 Actions 예시와 날짜를 고정하려면 파일 위쪽 UPDATE의 주석을 해제하고 다음으로 바꾼다.

```sql
UPDATE lt_config SET as_of = DATE '2026-10-16';
```

이후 **BEGIN부터 COMMIT까지 전체 파일을 같은 연결에서 한 번에 실행**한다.
측정 회원 50명과 워밍업 3명의 포트폴리오·보유·거래·과거 스냅샷을 만든다.
기존 종목·종가는 생성하지 않으므로 1년 가격 이력이 충분한 주식 30개 이상이 필요하다.
같은 기준일 회원이 이미 있거나 가격이 부족하면 중단한다. 중간 오류가 나면 `ROLLBACK;` 후 원인을 확인한다.

적재 후 아래 결과가 53인지 확인한다.

```sql
SELECT count(*) AS test_users
FROM members
WHERE provider_id LIKE 'lt496-20261016-%';
```

로컬 설정 JSON의 날짜를 반영한 SQL을 출력하려면 다음 명령을 쓸 수도 있다.

```bash
python3 run-loadtest.py --seed-sql
```

`sql/seed-portfolios.sql`에 설정 기준일을 반영해 **출력만** 한다. 출력 전체를 의도한 dev DB에서 직접 실행한다.
초기 적재는 자동 실행하지 않는다. SQL 파일을 직접 열어 실행하는 경우 기준일이 JSON과 같은지 확인한다.

### 3. 비공개 환경 및 모니터링 준비

`.local/dev.env`를 권한 `600`으로 준비한다. 필수 키는 다음과 같다.

- `LOADTEST_ENVIRONMENT=dev`
- `DB_URL`(PostgreSQL JDBC URL), `DB_USERNAME`, `DB_PASSWORD`
- `JWT_SECRET`, 선택적으로 `JWT_ISSUER`(기본 `daynomy`)

비밀 값은 JSON·명령 인자·Git·로그에 넣지 않는다. Prometheus는 내부망 또는 SSH 터널로 접근한다.
기본 `127.0.0.1:9090`은 터널이 준비된 경우의 예시이며, Prometheus/Actuator를 인터넷에 새로 공개하지 않는다.
Grafana에서 지표가 보이는 것과 실행 머신이 Prometheus API를 조회할 수 있는 것은 별도다.

### 4. 기준 데이터 보관 — dev DB 읽기

```bash
python3 run-loadtest.py --capture
```

53명의 보유·거래·스냅샷과 가격 문맥을 `.local/baseline.json`에 보관한다. 기존 baseline을 덮어쓰지 않는다.

### 5. 실제 부하 — dev DB/API 접근

```bash
python3 run-loadtest.py --execute
```

각 RPS를 별도 k6 프로세스로 실행한다. 자원 안정 확인 → 대상 락 확인 → 초기 데이터 복원 → 토큰 발급 → 워밍업 → 측정을 수행한다.
설정 사본을 실행별로 고정하므로 원본 JSON을 중간에 편집해도 진행 중 실행에는 영향을 주지 않는다.
다른 버전 비교는 `version`만 바꾸고 초기 데이터·날짜·RPS·시간·VU·목표·서버 조건을 맞춘다.

JWT는 기존 `JwtTokenProvider`로 발급한다. Google 로그인은 부하에 포함하지 않지만 서버 JWT/CSRF 검증은 유지한다.
서명키는 k6에 전달하지 않고 토큰만 비공개 파일에 저장한다. 실행에 필요한 유효기간으로 단계별 새 발급하며 전역 만료 설정은 바꾸지 않는다.
복원은 캡처한 테스트 포트폴리오의 보유·이력·스냅샷만 대상으로 한다. 다른 사용자·회원·종목·가격·시퀀스는 변경하지 않는다.
대상 DB/API·회원 신원·가격 문맥·fingerprint가 달라지거나 대상 락이 남아 있으면 거절한다.

## GitHub Actions에서 실행할 때

[기존 k6 workflow](../../.github/workflows/k6-dev.yml)의 `script`, `params` 두 입력을 그대로 사용한다.
별도 workflow를 만들지 않고 `script`가 **`backend/loadtest/transactions-v1.js` 또는 `backend/loadtest/transactions-v2.js`**이면
`python3 -B backend/loadtest/run-loadtest.py --actions`를 직접 실행한다. `params` JSON은 `K6_PARAMS_JSON` 환경변수로 전달한다.
그 외 루트 `loadtest/` 스크립트와 기존 파라미터 전달 방식은 유지한다.

```text
Run workflow의 params JSON
→ 기본 loadtest-config.json 위에 지정한 값만 반영·검증
→ 원본 데이터 복원 → 새 토큰 발급 → 워밍업 → 한 RPS 측정 → 안정 확인
→ 다음 RPS (실패하면 중단)
```

### 한 번 준비할 것

- dev에 V1/V2 API를 배포하고, **실제 배포 SHA**를 기록한다. Actions가 checkout한 스크립트 SHA와 배포 SHA는 다를 수 있다.
- 앞 절차대로 기준일을 맞춘 SQL을 사람이 dev DB에 직접 실행한다. Actions는 SQL을 자동 적재하지 않는다.
- 기존 `loadtest-dev` runner에서 dev DB 읽기/쓰기와 Prometheus 조회가 가능한지 확인한다.
  Java 21은 workflow가 준비하며 Python 3·k6는 runner에 필요하다. Gradle은 저장소 Wrapper를 사용한다.
- GitHub **development Environment**에 아래 변수/비밀 값을 준비한다.

| 종류 | 이름 | 값 |
| --- | --- | --- |
| 기존 Variable | `DEV_BASE_URL` | `https://dev.daynomy.com` 등 dev API origin |
| 기존 Variable | `K6_PROMETHEUS_RW_SERVER_URL` | k6 지표를 보내는 remote-write endpoint |
| Variable | `PORTFOLIO_PROMETHEUS_URL` | 관리자가 자원을 조회할 Prometheus origin. 생략하면 JSON 기본값 `http://127.0.0.1:9090` |
| 선택 Variable | `PORTFOLIO_LOADTEST_STATE_DIR` | runner의 전용 비공개 **절대 경로**. 생략하면 `$HOME/.local/state/daynomy-portfolio-loadtest-496` |
| Secret | `PORTFOLIO_LOADTEST_DEV_ENV` | 아래 키를 실제 dev 값으로 채운 여러 줄 환경 파일 |

```dotenv
LOADTEST_ENVIRONMENT=dev
DB_URL=jdbc:postgresql://<dev-db-host>:5432/<database>
DB_USERNAME=<loadtest-user>
DB_PASSWORD=<secret>
JWT_SECRET=<dev-signing-secret>
JWT_ISSUER=daynomy
```

Secret은 채팅·params·저장소에 넣지 않는다. 측정 시 기존 토큰 Secret인 `K6_ACCOUNTS_JSON_GZ_BASE64`는 사용하지 않는다.
이 테스트는 기존 JWT 발급 로직으로 50+3계정의 토큰을 직접 준비한다. k6 자식 프로세스에는 서명키·환경 Secret을 전달하지 않는다.

### Run workflow 입력

변경이 반영된 브랜치를 선택한다. 사용 가능한 `script`는 다음 두 개다.

```text
backend/loadtest/transactions-v1.js
backend/loadtest/transactions-v2.js
```

**둘 중 하나만** script 입력란에 넣는다. 버전은 파일에서 고정하므로 `params`에 `VERSION`을 넣지 않는다.

**실행 순서: 설정 확인(plan) → SQL 직접 적재 → 원본 보관(capture) → V1 탐색 → 회복 확인 → V2 탐색 → 경계 주변 고정 RPS로 V1/V2 재검증.**

SQL 적재 직후 V1 script를 선택하고 다음 params로 원본을 한 번 보관한다. 부하는 보내지 않으며 기존 원본을 덮어쓰지 않는다.

```json
{
  "ACTION": "capture",
  "REFERENCE_DATE": "2026-10-16"
}
```

**1차 탐색:** V1 script에서 아래 params를 사용한다. 먼저 ACTION만 `plan`으로 바꿔 확인하고, 준비가 끝나면 `execute`로 실행한다.

```json
{
  "ACTION": "execute",
  "REFERENCE_DATE": "2026-10-16",
  "TRADE_MONTHS_AGO": 1,
  "RPS_STEPS": [0.1, 0.2, 0.5, 1, 2, 3],
  "STAGE_SECONDS": 120
}
```

각 RPS를 2분씩 측정하며 실패하면 더 높은 RPS는 실행하지 않는다. 서버 회복과 중단 표시를 확인한 뒤, 같은 params로 V2 script를 실행한다.
워밍업은 단계마다 별도 2분이므로, 6단계를 모두 진행하면 워밍업·측정만 최소 24분이고 준비·안정 대기 시간이 추가된다.

**2차 고정 RPS 재검증:** 예를 들어 V1이 1 RPS를 통과하고 2 RPS에서 실패했다면 1 / 1.5 / 2 RPS를 하나씩 검증한다.
아래는 1.5 RPS를 5분 측정하는 params다. 각 조건에서 V1 실행 → 회복 확인 → 같은 params로 V2 실행 순서로 비교한다.

```json
{
  "ACTION": "execute",
  "REFERENCE_DATE": "2026-10-16",
  "TRADE_MONTHS_AGO": 1,
  "RPS_STEPS": [1.5],
  "STAGE_SECONDS": 300
}
```

| 순서 | 조건 | 비교 |
| --- | --- | --- |
| 1 | 전체 RPS 목록, 각 120초 | V1 탐색 → 회복 확인 → V2 탐색 |
| 2 | `[1]`, 300초 | V1 → 회복 확인 → V2 |
| 3 | `[1.5]`, 300초 | V1 → 회복 확인 → V2 |
| 4 | `[2]`, 300초 | V1 → 회복 확인 → V2 |

2차에는 RPS를 **한 값만** 넣는다. 여러 값을 묶으면 중간 실패 이후 조건이 실행되지 않을 수 있다.
위 경계값은 예시이며 1차 결과에 맞춰 선택한다. 경계는 필요하면 반복 검증하고 순서를 V2 → V1로도 바꾼다.
V1/V2는 같은 원본·측정 시간·서버 사양·배포 상태에서 비교한다. 6개월 전 거래는 `TRADE_MONTHS_AGO`만 6으로 바꿔 별도로 진행한다.

**실패 후:** 목표 초과로 Actions가 실패 표시로 끝날 수 있다. 서버·DB의 남은 작업과 회복을 운영자가 확인한 뒤 다음 params를 실행한다.

```json
{
  "ACTION": "acknowledge-recovery",
  "REFERENCE_DATE": "2026-10-16"
}
```

중단 표시만 해제하며 자동 복원·재시험은 하지 않는다. 회복 확인을 생략하고 무조건 실행하는 용도로 사용하지 않는다.

- `ACTION=plan`: 통신 없는 조건 확인. ACTION을 생략해도 plan이다. 토큰·DB 접근 없이 실행 계획 결과물을 남긴다.
- `ACTION=capture`: SQL 적재 직후 원본 baseline을 **한 번** 보관한다. 기존 baseline은 덮어쓰지 않는다.
- `ACTION=execute`: baseline이 있어야 시작한다. 각 RPS마다 자동 복원·토큰 발급·워밍업·측정·감시를 수행한다.
- `ACTION=acknowledge-recovery`: **운영자가 서버/DB의 남은 작업을 확인한 후에만** 사용한다. 안정/대상 락을 재확인해 중단 표시만 해제한다.
  자동 복원·재측정은 하지 않는다.

먼저 `plan` → SQL 직접 적재 → `capture` → V1 `execute` → 회복 확인 → `script`만 `transactions-v2.js`로 바꿔 `execute` 순서다.
V1이 실패해 중단 표시가 남았으면 먼저 수동 점검·`acknowledge-recovery`를 거쳐야 V2를 시작할 수 있다.
기간 비교는 `TRADE_MONTHS_AGO`를 1 또는 6으로 바꾼다. 측정 시간을 늘릴 때는 `STAGE_SECONDS`를 300/600으로 바꾸고 필요한 RPS만 남긴다.
`RPS_STEPS`는 배열로 받는다. V1/V2·기간별 병렬 실행은 하지 않는다.

추가로 `WARMUP_SECONDS`, `VUS`, `REQUEST_TIMEOUT_SECONDS`, `TARGET_P95_MS`를 재정의할 수 있다.
생략하면 JSON 기본값(워밍업 120초·50 VU·타임아웃 10초·p95 목표 3초)을 쓴다.
알 수 없는 키·잘못된 값·dev 외 대상·50계정 중첩 위험은 거절한다. Actions job 제한 120분 안에 들어오도록 조건 예산도 검사한다.

### 원본 데이터와 중단 표시 보존

`baseline.json`, `execution.lock`, 실행 기록은 checkout 밖의 전용 경로에 남는다.
job 동안만 `backend/loadtest/.local`을 그 경로에 연결하고 종료 시 환경 파일·토큰과 연결을 제거한다.
checkout 정리·Actions 재실행으로 baseline이나 중단 표시가 지워지지 않는다. 강제 종료 때 남은 환경/토큰은 다음 정리 단계에서 제거하며
전용 경로는 권한 700, 비공개 파일은 600으로 보관한다.

같은 `loadtest-dev` 라벨은 원본 상태를 보관하는 **단일 runner**에 연결해야 한다. 새 runner에서 원본이 없으면 실행을 거절하며,
변경된 DB를 기준으로 자동 재캡처하지 않는다. 기준일·DB가 달라졌다면 기존 자료를 비공개로 보존하고 새 원본 준비 절차를 밟는다.
배포와 k6 workflow는 같은 `development-backend` concurrency 그룹으로 직렬화하며 진행 중 실행을 자동 취소하지 않는다.

### 결과 확인

Actions 실행의 **Summary**에서 단계별 RPS·성공 건수·p95·판정을 본다.
`portfolio-results-<run id>-<attempt>` artifact에는 해당 실행의 설정·단계 JSON·전체 결과·비교 CSV만 올린다.
토큰·환경 파일·baseline·원본 로그는 업로드하지 않는다. 실패한 실행도 가능한 결과를 남기며 후속 단계로 진행하지 않는다.

기존 [Prometheus remote write](https://grafana.com/docs/k6/latest/results-output/real-time/prometheus-remote-write/) 출력도 연결한다.
Grafana에서 `testid`(단계 실행 ID), `version`, `trade_date`, `rps`, `profile`로 k6 지표를 구분한다.
`phase=warmup/measure`로 HTTP 지표를 나누고, 본 측정 지연은 `transaction_duration`, 워밍업은 `warmup_duration`으로 분리한다.
remote-write의 p95는 전송된 시계열이고, 단계 전체 통과 판정은 artifact의 k6 요약 JSON을 기준으로 한다.
runner가 모니터링 EC2에 함께 있다면 k6가 Prometheus/Grafana 자원을 사용하는 점도 관찰한다.

이 변경은 실행 연결 코드다. 실제 runner 네트워크·Secret·V2 배포 여부는 실행 전에 확인해야 한다.

## 중단과 복구

- p95 3초 초과: 단계 종료 후 실패 처리, 다음 RPS 진행 금지.
- HTTP/응답 수량 검증 실패·타임아웃: k6 중단 요청. 요청 누락도 threshold로 중단하며 평가 지연은 있을 수 있음.
- CPU 85% 이상 또는 가용 메모리 15% 이하가 60초 지속: 실행 관리가 중단.
- 필수 지표 누락·오래된 지표·조회 실패·대상 down: 정상으로 간주하지 않고 중단.
- 정상 단계 사이에도 자원 기준 미만·DB 연결 대기 0이 30초 유지돼야 복원. 최대 180초 내 미충족 시 중단.
- 중단 시 `.local/execution.lock`으로 후속 실행·자동 복원을 차단. 서버 재시작·쿼리 취소는 하지 않음.

**클라이언트 종료가 서버 DB 작업 취소를 보장하지 않는다.** 지표 수집·전송 지연도 있어 인프라 보호를 보장하는 장치는 아니다.
운영자가 남은 요청·DB 작업·자원 상태를 확인한 다음에만 다음 명령으로 중단 표시를 해제한다.

```bash
python3 run-loadtest.py --acknowledge-recovery
```

자원 안정과 테스트 대상 락을 재확인하지만 자동 복원·재시험은 하지 않는다. 모든 백그라운드 작업 종료를 증명하지도 않는다.
다른 머신에서 동시에 실행하지 않는다.

## 결과와 검증

`.local/results/<실행ID>/`에 설정 사본·단계 JSON·로그·전체 `results.json`을 저장하고 `comparison.csv`에 비교 행을 누적한다.
`comparisonKey`가 같은 행은 fixture·요청 조건이 같고 버전만 다른 비교다. 배포 SHA·서버 사양·배치 실행 여부는 별도로 기록한다.

- `startedPerSecond`: 시작 건수/예정 시간. 불완전·조기 종료 실행의 실제 순간 RPS로 해석하지 않음.
- `successfulPerSecond`: 측정 구간 및 마지막 응답까지 drain을 포함한 성공 처리량.
- `latency`: 연결 설정을 포함한 POST 경과시간. CSRF·워밍업은 제외.
- `accounts`: 측정 계정 50명의 시작·성공·실패 건수. 미사용 계정도 0건 표시, 토큰 제외.
- `profiles`: light/standard/heavy별 시작·성공·실패 건수·사용 계정 수·본 측정 지연(p95/p99 등).
- `warmup`: 별도 워밍업 전체 및 그룹별 완료 건수·지연. 본 측정 p95·처리량에는 포함하지 않음.
- `version`, `tradeDate`, `rps`: 어느 버전·과거 날짜·요청률의 결과인지 구분하는 키. 그룹 표본이 없으면 지연은 빈 객체이며 0ms로 해석하지 않음.
- `resourceSamples`, `stopReasons`: 서버 자원 샘플과 중단 사유.
- 판정: `exploratory_pass`, `target_exceeded`, `incomplete`, `protection_stop`.

2분은 탐색용이다. 경계 구간은 5~10분 이상 반복 측정하고, 낮은 RPS는 표본이 부족하면 연장한다.
3 RPS 통과를 절대 최대 처리량으로 주장하지 않는다. 실제 예상 트래픽 근거는 별도로 정한다.
Grafana 일부 패널은 전체 API·최근 5분 기준이어서 2분 단계별 k6 숫자와 다를 수 있다.
요청당 SQL 수·SQL 시간은 별도 DB 통계/계측으로 확인해야 한다. 전체 스냅샷 동등성은 기존 V1/V2 백엔드 회귀 테스트로 검증한다.

```bash
python3 -B -m unittest discover -s tests -p 'test_*.py'
node --experimental-vm-modules --test tests/transactions.test.mjs
```

위 검증은 통신 없는 mock 테스트다. Java 도구·SQL·복원은 `org.grit.daynomy.loadtest`의 단위 테스트와 격리 PostgreSQL Testcontainers로 검사한다.
Backend CI에도 연결하며 실제 dev 부하테스트를 자동 수행하지 않는다.
