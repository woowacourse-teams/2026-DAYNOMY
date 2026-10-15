# 포트폴리오 거래 등록 부하 테스트 (#496)

기존 `POST /api/users/me/portfolio/transactions`의 매수·매도 등록을 측정한다.
V1/V2 비교를 위한 합성 데이터 준비·초기화·측정 절차다.
V1 비즈니스 코드, 프론트엔드, 운영 데이터는 변경하지 않는다.

실행 파일을 `transactions-v1.js`와 `transactions-v2.js`로 나눈다. 각 파일은
API 버전을 고정하며, 공통 `transaction-scenario.js`의 데이터·부하·요청·검증
로직을 사용한다. 한 번의 실행에서 한 버전만 요청한다. 다른 버전으로 대체하지 않는다.

| 버전 | 거래 등록 경로 | 상태 |
| --- | --- | --- |
| `v1` | `/api/users/me/portfolio/transactions` | 구현·스모크 검증 완료 |
| `v2` | `/api/v2/users/me/portfolio/transactions` | 일괄 조회 API 구현·PostgreSQL 인수 테스트 검증 |

## 시나리오와 비교 순서

| 시나리오 | 거래일 | 확인할 비용 |
| --- | --- | --- |
| `today` | 한국 시간 오늘 | 거래 저장·전체 이력 재생, 날짜 목록 조회 |
| `one-month` | 1개월 전 같은 날짜 | 약 1개월의 스냅샷 갱신 |
| `six-months` | 6개월 전 같은 날짜 | 약 6개월의 반복 조회·재계산·저장 |

1. 기본 데이터: 회원 50명, 회원당 보유 20종목·매수 거래 1,000건·6개월치 스냅샷.
2. 사용자 1명으로 각 기간의 BUY/SELL을 분리해 측정한다.
3. 같은 데이터에서 서로 다른 회원 5·10·20·50명으로 부하를 늘린다.
4. 보유 20종목·사용자 수·기간을 고정하고 거래 이력만 100·1,000·10,000건으로 바꾼다.
5. V2에서도 같은 초기 데이터, 요청 순서·건수, 서버 사양·설정으로 반복한다.

한 VU(가상 사용자)는 한 회원에 대응하며, 요청을 순차 실행한다. 각 VU의
요청 횟수를 고정한 `per-vu-iterations`를 사용해 버전별 데이터 증가량을 맞춘다.
따라서 사용자 수는 동시 처리 작업자의 수이며 RPS와 같지 않다. 이 시험은
고정 RPS의 한계 측정이나 장시간 안정성 시험을 대신하지 않는다.

최초 기본값은 VU당 5건이다. 안정적인 p95를 얻으려면 스모크 검증 후 횟수를
늘려 충분한 표본을 확보한다. VU당 최대 500건, POST 타임아웃 180초,
전체 시험 최대 30분이며 실제 완료 건수를 확인한다. 재시도는 하지 않는다.

## 합성 데이터 규칙

- 모든 회원은 같은 포트폴리오 데이터를 가진다. 개인 정보와 실제 종목 데이터는 사용하지 않는다.
- 회원의 보유 종목마다 최초 잔고 1,000주를 6개월 전 날짜에 기록한다.
- `--transactions`는 BUY 거래 수이며, 최초 잔고 이력은 별도다. 20종목·1,000거래는 회원당 이력 1,020행이다.
- BUY 1주를 기간·종목에 걸쳐 결정적으로 배분한다. 종목별 매입가는 고정이고 수수료·세금은 0이다.
- 가격은 평일마다 1원씩 증가하는 합성 종가다. 첫 스냅샷의 일일 손익을 계산하도록 직전 평일 가격도 준비한다.
- 국내 공휴일을 반영하지 않는 합성 평일 달력이다. 거래일 제약, 누락 가격, 반올림 경계는 별도 정확성 테스트 대상이다.
- 초기 스냅샷은 기존 계산 규칙에 맞춰 생성한다. 과거 매도도 최초 잔고 이후이므로 수량 부족 없이 등록 가능하다.
- 측정 요청은 종목 1의 BUY 또는 SELL 1주, 고정 매입가, 수수료·세금 0이다. 응답 수량과 손익을 검증한다.
- `fixtureHash`는 초기 SQL의 SHA-256이다. 한국 날짜가 바뀌면 데이터·토큰을 다시 준비한다.

## 로컬 환경 준비

필수 도구는 Python 3.9 이상, Docker Compose v2, Java 21, k6다.
아래 명령은 저장소 루트에서 실행한다.

```bash
python3 backend/loadtest/manage.py prepare
python3 backend/loadtest/manage.py up
python3 backend/loadtest/manage.py serve
```

`serve`는 포그라운드에서 실행한다. 별도 터미널에서 다음을 실행한다.
서버가 시작되어 Flyway 마이그레이션을 완료한 다음 seed를 넣는다.

```bash
python3 backend/loadtest/manage.py seed
python3 backend/loadtest/manage.py verify
```

- DB: `127.0.0.1:55496/daynomy_loadtest`, API: `127.0.0.1:8496`, Actuator: `127.0.0.1:8497`.
- 전용 Compose 프로젝트 `daynomy-loadtest-496`와 볼륨을 사용한다.
- `seed`는 **전용 DB의 모든 회원·종목 및 연결된 데이터**를 초기화한다. DB 이름과 사용자 이름을 SQL 실행 전에 검사한다.
- 실행 도구는 DB·서버 주소를 고정하며 dev/prod DB에 연결하는 옵션을 제공하지 않는다.
- `serve`는 기존 `backend/.env` import를 끄고 별도 loadtest 설정으로 실행한다.
- 외부 수집·뉴스 생성·리그 계산·포트폴리오 스냅샷 예약 작업은 끈다.
- 실제 인증 필터와 CSRF 검증을 사용한다. 익명 `/api/auth/csrf` 조회 뒤 회원별 JWT 쿠키를 사용한다.
- 무작위 로컬 전용 JWT 키·DB 비밀번호·24시간짜리 토큰은 `.local/`에만 저장한다. 디렉터리 권한은 700, 파일 권한은 600이며 Git에서 제외한다.
- `.local/runtime.env`와 `.local/fixture.json`은 공유하거나 커밋하지 않는다. k6 HTTP 디버그 출력도 켜지 않는다.

데이터 규모 변경 시 키와 DB 비밀번호는 유지한다.

```bash
python3 backend/loadtest/manage.py prepare --members 50 --holdings 20 --transactions 10000
python3 backend/loadtest/manage.py seed
```

## 실행과 초기화

먼저 워밍업으로 서버 경로를 실행하고, seed로 워밍업 거래를 제거한다.
그 뒤 본 측정과 전체 과거 스냅샷 검증을 수행한다.

```bash
python3 backend/loadtest/manage.py run --scenario six-months --side BUY --vus 1 --iterations 2 --label warmup-six-months-buy
python3 backend/loadtest/manage.py seed
python3 backend/loadtest/manage.py run --scenario six-months --side BUY --vus 1 --iterations 20 --label v1-six-months-buy-vu1-r1
python3 backend/loadtest/manage.py verify-result --label v1-six-months-buy-vu1-r1
```

매 측정 전에 seed를 복원한다. 예를 들어 다음 시험은:

```bash
python3 backend/loadtest/manage.py seed
python3 backend/loadtest/manage.py run --scenario one-month --side SELL --vus 5 --iterations 20 --label v1-one-month-sell-vu5-r1
python3 backend/loadtest/manage.py verify-result --label v1-one-month-sell-vu5-r1
```

`run`은 초기 행 수와 인증된 보유자산 응답을 확인한다. seed를 복원하지 않은
상태는 거부한다. 시험·검증·seed를 동시에 실행하지 않는다. 동일 조건을 최소
3회 반복하고 새 label을 사용한다. 실패·타임아웃은 성공 샘플로 취급하지 않는다.
401·403, 응답 수량 불일치 등은 시험을 중단한다.

`verify-result`는 측정이 끝난 상태에서만 실행한다. 대상 회원의 현재 수량·평균
매입가·실현손익과 모든 과거 스냅샷을 결정적인 예상 결과와 비교한다. 검증 요청은
부하 측정 및 SQL 통계 수집 이후 실행한다.

### k6 CLI로 직접 실행

위의 환경 준비 후, 저장소 루트에서 seed를 복원하고 스크립트 디렉터리로 이동한다.

```bash
python3 backend/loadtest/manage.py seed
python3 backend/loadtest/manage.py verify
cd backend/loadtest
k6 run \
  -e SCENARIO=six-months \
  -e SIDE=BUY \
  -e VUS=5 \
  -e ITERATIONS=20 \
  -e SUMMARY_FILE=./.local/k6-six-months-buy-vu5.json \
  transactions-v1.js
```

| 환경변수 | 값 | 기본값 |
| --- | --- | --- |
| `SCENARIO` | `today`, `one-month`, `six-months` | `today` |
| `SIDE` | `BUY`, `SELL` | `BUY` |
| `VUS` | 준비된 회원 수 이내의 가상 사용자 수 | `1` |
| `ITERATIONS` | 회원당 등록 건수, 최대 500 | `5` |
| `SUMMARY_FILE` | JSON 결과 저장 경로 | `./.local/summary-<version>.json` |

콘솔에 정상 완료 건수·성공 처리량·검증 실패율·POST 평균/p95/p99를 출력한다.
다음 시험도 반드시 seed 복원 후 실행한다. 적은 건수의 p95/p99는 스모크 확인용이다.
처리량은 최초 CSRF 획득을 포함한 전체 시험 시간 기준이다.

직접 실행은 k6 결과만 저장한다. SQL 통계·실행 메타데이터·전체 과거 스냅샷
검증까지 함께 기록하는 기준 측정에는 `manage.py run`과 `verify-result`를 사용한다.

### V2 실행 및 버전 비교

한 시험에서는 한 버전만 호출하며 V1/V2를 동시에 실행하지 않는다.
스크립트 디렉터리(`backend/loadtest`)에서:

```bash
k6 run \
  -e SCENARIO=six-months \
  -e SIDE=BUY \
  -e VUS=5 \
  -e ITERATIONS=20 \
  -e SUMMARY_FILE=./.local/k6-v2-six-months-buy-vu5.json \
  transactions-v2.js
```

SQL 통계와 실행 메타데이터까지 함께 수집하려면 저장소 루트에서:

```bash
python3 backend/loadtest/manage.py seed
python3 backend/loadtest/manage.py run --version v1 --scenario six-months --side BUY --vus 5 --iterations 20 --label v1-six-months-buy-vu5-r1
python3 backend/loadtest/manage.py verify-result --label v1-six-months-buy-vu5-r1
python3 backend/loadtest/manage.py seed
python3 backend/loadtest/manage.py run --version v2 --scenario six-months --side BUY --vus 5 --iterations 20 --label v2-six-months-buy-vu5-r1
python3 backend/loadtest/manage.py verify-result --label v2-six-months-buy-vu5-r1
```

각 버전의 워밍업은 별도로 수행하고 본 측정 전에는 seed를 복원한다. JSON의
버전·경로·fixture hash·VU·등록 횟수가 의도한 조건인지 확인한다.

## 결과와 해석

결과는 `.local/results/<label>/`에 저장한다.

| 파일 | 내용 |
| --- | --- |
| `run.json` | 커밋·dirty 상태, fixture hash, 데이터 규모, 기간·BUY/SELL, VU·횟수, 실행 시각·종료 코드 |
| `summary.json` | POST 전용 `transaction_duration`의 p95·p99, 성공 건수·처리량, 오류율 및 k6 지표 |
| `sql.json` | 로컬 `pg_stat_statements`의 정규화된 SQL별 호출 수·실행 시간 |
| `verification.json` | 대상 회원별 보유자산·과거 스냅샷 검증 성공 결과 |

`transaction_duration`은 POST만 포함한다. 전체 `http_req_duration`에는 최초 CSRF
조회가 섞이므로 이를 거래 응답시간으로 보고하지 않는다. 성공 건수는 반드시
`VU × iterations`와 같아야 한다. 초기에는 정확성·완료 건수·오류율을 기준으로
판정하고 임의의 응답시간 목표를 두지 않는다.

SQL 통계는 사전 검증 후 초기화하고 측정 직후 저장한다. PostgreSQL 전체에서
수집하므로 이 전용 DB에 다른 요청·seed·검증 작업을 동시에 보내지 않는다.
BEGIN/COMMIT 등도 포함되므로 SELECT/DML 호출 수를 구분해서 비교한다.
요청당 SQL은 호출 수를 **성공 건수**로 나눠 보되, 실패가 있으면 기준 측정으로
채택하지 않는다. 여러 요청의 합계 SQL 시간은 API 응답시간과 다르다.

Actuator의 `/actuator/prometheus`로 HTTP·Hikari·JVM 지표를 확인할 수 있다.
서버 사양, JVM·DB 버전, 커넥션 풀 설정과 CPU·메모리·DB 대기를 측정 기록에
함께 남긴다. 애플리케이션 내부 단계별 타이머는 이번 준비 단계에서 추가하지
않았으며, SQL 결과로 병목을 좁힌 뒤 필요한 구간만 계측한다.

같은 회원의 동시 변경은 이 부하 시험에 섞지 않고 별도 정합성 시험으로 진행한다.
수정·삭제의 V2 API와 날짜별 이력 재생 알고리즘 개선은 후속 단계다.

## V2 구현 범위

`portfolio.v2` 패키지의 Controller → 거래 Service → 스냅샷 Service → 계산 Domain 흐름으로 구성한다.
기존 Entity·DTO·`PortfolioLedgerCalculator`를 재사용하며 DB 스키마는 바꾸지 않는다.

- 회원 → 포트폴리오 순서로 잠금을 확보한 뒤 거래 저장과 스냅샷 갱신을 같은 트랜잭션에서 처리한다.
- 거래일 검증은 해당 종목의 이후 잔고 등록·조정 존재 여부를 조건 조회한다.
- 등록한 거래를 포함한 정렬된 이력을 Asset과 함께 한 번 조회하고 현재 보유자산·날짜별 계산에 재사용한다.
- 현재 전량 매도한 종목도 과거 이력의 종목 목록에 포함한다.
- 기존 스냅샷은 대상 포트폴리오·기간으로 한 번 조회한다.
- 종가는 최대 31개 재계산 기준일씩 나누어 조회한다. 직전 종가 기준일도 포함한다.
- 기존 스냅샷은 dirty checking으로 반영하고 날짜마다 `saveAndFlush`하지 않는다.
- 새 스냅샷의 IDENTITY INSERT는 개별 SQL로 실행될 수 있다. `saveAll`을 JDBC 일괄 INSERT로 간주하지 않는다.
- 오늘 거래는 과거 스냅샷 조회·갱신을 생략한다.
- 날짜별 이력 재생은 기존 계산기를 사용한다. 순서·반올림·가격 누락 시 삭제·저장 범위 초과 시 롤백을 유지한다.

범위를 제한해 읽은 가격의 메모리 사용량을 줄이지만, 전체 이력·대상 스냅샷은
요청 중 메모리에 보관한다. SQL 감소 효과와 남은 이력 재생 비용은 별도로 측정한다.

## 초기 스모크 검증 기록

2026-10-15, V1 기준 커밋 `0dba10d7`에서 로컬 Java 21, PostgreSQL 16.15,
k6 1.1.0으로 다음을 확인했다. 테스트 도구와 기존 프론트엔드 변경은 미커밋 상태였다.

- 작은 데이터(2회원·3종목·30거래)에서 3기간 × BUY/SELL, 각 2건: 총 12건 정상 등록.
- 기본 데이터(50회원·20종목·1,000거래)에서 6개월 전 BUY 1건: 정상 등록.
- 기본 데이터에서 서로 다른 5회원 × 오늘 BUY 2건: 총 10건 정상 등록.
- 각 실행 후 보유 수량·평균 매입가·실현손익과 모든 과거 스냅샷의 예상값 일치.
- 합성 데이터 단위 테스트 7개 통과. 백엔드 `spotlessCheck test` 통과(638개 중 기존 조건부 제외 1개).

기본 데이터의 과거 BUY 1건은 POST 약 **17.6초**, 전체 SQL 호출 **5,776회**,
그중 종가 개별 조회 **5,240회**를 기록했다. 날짜별 반복 이력 조회는 131회다.
단일 요청의 스모크 기록이며 워밍업·반복을 갖춘 성능 기준이나 p95 결과로
사용하지 않는다. SQL 실행 시간만으로 전체 지연을 설명할 수 없으며, 반복
왕복·이력 재생·ORM 처리 비용을 함께 확인해야 한다.

원본 결과는 `.local/results/smoke-*`에 보존한다. 실제 기준 측정은 위의 워밍업·
데이터 복원·반복 절차로 별도 수행한다.

## V1/V2 순차 비교 기록

2026-10-15, 같은 로컬 서버에서 각 버전을 2건씩 워밍업한 뒤 기본 데이터
(50회원·20종목·회원당 1,000거래·131개 과거 스냅샷)를 매 시험 복원했다.
6개월 전 BUY, VU 1명, 회원당 3건으로 V1 → V2를 3회 반복했다.
각 버전의 측정 요청은 9건이며 워밍업 요청은 결과에서 제외했다.

| 지표 | V1 | V2 |
| --- | ---: | ---: |
| POST 평균 응답시간(3회 평균) | 18,569.07 ms | 94.61 ms |
| 요청당 전체 SQL 호출 | 5,776 | 149 |
| 요청당 종가 조회 SQL | 개별 조회 5,240회 + 기준일 조회 1회 | 일괄 조회 5회 + 기준일 조회 1회 |
| 정상 완료 | 9/9 | 9/9 |
| 보유자산·실현손익·모든 과거 스냅샷 검증 | 일치 | 일치 |

V1의 실행별 평균은 18,509.53 / 18,321.10 / 18,876.60 ms,
V2는 92.40 / 87.75 / 103.69 ms다. Java 21·PostgreSQL 16.15·k6 1.1.0,
Hikari min/max 10, 부하 발생기·서버·DB가 같은 로컬 컴퓨터인 조건이다.
측정 시 소스는 미커밋 상태였으며 `run.json`에 기준 커밋·dirty 상태를 기록했다.
fixture hash는 `e812854492f34ac082d2b4744211409fc2f33688751e86ebaf488ab277a17143`이다.

소규모 반복 비교이며 실제 서버의 최대 처리량이나 안정적인 p95를 의미하지 않는다.
동시 사용자 수 증가·지속 부하·부하 발생기 분리는 후속 검증이다. 원본은
`.local/results/compare-*`, 집계는 `.local/results/comparison.json`에 보존한다.

## JFR 프로파일링과 기간·동시 사용자 비교

계산 코드를 바꾸지 않고 JDK 21의 JFR로 실행 스택과 DB 소켓 읽기를 수집한다.
전용 서버를 실행하고 `prepare`를 완료한 뒤 서버 JVM의 PID를 지정한다.
`JAVA_HOME/bin/jcmd -l`에서 `DaynomyApplication`과 loadtest 설정을 확인한다.

```bash
python3 backend/loadtest/benchmark.py profile \
  --label profile-496-example --pid <서버-JVM-PID> --jdk "$JAVA_HOME" \
  --vus 1 --iterations 3 --repeats 1

python3 backend/loadtest/analyze_profile.py --jdk "$JAVA_HOME" \
  backend/loadtest/.local/results/profile-496-example/*.jfr
```

JFR 실행은 성능 비교에 섞지 않는다. `ExecutionSample`/`NativeMethodSample`의
API 처리 스레드 스택을 자동 flush·기타 flush·이력 계산·JDBC 등으로 분류한다.
비율은 **실행 샘플 비중**이며 정확한 구간별 경과시간 비율이 아니다. DB 포트의
SocketRead 시간은 서버 처리·통신 대기를 포함하고 PostgreSQL 실행시간과 다르다.
표본이 없는 짧은 요청은 해당 코드가 비용이 없다는 뜻이 아니다.

JFR을 끈 비교 실행:

```bash
python3 backend/loadtest/benchmark.py matrix --label matrix-496-example \
  --vus 1 5 10 20 50 --iterations 3 --repeats 3
```

- 기본은 BUY이며 `--side SELL`로 매도를 별도로 측정할 수 있다.
- 각 기간의 V1/V2를 워밍업하고, 매 측정 전에 같은 초기 데이터를 복원한다.
- 반복 차수마다 버전 실행 순서를 바꾼다. V1/V2를 동시에 호출하지 않는다.
- 각 결과에 응답·전체 과거 스냅샷 검증을 수행한다. 실패 시험을 정상 성능 비교에 넣지 않는다.
- 클라이언트 중단 후에도 서버 작업이 남을 수 있어, DB 연결 active/pending이 연속 3회
  0이 된 뒤 초기화한다. 30분 내 끝나지 않으면 다음 시험을 진행하지 않는다.
- `resources.json`은 0.5초 간격의 JVM CPU 누적시간·메모리·GC·Hikari 사용/대기 연결
  등의 관측이다. 짧은 피크는 놓칠 수 있으며 측정 전 검증·CSRF 구간도 포함한다.
- 전체 계획·환경과 집계는 `.local/results/<label>/environment.json`, `comparison.json`에 저장한다.
  시험별 k6 출력은 같은 디렉터리의 로그, 상세 결과는 각 시험 label 디렉터리에 저장한다.
- VU당 요청 횟수가 정해진 짧은 동시 요청 비교다. 지속 부하·고정 RPS·최대 처리량 시험과
  구분하고, 적은 표본의 p95/p99를 안정적인 지연 지표로 해석하지 않는다.

측정 방법과 결과 해석은 [프로파일링·비교 기록](PROFILING-496.md)에 정리한다.

## 검증 및 종료

```bash
python3 -m unittest discover -s backend/loadtest -p 'test_*.py'
k6 inspect backend/loadtest/transactions-v1.js
k6 inspect backend/loadtest/transactions-v2.js
cd backend
./gradlew spotlessCheck test
```

서버는 `serve` 터미널에서 Ctrl+C로 종료한다. 저장소 루트에서 DB를 종료한다.
`down`은 컨테이너·네트워크만 제거하고 전용 데이터 볼륨은 보존한다.

```bash
python3 backend/loadtest/manage.py down
```

관련 자료: [이슈 #496](https://github.com/woowacourse-teams/2026-DAYNOMY/issues/496),
[도구 선정 #477](https://github.com/woowacourse-teams/2026-DAYNOMY/issues/477),
[k6 per-VU iterations](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/per-vu-iterations/),
[k6 cookies](https://grafana.com/docs/k6/latest/using-k6/cookies/).
