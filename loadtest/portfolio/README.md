# 포트폴리오 1,000명 / API 1~4 부하테스트

기본값은 **API 하나당 100 RPS, 본 측정 5분, 워밍업 1분**이다. 서버가 어디까지 버티는지 측정한다.
300·500 RPS는 다음 실행에서 명시적으로 선택하며 자동으로 부하를 올리지 않는다.
측정 회원 1,000명과 워밍업 3명은 `lt1000-20261029-1`~`1003`이다. 기존 53명용 도구·계정은 그대로 사용한다.

| 번호 | 파일 (`loadtest/portfolio/api/` 기준)     | API                                            |
| ---- | ----------------------------------- | ---------------------------------------------- |
| 1    | `get-portfolio.js`             | GET `/api/users/me/portfolio`                  |
| 2    | `calculate.js`       | POST `/api/portfolio/calculate`                |
| 3    | `transactions-v2.js` | POST `/api/v2/users/me/portfolio/transactions` |
| 4    | `performance.js`     | GET `/api/users/me/portfolio/performance`      |

## 1. dev 서버에서 원본 보관·토큰 발급

준비 도구는 dev DB와 실행 중인 dev 서버의 JWT 설정에 접근할 수 있는 머신에서 실행한다.
기존 `JwtTokenProvider`로 USER access token을 발급하므로 Google 로그인 과정 없이 정상 인증 요청을 보낼 수 있다.
API의 JWT·회원·CSRF 검증은 그대로 수행한다. 도구는 Spring 서버나 스케줄러를 시작하지 않는다.

서버의 기존 dev 환경 파일을 `--env-file`로 지정한다. 파일은 `KEY=value` 형식이며
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`이 필요하다. `JWT_ISSUER` 기본값은 `daynomy`다.
아래 `/absolute/path/to/dev.env`를 실제 **dev용** 파일 경로로 바꾼다. 도구는 DB 이름 `daynomy_dev`를 검사한다.

프로젝트 체크아웃의 `backend/`에서 실행:

```bash
LOADTEST_ENVIRONMENT=dev ./gradlew -q portfolio1000Loadtest --args="capture --env-file /absolute/path/to/dev.env --namespace lt1000-20261029 --base-url https://dev.daynomy.com"
LOADTEST_ENVIRONMENT=dev ./gradlew -q portfolio1000Loadtest --args="tokens --env-file /absolute/path/to/dev.env --namespace lt1000-20261029 --base-url https://dev.daynomy.com --token-seconds 7200"
```

`capture`는 첫 시험 전에 한 번만 실행한다. 원본이 이미 있거나 거래·보유 건수가 시드와 다르면 거절한다.
이력·스냅샷은 테이블별 JSONL gzip으로 스트리밍하여 원본 전체를 힙에 올리지 않는다.

생성 파일:

- `loadtest/.local/lt1000-20261029-baseline/`: 원본 행·대상·스키마·참조 데이터 해시. 이후 복원에 필요하다.
- `loadtest/.local/accounts-1000.json`: 1,003명의 JWT와 실제 보유·거래 입력·조회 기간. 파일 권한 `0600`.

`tokens`는 현재 포트폴리오가 원본과 같은지 검사한다. 토큰은 기본 2시간이고 계정 파일의 평가일은 발급한 날의 서울 날짜다.
만료가 임박하거나 날짜가 바뀌면 복원 후 다시 발급한다. 미국 거래 대상의 최신 종가에 대응하는 DB 환율도 필요하다.

### 기존 독립 tool.jar 방식으로 실행하는 경우

앱 배포 없이 준비용 클래스 두 개를 기존 서버의 `runtime/classes:runtime/lib/*`와 함께 실행할 수 있다.
현재 체크아웃의 `backend/`에서 빌드한다.

```bash
./gradlew testClasses
mkdir -p build/loadtest-1000
jar --create --file build/loadtest-1000/tool-1000.jar \
  -C build/classes/java/test org/grit/daynomy/loadtest/Portfolio1000LoadtestTool.class \
  -C build/classes/java/test org/grit/daynomy/loadtest/PortfolioLoadtestTool.class
```

`tool-1000.jar`를 기존 준비 도구의 `runtime/`가 있는 서버 폴더로 복사한다. Java 21과 기존 앱 의존 라이브러리가 필요하다.
그 폴더에서 실행하며 환경 파일 경로를 실제 dev 경로로 바꾼다.

```bash
LOADTEST_ENVIRONMENT=dev java -Xmx512m \
  -cp 'tool-1000.jar:runtime/classes:runtime/lib/*' \
  org.grit.daynomy.loadtest.Portfolio1000LoadtestTool capture \
  --env-file /absolute/path/to/dev.env --namespace lt1000-20261029 --base-url https://dev.daynomy.com

LOADTEST_ENVIRONMENT=dev java -Xmx512m \
  -cp 'tool-1000.jar:runtime/classes:runtime/lib/*' \
  org.grit.daynomy.loadtest.Portfolio1000LoadtestTool tokens \
  --env-file /absolute/path/to/dev.env --namespace lt1000-20261029 --base-url https://dev.daynomy.com \
  --token-seconds 7200
```

출력 위치는 실행 폴더 아래의 `loadtest/.local/`이다.
DB·JWT 설정은 준비 머신에서 사용하고, 부하 발생기에는 발급된 계정 파일만 전달한다.

## 2. dev 서버에서 분할 Secret 등록

Python 3와 GitHub CLI(`gh`)가 필요하다. 이 브랜치를 checkout한 dev 서버에서 실행한다.
GitHub에 인증한 계정은 저장소의 development Environment Secret 수정 권한이 있어야 한다.

```bash
gh auth status
# 로그인하지 않았다면 한 번 실행:
gh auth login --hostname github.com --web
```

토큰 발급 도구를 실행한 `backend/` 폴더에서 다음 명령을 실행한다.
독립 JAR로 발급했다면 마지막 인자를 실제 `accounts-1000.json` 절대 경로로 바꾼다.

```bash
python3 ../loadtest/portfolio/register-secrets.py \
  loadtest/.local/accounts-1000.json \
  --repo woowacourse-teams/2026-DAYNOMY \
  --environment development
```

등록 도구가 1,003명인지 검사한 뒤 gzip → Base64 변환하고 **40,000바이트씩 최대 16개**로 나눈다.
Secret 값은 명령 인자·로그에 출력하지 않고 `gh secret set` 표준입력으로 전달한다.
등록 전에 기존 분석 테스트용 계정 원본은 별도로 보관한다. 첫 Secret은 같은 이름을 공유하므로 새 데이터로 바뀐다.

- 첫 조각: `K6_ACCOUNTS_JSON_GZ_BASE64`
- 이후 조각: `K6_ACCOUNTS_JSON_GZ_BASE64_PART_02` ~ `_PART_16` 중 필요한 개수
- 완료 정보: `K6_ACCOUNTS_JSON_GZ_BASE64_MANIFEST` (조각 수와 전체 SHA-256)

마지막 완료 정보까지 등록되어 `Done.`이 출력되어야 실행한다. 중간 실패 시 같은 명령을 다시 실행한다.
Actions가 순서대로 합친 뒤 SHA-256을 확인하고 압축을 푼다. 누락·이전 조각 혼합은 k6 실행 전에 실패한다.
이전 실행보다 조각 수가 줄어도 남은 조각은 사용하지 않는다. 매번 전체 등록 명령을 다시 실행한다.

[GitHub Secret 하나의 48KB 제한](https://docs.github.com/en/actions/reference/security/secrets)보다 작게 나누므로 전체 파일이 48KB를 넘어도 등록할 수 있다.
최대 용량은 Base64 기준 640,000바이트이며 초과하면 업로드 전에 거절한다. 최대 17개의 Environment Secret이 필요하다.
runner에는 사전 파일 배치가 없다. Actions가 권한 `0600` 임시 계정 파일을 만들고 종료 시 삭제한다.
기존 분석 테스트도 동일한 조각 복원 과정을 사용하며, 완료 정보가 없는 기존 단일 Secret도 지원한다.

## 3. 우선 100 RPS로 네 API를 개별 실행

원본을 유지하기 위해 **1 → 2 → 4 → 3** 순서로 실행한다. 3번은 100 RPS·5분에 30,000건의 거래를 예정한다.
각 측정 반복은 대상 API에 보내는 HTTP 요청 한 개다. CSRF 발급은 준비 단계에 포함한다.
워밍업은 별도 3명, 본 측정은 1,000명을 전체 요청 순서대로 순환한다.

### GitHub Actions

기존 **k6 development smoke test** workflow를 사용한다.
Run workflow의 브랜치에서 `feature/BE/#752`를 선택한다. 해당 브랜치를 먼저 push해야 한다.
development 환경이 `dev` 브랜치만 허용한다면 `dev`에 merge한 뒤 `dev`를 선택한다.
기존 `DEV_BASE_URL`, `K6_PROMETHEUS_RW_SERVER_URL`과 위 `K6_ACCOUNTS_JSON_GZ_BASE64` Secret을 사용한다.
기존 53명용 `PORTFOLIO_ACCOUNTS_JSON_GZ_BASE64` Secret은 변경하지 않는다.
`script`에는 아래 파일을 하나씩 입력한다.

```text
loadtest/portfolio/api/get-portfolio.js
loadtest/portfolio/api/calculate.js
loadtest/portfolio/api/performance.js
loadtest/portfolio/api/transactions-v2.js
```

네 파일 모두 `params`는 아래 값을 사용한다. `RUN`을 생략하면 Actions 실행 ID를 사용한다.

```json
{ "RPS": 100, "DURATION": "5m", "WARMUP_DURATION": "1m" }
```

`ACCOUNTS_FILE`과 `BASE_URL`은 workflow가 설정한다.
계정 파일은 결과 Artifact에 포함하지 않으며 토큰·응답 본문을 로그로 출력하지 않는다.

| 설정                      | 기본값 / 의미                                                       |
| ------------------------- | ------------------------------------------------------------------- |
| `RPS`                     | 100. 이후 300·500을 명시적으로 선택                                 |
| `DURATION`                | 5m 본 측정. 워밍업·사후 검증 시간은 별도                            |
| `WARMUP_DURATION`         | 1m                                                                  |
| `VUS`                     | `max(100, RPS × 2)`. 100 RPS는 200 VU. 실행 슬롯이며 회원 수와 다름 |
| `REQUEST_TIMEOUT_SECONDS` | 10초                                                                |
| `TRADE_MONTHS_AGO`        | 3번: 발급일 기준 1개월 전. `0`(발급일)·`1`·`6`을 따로 실행          |
| `PERFORMANCE_MONTHS`      | 4번: 발급일 기준 1개월. `1`·`6`·`12`                                |
| `RUN`                     | 결과 파일 구분. 기본값은 Actions 실행 ID                    |

`params/100rps.json`, `params/300rps.json`, `params/500rps.json`은 수동 실행용 입력 예시다.
반복 측정·거래일 변경·다음 RPS 전에 원본을 복원한다. 300·500 RPS 실행은 수동으로 선택한다.

## 4. 결과 판정과 복원

- 본 측정 p95 ≤ 300ms, p99 ≤ 500ms, 응답/검증 실패율 < 1%, 데이터 불일치 0건을 확인한다.
- 예정 요청 수와 실제 시작 수를 대조하고 `dropped_iterations=0`을 요구한다.
- 지연 시간은 연결 설정을 포함한 요청 완료까지의 경과 시간이다. 프로필·국내/미국/혼합 그룹별로도 집계한다.
- 3번 종료 후 1,000명 모두의 보유 수량 증가·해당 거래일 이력 건수를 HTTP로 확인한다. 금액·스냅샷의 독립 재계산 검증은 포함하지 않는다.
- HTTP 오류·타임아웃·요청 누락이 있으면 3번 정합성은 `inconclusive`다. 클라이언트 타임아웃 뒤에도 서버 작업이 남을 수 있다.
- 결과 파일은 `result-portfolio-1000-*.json`이며 토큰·회원 ID·응답 본문이 포함되지 않는다.

3번 이후에는 서버·DB에 남은 작업이 끝난 것을 확인한 다음 준비 머신에서 복원한다.
해당 namespace의 보유·이력·스냅샷을 원본으로 돌리며 회원 ID와 시퀀스는 유지한다.

```bash
LOADTEST_ENVIRONMENT=dev ./gradlew -q portfolio1000Loadtest --args="restore --env-file /absolute/path/to/dev.env --namespace lt1000-20261029 --base-url https://dev.daynomy.com"
```

독립 JAR 방식은 앞의 Java 명령에서 `capture`나 `tokens` 대신 `restore`를 사용한다.
종가·환율·스키마·회원 정보가 바뀌면 복원을 거절하므로 측정 조건을 확인한 뒤 진행한다.
실패한 결과로 원본을 덮어쓰지 않는다.

## CI 검증

Backend CI에서 스크립트 계약·입력 검증과 토큰 발급 도구 검증을 실행한다.
실제 dev 부하 발생은 위 수동 Actions 실행에서만 수행한다.
