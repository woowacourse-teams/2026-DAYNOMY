# 투자리그 전체 API V1/V2 부하테스트 (#601)

브랜치: `chore/BE/#601`. 프론트엔드나 운영 API 구현은 변경하지 않는다.
`league-v1.js`는 개선 전 배포, `league-v2.js`는 개선 후 배포를 측정한다.
두 파일은 동일한 API 경로·요청·판정을 사용하므로 실제 백엔드 배포를 바꾼 뒤 각각 실행해야 한다.

## 실행 파일과 시나리오

기존 `league-rankings-v1.js`/`league-rankings-v2.js`는 계정 없는 순위 조회 확인용으로 유지한다.
전체 기능은 `league-v1.js`/`league-v2.js`의 `CASE`로 선택한다. 한 실행은 한 조건을 측정한다.

| CASE                                                         | 측정 내용                                                                                      |
| ------------------------------------------------------------ | ---------------------------------------------------------------------------------------------- |
| `weeks`, `rankings`                                          | 주차와 순위. `PAGE`, `LEAGUE_TYPE`, `WEEK_START`로 페이지·3개 유형·최근 3주를 각각 측정        |
| `investor`, `details`, `daily-history`                       | 공개 투자자 프로필, 종목·판단·복기 상세, 일별 수익률                                           |
| `profile`, `create-profile`, `save-profile`, `publication`   | 내 프로필 조회·생성·수정, 참가 설정 전환 후 복원                                               |
| `source`, `shared`, `import`, `visibility`, `remove`         | 원본·공유 자산 조회, 가져오기, 숨김 후 공개 조회·해제, 제거 후 다시 가져오기                   |
| `decisions`, `decision`, `reason`, `review`, `revise-review` | 내 기록 조회, 판단 저장·동일 키 재전송·후속 조회, 이유 수정, 복기 작성·수정                    |
| `follow`, `unfollow`, `follow-summary`                       | 팔로우·해제 후 요약 확인, 팔로잉 요약 조회                                                     |
| `browse` (기본)                                              | 주차 → 순위 → 투자자 → 상세 → 일별 수익률                                                      |
| `onboarding`                                                 | 프로필 저장 → 원본 조회 → 공유 자산 가져오기 → 판단 작성 → 참가 설정 → 프로필·공유 자산 재조회 |
| `records`                                                    | 판단 작성·재전송 → 복기 작성·수정 → 기록 재조회와 저장 결과 검증                               |
| `follow-flow`                                                | 해제 → 팔로우·중복 팔로우 → 해제와 각 요약 확인                                                |
| `privacy`                                                    | 프로필 비공개 → 비로그인 프로필·상세·일별 조회의 404 확인 → 공개 설정 복원                     |
| `mixed`                                                      | 전역 순번 10회 중 7회 browse, 1회 follow-flow, 1회 records, 1회 privacy                        |
| `contention`                                                 | 같은 회원·같은 requestKey에 동시 판단 저장 요청, 동일 ID 재사용·단일 저장 확인                 |
| `recalculate`                                                | 격리 환경에서 전체 수익률 재집계를 한 번 실행                                                  |
| `mixed-with-recalculate`                                     | 혼합 부하 중간에 별도 1 VU가 재집계 한 번 실행                                                 |

`reason`은 #591 API 배포가 선행되어야 한다. 미배포 404를 성공으로 처리하거나 자동 제외하지 않는다.
`records`는 새 판단·복기를 작성하며, `reason`은 fixture의 기존 판단을 수정한다.
`import`는 기존 공유 자산이 없는 원본/값이 같은 원본/값이 다른 원본을 각각 준비해 최초·동일·변경 가져오기를 구분한다.

## 계정·데이터 준비

1. `league-fixture.example.json`을 비공개 `loadtest/accounts-league.json`으로 복사하고 실제 테스트 데이터로 채운다. 예시는 실행 가능한 계정이나 토큰이 아니다.
2. `namespace`는 `lt601-YYYYMMDD`, 프로필 이름은 `<namespace>-번호`로 한다. 쓰기 전에 모든 측정·워밍업 계정의 실제 공개 ID와 이름을 확인하며, 일치하지 않으면 요청을 중단한다.
3. `users`는 측정 전용, `warmup`은 별도 워밍업 전용이다. ID·토큰이 겹치면 실행하지 않는다. `targets`는 두 그룹과 다른 공개 투자자로 준비하고 종목·상세 공개를 허용한다.
4. 각 계정에는 fixture에 적힌 공유 보유 자산·판단·복기를 미리 준비한다. `decisionId`/`reviewId`는 해당 계정 소유여야 한다. 공개 타깃의 `holdingCount`와 선택 주차의 `dailyPointCount`를 실제 조회로 확인한다. 종목 수가 0인 상세나 빈 순위로 성능을 측정하지 않는다.
5. 프로필 생성은 별도 초기 상태를 쓴다. `CASE=create-profile`에서는 모든 측정·워밍업 계정의 `publicId`를 `null`로 지정하고 실제 프로필이 없어야 한다(GET 204). 각 계정은 한 번만 사용하므로 `RPS × 시간(초)` 이상의 신규 계정이 필요하다.
6. Access Token은 기존 로그인 또는 별도 승인된 테스트 계정 발급 절차로 준비한다. 실제 서명키·DB 비밀번호를 부하 발생기로 보내지 않는다. 토큰의 회원 ID·USER 역할·만료를 검사하고 실제 API도 인증을 확인한다. Refresh Token은 필요 없다.
7. 참가자 100명·1,000명, 회원별 종목·판단·복기·팔로우 수, 장기간 이력을 구분해 준비한다. 가격·거래일 달력·일별 수익률·확정 순위가 서로 일치해야 한다.

**이 스크립트는 데이터 적재·토큰 발급·DB 복원을 자동으로 수행하지 않는다.**
테스트 계정과 기준 데이터를 먼저 준비해야 하며, 이를 완료하지 않은 상태로 #601을 완료 처리하지 않는다.

## 초기 상태와 반복 비교

- 측정 전 비공개 DB 원본을 보관하고 SHA-256을 `baselineHash`에 기록한다. 이 값은 비교용 표식이며 DB와의 일치는 운영자가 확인한다.
- 같은 스크립트 버전·서버 사양·데이터·타깃 목록·워밍업 순서를 사용한다. 각 실행의 `DEPLOYED_SHA`는 실제 배포한 백엔드 SHA다.
- 워밍업은 별도 계정으로 선택한 흐름을 한 번씩 실행한다. mixed는 네 종류의 흐름을 모두 워밍업한다. 본 측정과 `phase`를 분리한다. 워밍업 계정도 쓰기로 바뀌므로 매 비교 전에 초기 상태로 복원한다.
- 쓰기 후에는 보유 자산뿐 아니라 프로필/회원 이름·참가 이력·공유 자산 이력·판단·복기·팔로우·일별 수익률·집계 결과의 변경도 확인한다. 참가 해제/재참가는 참여 이력을 바꾸므로 API에서 값만 되돌린 것으로 원본 복원을 대신할 수 없다.
- 공유 dev는 식별한 테스트 회원의 데이터만 복원한다. 전체 집계 결과를 포함한 비교가 필요하면 전용 backend/DB 복제 환경을 사용하고 그 환경의 원본을 복원한다. 일반 사용자나 원본 자산 데이터는 변경하지 않는다.
- 비공개/공개 전환 중에는 전체 순위 참가자 수가 변할 수 있다. `CASE=rankings`는 `EXPECTED_TOTAL`과 정확히 비교하고, 혼합 흐름은 비어 있지 않은 페이지와 응답 계약을 검증한다.
- 측정 계정은 전역 반복 순번으로 선택한다. 순환 전에 이전 요청이 끝나도록 계정 수를 검사한다. 30개 계정이면 쓰기 `1 RPS × 30초`부터 시작할 수 있다. 고부하는 더 많은 계정이 필요하다.
- `contention`만 의도적으로 같은 계정과 같은 키를 재사용한다. 서버의 실제 DB 경합·잠금 검증은 dev 또는 격리 환경 실행 결과로 확인한다.

## GitHub Actions 첫 실행

기존 `.github/workflows/k6-dev.yml`은 수정하지 않는다. 실행 가능한 브랜치에 스크립트를 반영한 뒤
**k6 development smoke test**의 기존 `script`/`params` 입력을 사용한다.
`DEV_BASE_URL`, `K6_PROMETHEUS_RW_SERVER_URL`, runner, Prometheus 출력과 결과 artifact 설정을 그대로 사용한다.

기존 워크플로는 `portfolio-analysis-*.js`와 거래 스크립트에만 계정 Secret을 파일로 풀어 준다.
리그 계정은 `LEAGUE_FIXTURE_FILE`에 지정한 비공개 파일에서 읽는다. 분석/거래 계정 파일이나 Secret은 사용하지 않는다.

1. 실제 테스트 계정으로 채운 `accounts-league.json`을 runner의 checkout 디렉터리 **밖에** 준비한다. 예: `/home/ubuntu/loadtest-fixtures/accounts-league.json`. checkout은 작업 디렉터리의 비공개 파일을 지울 수 있다.
2. 디렉터리는 runner 사용자만 접근하도록 700, 파일은 600 권한으로 둔다. 새 리그 Secret이나 워크플로 변경은 필요 없다.
3. `params`에는 절대 파일 경로만 넣는다. 토큰·fixture 본문을 입력하거나 Git·로그·artifact에 올리지 않는다. `BASE_URL`/`ACCOUNTS_FILE`은 기존 워크플로가 관리하므로 넣지 않는다.
4. 외부 fixture는 워크플로가 생성하거나 삭제하지 않는다. 실행자가 계정·토큰을 갱신하고 사용 후 정리한다. 결과 artifact에는 기존 규칙대로 `result-*.json`만 포함된다.

script:

```text
loadtest/league-v1.js
```

params 예시 — SHA·월요일·확정 여부는 실제 환경 값으로 바꾼다:

```json
{
  "CASE": "browse",
  "LEAGUE_FIXTURE_FILE": "/home/ubuntu/loadtest-fixtures/accounts-league.json",
  "DEPLOYED_SHA": "0123456",
  "WEEK_START": "2026-10-19",
  "EXPECTED_CONFIRMED": false,
  "RPS": 1,
  "DURATION": "30s"
}
```

V2는 script를 `loadtest/league-v2.js`로, SHA를 개선 배포 값으로 바꾸고 나머지 조건을 유지한다.
`CASE=rankings`에는 `EXPECTED_TOTAL`도 필요하다. `AUTHENTICATED=true`로 로그인 순위·투자자 조회를 비교할 수 있다.

| 입력                                               | 기본값 / 의미                                                                         |
| -------------------------------------------------- | ------------------------------------------------------------------------------------- |
| `CASE`                                             | `browse`. 위 표에서 선택                                                              |
| `DEPLOYED_SHA`, `WEEK_START`, `EXPECTED_CONFIRMED` | 필수. 실제 배포·월요일·해당 주차 확정 여부                                            |
| `LEAGUE_TYPE`, `PAGE`                              | `WEEKLY_RETURN`, `0`. 같은 조건으로 유형·주차·페이지별 비교                           |
| `EXPECTED_TOTAL`                                   | rankings에서 필수. 선택 페이지가 비어 있지 않은 전체 참가자 수                        |
| `AUTHENTICATED`                                    | 기본 false. browse/순위/투자자 조회에 로그인 쿠키 적용                                |
| `RPS`, `DURATION`, `VUS`                           | 기본 `1`, `30s`, `20`. RPS는 **사용자 흐름 시작률**, 실제 HTTP 요청률과 다르다        |
| `REQUEST_TIMEOUT_SECONDS`                          | 기본 10, 최대 60                                                                      |
| `TARGET_P95_MS`                                    | 기본 1000. 일반 API 각각에 적용하며 두 버전에 같은 기준 사용                          |
| `RUN`                                              | Actions 실행 ID, 로컬 `local`. 로컬 반복 시 다른 이름 사용                            |
| `LEAGUE_FIXTURE_FILE`                              | 로컬 기본 `./accounts-league.json`. Actions에서는 runner checkout 밖의 절대 경로 필수 |
| `ISOLATED`, `BATCH_TIMEOUT_SECONDS`                | 로컬 집계 전용. 아래 참고                                                             |

## 집계와 동시 부하

관리자 재집계는 테스트 회원 외 모든 참가자도 처리한다. **스크립트가 `GITHUB_ACTIONS=true`에서 집계 CASE를 차단한다.**
전용 backend/DB 복제 환경에서 fixture의 `isolated=true`, 해당 환경에서만 유효한 `admin` 회원 ID·ADMIN 토큰,
그리고 `ISOLATED=true`를 모두 준비한다. 일반 사용자 계정에 관리자 토큰을 재사용하지 않는다.

로컬 k6에서 `CASE=recalculate` 또는 `CASE=mixed-with-recalculate`를 선택한다.
집계는 별도 shared-iterations 1회로 실행하며 타임아웃은 `BATCH_TIMEOUT_SECONDS`(기본 300초, 최대 1200초)다.
HTTP 요청 타임아웃은 서버의 진행 중인 집계를 취소하지 않는다.

관리자 API는 전체 재집계만 제공한다. 증분 계산·종가/달력 수집 이벤트·주간 확정은 기존 서비스 벤치마크와 실제 스케줄 실행을 별도로 확인한다.
`LeagueFinalizationBenchmarkTest`는 주간 확정 벤치마크이며 전체 수익률 계산 성능을 대신하지 않는다.

## 결과와 통과 기준

- `league_flow_*`: 본 측정 흐름 시작 수·성공률·소요 시간. 예정 흐름 누락 또는 실패가 있으면 실패다. 결과에는 흐름 시작률과 마지막 응답 대기까지 포함한 실제 API 응답 처리량을 구분해 기록한다.
- `league_api_*`: API별 건수·성공률·지연. warmup/preflight/measurement를 분리한다. 측정 응답은 명세와 후속 조회를 모두 검사한다.
- `league_batch_*`: 집계 성공률과 시간. 일반 API의 p95 1초 기준과 분리한다.
- 예상한 비공개 조회 404는 정상 계약으로 처리한다. 기본 `http_req_failed`에는 이 404도 포함되므로 판정은 `league_api_success`를 사용한다.
- API별 p95 목표, 성공률 100%, 예정 흐름 달성, dropped_iterations=0을 검사한다. 누락이나 계약 오류는 실행을 중단하며 자동 재시도하지 않는다.
- 요청 URL의 사용자 ID는 Prometheus 라벨에서 제외한다. 결과 JSON에는 토큰·닉네임·회원/투자자 ID·응답 본문을 저장하지 않는다.
- `result-league-<version>-<case>-<run>.json`에 API별 건수·p95/p99와 **미실행 API 목록**을 보관한다. 한 CASE 통과는 전체 API 검증 완료를 뜻하지 않는다. 이슈의 모든 CASE/조건별 결과를 연결한다.
- `mixed` 10회 미만은 일부 흐름을 실행하지 못한다. 30초 확인 후 각 API와 혼합 부하를 별도로 측정하고, API마다 목표 요청률을 정해 증가시킨다.
- Grafana에서 CPU·메모리·GC·DB 연결/잠금 대기·SQL 수/시간과 집계 성공/실패·확정 지연을 함께 확인한다. 자원 포화나 오류가 지속되면 중단하고 서버·DB 작업 종료와 회복 후 복원한다.

## 실제 API 요청 없는 검증

```bash
node --experimental-vm-modules --test loadtest/tests/*.test.mjs backend/loadtest/tests/transactions.test.mjs
```

위 명령으로 로컬에서 실행한다. 기존 Backend CI 워크플로는 수정하지 않으며 이 검증 명령을 새로 연결하지 않는다.
워크플로 호환 테스트는 기존 k6 실행 셸을 그대로 실행해 파일 경로·인수 전달과 기존 분석 계정 처리를 확인한다.
HTTP mock 테스트는 흐름·인증·검증·V1/V2 조건의 동등성을 확인하며 실제 서버 성능·동시 DB 정합성을 증명하지 않는다.
