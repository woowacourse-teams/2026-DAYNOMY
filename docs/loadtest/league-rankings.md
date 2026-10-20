# 리그 순위 조회 개선 전 측정

측정 대상은 `GET /api/league/rankings?leagueType=WEEKLY_RETURN&page=0&size=20`이다.
이 변경에는 테스트 준비 파일만 포함하며, 순위 계산 서비스와 조회 쿼리는 변경하지 않는다.

## 개발 DB 준비

1. DBeaver 또는 psql에서 **dev 백엔드가 사용하는 PostgreSQL**에 연결한다.
2. 현재 주차의 거래일 달력과 실제 종가 수집이 완료돼 있어야 한다. SQL은 기존 종가와 달력을 수정하지 않으며, 가격이 있는 국내 상장 종목이 3개 미만이거나 수집 상태가 불완전하면 중단한다.
3. [seed-league-rankings.sql](../../backend/loadtest/sql/seed-league-rankings.sql) 전체를 한 번 실행한다. 회원 100명, 공개 프로필·리그 참여·포트폴리오, 보유 종목, 투자 판단·복기와 일별 수익률을 추가한다. `lt601-rank-` 회원이 이미 있으면 중복 적재하지 않는다.
4. dev API를 조회해 `confirmed=false`, `asOfDate`가 존재하고 `totalCount`가 100명 이상인지 확인한다. 기존 참가자가 있으면 총인원은 100명을 초과할 수 있다.

이 SQL은 Flyway 마이그레이션이 아니므로 **머지·배포만으로 DB에 자동 실행되지 않는다.**
로컬 DB에 넣은 데이터도 dev DB로 복사되지 않는다.

## GitHub Actions 실행

기존 **k6 development smoke test** 워크플로를 사용한다.
스크립트가 반영된 브랜치를 선택하고 다음 값을 입력한다.
기존 `DEV_BASE_URL`, Prometheus 전송 설정과 러너는 그대로 사용한다.

| 실행 | script | params |
| --- | --- | --- |
| 스모크 | `loadtest/league-rankings-smoke.js` | `{}` |
| 부하 | `loadtest/league-rankings-load.js` | `{"VUS":50,"RPS":50}` |

- 스모크: 5 VU, 총 5분. 1분 1 RPS → 3분 5 RPS → 마지막 1분 5→0 RPS.
- 부하: 50 VU, 총 5분. 각 1분씩 5 → 10 → 20 → 50 RPS → 마지막 1분 50→0 RPS.
- 부하 스크립트는 시작 전에 순위가 비어 있지 않은지 확인하고, 본 측정은 확인한 주차로 고정한다.
- 구간별 RPS·p95·p99, HTTP 오류율, check 성공률과 누락 요청 수는 Actions 로그에 출력된다. 부하 스크립트의 요약 JSON은 별도로 생성하지 않는다.
- HTTP 오류, 응답 검사 실패 또는 누락 요청이 있으면 실패로 종료한다. 개선 전 측정에서 실패하더라도 로그와 Grafana 지표는 비교 자료로 보존한다.

## Grafana 확인과 개선 후 비교

k6 지표는 기존 워크플로의 Prometheus remote write로 전송한다.
Grafana에서 같은 실행 시간과 `testid=<Actions run ID>-<attempt>`로 조회한다.
서버 지표는 dev 환경의 기존 수집 경로를 사용한다.

개선 전 API의 배포 SHA, 주차·실제 참가자 수·수집 상태, 서버 사양과 DB 연결풀 크기를 기록한다.
측정 후 성능 개선 코드를 별도로 배포하고 같은 스크립트·입력·데이터 조건으로 다시 측정한다.
테스트 스크립트 선택만으로 API 구현이 바뀌지는 않는다.
