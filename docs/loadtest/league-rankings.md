# 투자리그 순위 조회 V1/V2 부하테스트

전체 리그 API·혼합 부하·집계 시나리오는 [league.md](./league.md)를 따른다. 이 문서는 계정 없는 순위 조회 확인용이다.

- [league-rankings-v1.js](../../loadtest/league-rankings-v1.js): 개선 전 백엔드 배포를 측정한다.
- [league-rankings-v2.js](../../loadtest/league-rankings-v2.js): 개선 후 백엔드 배포를 측정한다.
- 두 파일은 동일한 요청·검증·판정 코드를 사용하고 버전 라벨만 다르다.

**두 파일 모두 `/api/league/rankings`를 호출한다. 파일 선택으로 서버 구현이 바뀌지 않는다.**
V1 배포 → V1 측정 → V2 배포 → 서버 회복·동일 조건 확인 → V2 측정 순서로 실행한다.
백엔드 개선 구현·배포, 데이터 생성·복원은 이 스크립트에 포함하지 않는다.

## 실행 전 확인

1. dev API의 실제 배포 SHA와 서버 사양을 기록한다. `DEPLOYED_SHA`는 workflow 체크아웃 SHA가 아닌 실제 백엔드 SHA다. 입력값과 서버 배포 일치 여부는 운영자가 확인한다.
2. 두 배포에서 동일한 DB 데이터를 사용한다. 회원뿐 아니라 공개·참가 설정, 참여 기간, 포트폴리오, 거래일 달력, 수익률·확정 순위가 준비되어 있어야 한다.
3. API를 한 번 조회해 `weekStart`, `totalCount`, `confirmed`를 확인한다. `totalCount`는 전체 회원 수가 아닌 해당 주차·리그의 실제 순위 참가자 수다. 0명이면 데이터를 먼저 준비한다.
4. API는 KST 기준 이번 주와 이전 2주만 허용한다. 비교 중 주차가 바뀌지 않도록 같은 월요일을 `WEEK_START`로 지정한다.
5. 종가·달력 수집과 리그 재집계가 끝났는지 확인한다. V1/V2 사이에 데이터나 확정 상태가 바뀌면 비교를 다시 준비한다. 같은 참가자 수만으로 같은 데이터가 보장되지는 않는다.

첫 범위는 비로그인·첫 페이지 20명 조회다. 로그인·팔로우·투자자 상세·재집계 API는 호출하지 않는다.

## GitHub Actions 실행

기존 **k6 development smoke test** workflow를 사용한다. 두 실행 파일과 공통 파일이 실행 브랜치에 있어야 한다.
development 환경의 브랜치 제한을 확인하고 해당 브랜치에 반영한 뒤 실행한다.
기존 `DEV_BASE_URL`, `K6_PROMETHEUS_RW_SERVER_URL`과 runner를 사용하며 새 계정 Secret은 필요 없다.
**Run workflow는 실제 dev API를 호출한다.**

script:

```text
loadtest/league-rankings-v1.js
```

params 예시 — SHA, 월요일, 참가자 수, 확정 여부는 실제 환경 값으로 바꾼다:

```json
{
  "DEPLOYED_SHA": "0123456",
  "WEEK_START": "2026-10-19",
  "EXPECTED_TOTAL": 100,
  "EXPECTED_CONFIRMED": false,
  "RPS": 1,
  "DURATION": "30s"
}
```

V2는 script를 `loadtest/league-rankings-v2.js`로, `DEPLOYED_SHA`를 개선 배포 SHA로 바꾼다.
나머지 데이터·요청·서버 조건은 동일하게 유지한다. `RUN`은 생략하면 Actions 실행 ID가 사용된다.

| 입력                 | 의미 / 기본값                                             |
| -------------------- | --------------------------------------------------------- |
| `DEPLOYED_SHA`       | 필수. 실제 배포 백엔드의 Git SHA, 7~40자리                |
| `WEEK_START`         | 필수. 비교할 주차의 월요일, `YYYY-MM-DD`                  |
| `EXPECTED_TOTAL`     | 필수. 해당 주차·리그의 예상 참가자 수, 1 이상의 정수      |
| `EXPECTED_CONFIRMED` | 필수. 미확정 `false`, 확정 `true`                         |
| `LEAGUE_TYPE`        | `WEEKLY_RETURN`(기본), `STABLE`, `BEGINNER`               |
| `RPS`                | 초당 요청 수. 기본 1, 양의 정수                           |
| `DURATION`           | 기본 `30s`. `2m`, `5m`처럼 초 또는 분 단위 양의 정수      |
| `VUS`                | 실행 슬롯 수. 기본 20, 자동으로 늘리지 않음               |
| `TARGET_P95_MS`      | p95 미만 목표. 기본 1000ms                                |
| `RUN`                | 결과 파일 구분. 로컬 기본 `local`; 반복 시 다른 값을 지정 |

## 측정과 결과

- 첫 `1 RPS × 30초`는 통신·데이터 확인용이다. 첫 검증 GET 1건은 별도 `phase=preflight`로 기록하고 `league_*` 측정 지표에서 제외한다. 이 요청은 워밍업을 대체하지 않는다.
- 성능 비교 전 두 버전에 같은 워밍업을 적용한다. 예를 들어 `1 RPS × 1분`을 별도 실행하고, 이어서 본 측정을 한다.
- 참가자 수를 고정하고 `1 → 5 → 10 → 20 RPS`를 각 2분씩 따로 실행한다. 마지막 통과 구간을 5~10분씩 반복해 확인한다. 참가자 규모·리그 유형·확정 여부 비교는 조건별로 분리한다.
- 통과 기준은 예정 요청 수 달성, 모든 응답의 HTTP 200·예상 참가자 수·주차·확정 여부·순위 데이터 검증 통과, `dropped_iterations=0`, p95 1000ms 미만이다. p95 목표는 초기 기준이며 두 버전에 같은 값을 사용한다.
- 요청 누락은 k6 threshold로 중단한다. 응답 오류나 p95 초과는 종료 시 실패로 판정한다. CPU·메모리·DB 대기는 Grafana에서 관찰하고 자원이 포화되거나 오류가 지속되면 실행을 중단한다. 자동 자원 감시·데이터 복원은 없다.
- 요청 타임아웃은 10초다. k6 중단이 이미 실행 중인 서버·DB 작업을 취소하지는 않는다. 다음 실행 전 서버 회복을 확인한다.
- 로그와 `result-league-rankings-<version>-<run>.json`에서 SHA·입력 조건·예정/실제 요청 수·성공률·p95/p99·판정을 확인한다. 기존 Actions의 `k6-results-*` artifact에 결과가 포함된다.
- Grafana에서 `testid`, `version`, `league_type`, `run`, `phase`로 실행을 구분한다. `league_*`는 본 측정 지표이며 기본 HTTP 지표에는 preflight도 포함된다.

동일한 스크립트 버전·DB·서버 사양·워밍업 조건으로 비교하고, 배포 후 기동 재집계와 다른 배치가 겹쳤는지도 기록한다.
공개 설정 변경으로 확정 순위에 빈 순번이 생길 수 있어 순번의 연속성은 요구하지 않는다.
이 테스트는 처리 성능과 기본 응답 계약을 확인하며, V1/V2 전체 응답의 동등성을 증명하지는 않는다.
