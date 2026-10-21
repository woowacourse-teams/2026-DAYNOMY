# 과거 거래 등록 V1/V2 부하테스트 (#496)

기존 k6 workflow처럼 **계정 파일 준비 → Actions에서 script·params 입력 → k6 실행 → Grafana·결과 확인** 순서다.
Actions는 DB 복원이나 토큰 발급을 하지 않는다. `run-loadtest.py`, 설정 JSON, `ACTION` 입력은 제거했다.
**Run workflow를 누르면 실제 dev API에 요청한다.** 계획만 확인하는 Actions 모드는 없다.

## 파일 역할

| 파일 | 역할 |
| --- | --- |
| `transactions-v1.js`, `transactions-v2.js` | 버전별 k6 실행 파일 |
| `support/scenario.js` | CSRF 준비·워밍업·과거 매수 등록 |
| `support/prepare.js` | params 해석·53명 계정·토큰 검증 |
| `support/report.js` | 전체·그룹별·계정별 지표와 결과 JSON |
| `sql/seed-portfolios.sql` | 초기 데이터 직접 적재 |
| `tests/transactions.test.mjs` | 통신 없는 k6 시나리오 회귀 검증 |

토큰·복원 준비 도구는 `src/test/java/org/grit/daynomy/loadtest/PortfolioLoadtestTool.java`에 있다.
필요할 때 `portfolioLoadtest` Gradle 작업으로 별도 실행한다. 앱 JAR과 Actions 실행 경로에 포함되지 않는다.

## 이미 준비한 데이터

기준일은 `2026-10-16`, 회원 prefix는 `lt496-20261016-`이다. 이미 53명이 있다면 SQL을 다시 실행하지 않는다.

| 그룹 | 측정 회원 | 회원당 종목 | 회원당 BUY/SELL |
| --- | ---: | ---: | ---: |
| light | 20명 | 5개 | 50건 |
| standard | 20명 | 15개 | 200건 |
| heavy | 10명 | 30개 | 800건 |
| 워밍업 | 그룹별 1명, 총 3명 | 5/15/30개 | 50/200/800건 |

측정 1~50번은 light/standard/light/standard/heavy를 반복한다. 51~53번은 워밍업 회원이다.
53명 합계 보유 750행, BUY/SELL 14,050건, 초기 잔고 포함 이력 14,800건이다.
초기 적재에는 실제 종가 1년 및 직전 거래일이 충분한 주식 30개 이상이 필요하다. SQL은 가격을 만들지 않는다.
새 기준일 적재 시에만 SQL의 UPDATE 날짜를 바꾸고 BEGIN부터 COMMIT까지 같은 연결에서 전체 실행한다.

## 실행 전에 데이터·토큰 준비

준비 작업은 **dev DB와 기존 dev 환경정보에 접근 가능한 머신**에서 한다. 부하 발생 runner에 DB 비밀번호·JWT 서명키를 전달하지 않는다.
아래는 `backend/`에서 실행하는 예시다. `/absolute/path/to/dev.env`를 실제 기존 환경 파일 경로로 바꾼다.
파일은 `KEY=value` 형식이며 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`이 필요하다.
이미 현재 프로세스 환경에 해당 값이 있으면 `--env-file`을 생략할 수 있다. 서버 서비스의 환경이 다른 쉘에 자동 전달되지는 않는다.
`JWT_SECRET`은 실제 dev 서명키, issuer는 기본 `daynomy`다. 값은 Git·params·로그에 넣지 않는다.

### 1. 초기 원본 보관 — 최초 한 번

```bash
LOADTEST_ENVIRONMENT=dev ./gradlew -q portfolioLoadtest --args="capture --env-file /absolute/path/to/dev.env --namespace lt496-20261016 --base-url https://dev.daynomy.com"
```

SQL 적재 직후, **첫 측정 전에** 53명의 초기 상태를 `loadtest/.local/baseline.json`에 보관한다. 이미 있으면 덮어쓰지 않는다.
이 파일은 다음 준비에도 필요하므로 같은 머신·체크아웃에서 비공개로 보관한다. 측정 후 상태를 새 원본으로 덮어쓰지 않는다.

### 2. 매 실행 전 원본 복원·토큰 발급

이전 요청과 DB 작업이 끝났고 서버가 회복됐는지 확인한 뒤 다음 두 명령을 실행한다.

```bash
LOADTEST_ENVIRONMENT=dev ./gradlew -q portfolioLoadtest --args="restore --env-file /absolute/path/to/dev.env --namespace lt496-20261016 --base-url https://dev.daynomy.com"
LOADTEST_ENVIRONMENT=dev ./gradlew -q portfolioLoadtest --args="tokens --env-file /absolute/path/to/dev.env --namespace lt496-20261016 --base-url https://dev.daynomy.com --token-seconds 3600"
```

복원은 원본 테스트 회원의 보유·이력·스냅샷만 대상으로 한다. 회원·가격·다른 사용자·시퀀스는 변경하지 않는다.
회원/가격 문맥이 바뀌거나 잠금이 남아 있으면 거절한다. 토큰 발급은 DB가 원본과 같은지도 검사한다.
결과는 `loadtest/.local/accounts.json` 하나다. 측정 50명·워밍업 3명의 회원 ID·토큰·거래 대상·초기 수량이 들어 있다.
53번 로그인할 필요 없이 기존 `JwtTokenProvider`로 각 회원의 토큰을 만든다. Refresh Token은 파일이나 DB에 저장하지 않는다.

기존 분석 테스트의 계정·토큰은 이번에 적재한 53명과 다르므로 그대로 쓰지 않는다.
수동으로 파일을 준비해도 같은 53명·필드가 필요하고, 만료되면 다시 발급해야 한다.

### 복원 후 데이터 비교가 실패할 때

준비 도구는 복원 후 또는 토큰 발급 전 DB가 원본과 다르면 테이블·컬럼·차이 유형·건수를 표준 오류로 출력한다.
실제 값, 회원·행 ID, 토큰, 접속 정보는 출력하지 않는다. 다음은 출력 형식의 예시이며 실제 서버 진단 결과는 아니다.

```text
Baseline mismatch summary (values and row identifiers omitted):
table=portfolio_holding_histories column=unit_price kind=COLUMN_MISSING_IN_BASELINE count=14800
```

| kind | 의미 |
| --- | --- |
| `COLUMN_MISSING_IN_BASELINE` / `COLUMN_MISSING_IN_DB` | 원본 / DB 행에 컬럼이 없음. 원본에만 있는 컬럼 이름은 `<unknown_column>`으로 가림 |
| `NULL_MISMATCH` | 한쪽 값만 null |
| `NUMERIC_VALUE` / `TYPE_MISMATCH` / `VALUE_MISMATCH` | 숫자 값 / JSON 자료형 / 그 외 값이 다름 |
| `TIMESTAMP_REPRESENTATION` | 동일 시각을 나타내지만 타임존 등 문자열 표기가 다름 |
| `EXTRA_ROWS_IN_DB` / `MISSING_ROWS_IN_DB` | 원본과 DB 행 수 차이. 이 경우 해당 테이블의 컬럼 비교는 생략 |
| `ROW_ID_MISMATCH` / `STRUCTURE` | 같은 순서의 행 ID / JSON 구조가 다름 |

`1`과 `1.000000`은 기존처럼 같은 숫자로 비교한다. 시간 표기 차이 등 다른 불일치는 자동 보정하지 않으며,
복원 검증이 실패하면 커밋하지 않고 연결 종료 시 롤백한다. 원본 파일도 수정하지 않는다.
원본에 현재 스키마의 필수 컬럼 값이 없어 SQL 삽입 자체가 실패하면 이 비교 단계에 도달하지 않으므로 별도 SQL 진단이 필요하다.

#### 서버에서 독립 `tool.jar`를 사용 중인 경우

소스만 수정해도 기존 서버 JAR은 바뀌지 않는다. 수정한 체크아웃의 `backend/`에서 다음과 같이 도구 클래스만 묶는다.
앱 JAR과 의존 라이브러리는 포함하지 않으며, 기존 `runtime/classes:runtime/lib/*`와 함께 사용한다.

```bash
./gradlew testClasses
mkdir -p build/loadtest-tool
jar --create --file build/loadtest-tool/tool.jar \
  -C build/classes/java/test org/grit/daynomy/loadtest/PortfolioLoadtestTool.class
```

`build/loadtest-tool/tool.jar`를 dev 서버 `/home/ubuntu/loadtest-496/tool-next.jar`로 전송한 뒤,
서버에서 기존 파일을 보관하고 교체한다. 아래는 현재 스키마용으로 별도 생성해 둔 원본을 지정한 예시다.

```bash
cd /home/ubuntu/loadtest-496
cp -p tool.jar "tool.before-diagnostics.$(date +%Y%m%d%H%M%S).jar"
chmod 600 tool-next.jar
mv tool-next.jar tool.jar
LOADTEST_ENVIRONMENT=dev java \
  -Duser.timezone=Asia/Seoul -Xms32m -Xmx512m -XX:+UseSerialGC \
  -cp 'tool.jar:runtime/classes:runtime/lib/*' \
  org.grit.daynomy.loadtest.PortfolioLoadtestTool restore \
  --env-file /home/ubuntu/backend/daynomy.env \
  --namespace lt496-20261016 --base-url https://dev.daynomy.com \
  --snapshot baseline-current-schema-20261021.json
```

복원이 성공한 경우에만 토큰과 Actions 전달 파일을 갱신한다. 실패하면 위 진단 요약으로 원인을 먼저 확인한다.

```bash
umask 077
LOADTEST_ENVIRONMENT=dev java \
  -Duser.timezone=Asia/Seoul -Xms32m -Xmx512m -XX:+UseSerialGC \
  -cp 'tool.jar:runtime/classes:runtime/lib/*' \
  org.grit.daynomy.loadtest.PortfolioLoadtestTool tokens \
  --env-file /home/ubuntu/backend/daynomy.env \
  --namespace lt496-20261016 --base-url https://dev.daynomy.com \
  --snapshot baseline-current-schema-20261021.json --token-seconds 3600 && \
gzip -c loadtest/.local/accounts.json | base64 > accounts.secret
```

### 3. 토큰 파일을 Actions에 전달

기존 분석 테스트와 같은 gzip+Base64 전달 방식을 사용하되, 분석용 Secret은 덮어쓰지 않는다.

```bash
umask 077
gzip -c loadtest/.local/accounts.json | base64 > loadtest/.local/accounts.secret
```

GitHub `Settings → Environments → development → Environment secrets`에서
**`PORTFOLIO_ACCOUNTS_JSON_GZ_BASE64`**에 `accounts.secret` 파일 내용을 넣는다.
이 파일도 유효한 토큰을 담고 있으므로 Git·채팅에 올리지 않는다. Actions 종료 시 runner에 풀어 둔 계정 파일은 제거한다.
토큰이 충분히 남아 있고 매번 원본으로 복원했다면 같은 Secret을 재사용할 수 있다.

별도 `PORTFOLIO_LOADTEST_DEV_ENV` Secret이나 DB·JWT 설정을 Actions에 추가할 필요는 없다.
기존 Variables `DEV_BASE_URL`, `K6_PROMETHEUS_RW_SERVER_URL`은 그대로 사용한다.

## Actions 입력

기존 **k6 development smoke test** workflow를 사용한다. 현재 development 환경은 `dev` 브랜치만 허용하므로,
변경을 dev에 반영한 뒤 실행한다. workflow 실행 브랜치와 실제 API 배포 버전은 별개이며 V1/V2 배포 SHA를 확인해 기록한다.

script에는 둘 중 하나만 넣는다.

```text
backend/loadtest/transactions-v1.js
backend/loadtest/transactions-v2.js
```

첫 요청률 예시:

```json
{
  "REFERENCE_DATE": "2026-10-16",
  "TRADE_MONTHS_AGO": 1,
  "RPS": 0.1,
  "DURATION": "2m",
  "VUS": 50,
  "RUN": "explore-01"
}
```

| params | 의미 / 기본값 |
| --- | --- |
| `REFERENCE_DATE` | 필수. SQL과 원본을 보관한 기준일 |
| `TRADE_MONTHS_AGO` | 기준일로부터 과거 개월 수. 기본 1, 추가 비교는 6 |
| `RPS` | 측정 POST 시작 요청률. 기본 0.1 |
| `DURATION` | 본 측정 시간. 기본 `2m`, 재검증 `5m`/`10m` (10~600초) |
| `WARMUP_DURATION` | 별도 3명 워밍업. 기본 `2m` (1~300초) |
| `VUS` | k6 실행 슬롯. 기본 50, 자동으로 늘리지 않음 |
| `REQUEST_TIMEOUT_SECONDS` | 요청 타임아웃. 기본 10초 |
| `TARGET_P95_MS` | 본 측정 p95 목표. 기본 3000ms |
| `RUN` | 결과 구분용 이름. 생략하면 Actions 실행 ID |

`ACTION`, `RPS_STEPS`, `VERSION`은 받지 않는다. 버전은 script 파일로 정한다.
RPS는 분당 정수·예정 요청 수도 정수여야 하고, 한 실행 최대 5,000건이다.
계정 중첩을 피하기 위해 `RPS × (타임아웃+1초) < 50`을 검사한다. 50 VU가 부족하면 `dropped_iterations`로 드러나며 목표 요청률을 달성한 결과로 인정하지 않는다.
계정은 빈 VU 번호와 무관하게 전역 요청 순번으로 순환한다. V1/V2는 같은 회원·요청 순서를 사용한다.

## 1차 탐색 → 2차 재검증

**한 번에 한 버전·한 날짜·한 RPS만 실행한다.** 매번 같은 초기 데이터로 복원한 뒤 실행하며 병렬로 돌리지 않는다.

1. V1에서 `0.1 → 0.2 → 0.5 → 1 → 2 → 3 RPS`를 하나씩 2분 측정한다. 워밍업 2분은 별도다.
2. 응답시간·오류·서버 자원을 확인하고 통과했을 때만 다음 RPS로 올린다. 실패하면 올리지 않는다.
3. 서버 회복·원본 복원 후 같은 조건으로 V2를 측정한다.
4. 마지막 통과/첫 실패 구간에서 RPS 한 값을 5~10분 재검증한다. 각 조건에서 V1 → 회복·복원 → V2로 비교하고 필요하면 순서를 바꿔 반복한다.

예를 들어 경계가 1~2 RPS일 때 1.5 RPS 재검증:

```json
{
  "REFERENCE_DATE": "2026-10-16",
  "TRADE_MONTHS_AGO": 1,
  "RPS": 1.5,
  "DURATION": "5m",
  "VUS": 50,
  "RUN": "boundary-01"
}
```

6개월 전은 `TRADE_MONTHS_AGO`만 6으로 바꾸고 별도로 진행한다. 위 경계값은 예시이며 실제 1차 결과로 정한다.
2분은 탐색용이고 낮은 RPS의 p95는 표본이 적다. 3 RPS 통과를 최대 처리량이라고 주장하지 않는다.

## 중단·결과 확인

- HTTP 오류·타임아웃·예상과 다른 보유 수량: k6 중단 요청. 자동 재시도하지 않는다.
- 요청 누락: k6 threshold로 중단. p95 3초 초과는 실행 종료 시 실패로 판정한다.
- CPU·메모리는 Grafana에서 관찰한다. CPU 85% 이상 또는 가용 메모리 15% 이하가 60초 지속되면 운영자가 Actions를 취소하고 확인한다. **자동 자원 감시·중단·복원은 없다.**
- 클라이언트 중단이 이미 실행 중인 서버·DB 작업을 취소하지는 않는다. 남은 작업과 회복을 확인한 후 수동 복원한다.

Actions **Summary**와 로그에서 버전·거래일·RPS·성공 건수·p95·판정을 본다.
측정 거래 POST는 `RPS × 측정 시간(초)`건까지만 전송한다. 종료 경계의 추가 iteration은 POST 없이 끝나므로, k6 전체 iteration 수와 측정 거래 수는 다를 수 있다. 예정 거래가 누락되면 계속 실패로 판정한다.
`k6-results-<run id>-<attempt>` artifact에 `result-transactions-*.json`을 보관한다. 계정·토큰·baseline은 업로드하지 않는다.
결과 JSON에는 전체 p95/p99, light/standard/heavy별 지연·성공·실패, 50명 각각의 요청 건수(미사용 0건 포함), 별도 워밍업 지표가 들어 있다.
Grafana는 `testid`, `version`, `trade_date`, `rps`, `profile`, `phase`로 구분한다.

요청당 SQL 수·SQL 시간은 별도 DB 계측으로 확인한다. 배포 SHA·서버 사양·배치 실행 여부도 함께 기록한다.
전체 스냅샷의 V1/V2 동등성은 기존 백엔드 회귀 테스트로 검증한다.

## 코드 검증 — dev 요청 없음

```bash
node --experimental-vm-modules --test loadtest/tests/transactions.test.mjs
./gradlew spotlessCheck test
```

Node 테스트는 HTTP mock, Java 도구·복원 테스트는 격리 PostgreSQL Testcontainers를 사용한다. Backend CI에 연결하며 실제 dev 부하는 CI에서 자동 수행하지 않는다.
