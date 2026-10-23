# 리그 순위 조회 개선 전 측정

측정 대상은 `GET /api/league/rankings?leagueType=WEEKLY_RETURN&page=0&size=20`이다.
이 변경에는 테스트 준비 파일만 포함하며, 순위 계산 서비스와 조회 쿼리는 변경하지 않는다.

## 개발 DB 준비

1. DBeaver 또는 psql에서 **dev 백엔드가 사용하는 PostgreSQL**에 연결한다.
2. 현재 주차의 거래일 달력과 실제 종가 수집이 완료돼 있어야 한다. SQL은 기존 종가와 달력을 수정하지 않으며, 가격이 있는 국내 상장 종목이 3개 미만이거나 수집 상태가 불완전하면 중단한다.
3. [seed-league-rankings.sql](../../backend/loadtest/sql/seed-league-rankings.sql) 전체를 한 번 실행한다. `lt601-rank-001`부터 `lt601-rank-1000`까지 테스트 회원을 총 1,000명으로 맞춘다. 기존 100명이 있으면 그 데이터는 유지하고 나머지 900명만 추가한다. 번호가 모두 있으면 중복 적재하지 않고 중단한다.
4. 새 회원마다 공개 프로필·리그 참여·포트폴리오, 보유 종목 3개, 투자 판단 10개, 일부 판단의 복기와 수집 완료된 거래일별 수익률을 추가한다. 마지막 조회의 `added_members`는 이번 추가 인원, `total_test_members`는 전체 테스트 인원이다. `ANALYZE`로 추가 데이터 규모를 실행계획에 반영한다.
5. dev API를 조회해 `confirmed=false`, `asOfDate`가 존재하고 `totalCount`가 1,000명 이상인지 확인한다. 기존 참가자가 있으면 총인원은 1,000명을 초과할 수 있다. 기존 테스트 회원은 다른 주차로 재생성하지 않으므로, 주차가 바뀐 경우 해당 주의 참가·수익률 데이터가 유효한지도 확인한다.

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

## 추가 리그 조회 API 측정

아래 스크립트는 순위 조회와 별도로 실행한다. 둘 다 기본 50 VU·목표 50 RPS이며, 각 1분씩 5 → 10 → 20 → 50 RPS, 마지막 1분 50→0 RPS로 총 5분 실행한다. `RPS`와 `VUS`는 양의 정수로 변경할 수 있고, `BASE_URL`은 경로·쿼리·인증 정보가 없는 HTTP(S) origin이어야 한다.

| 대상 | 스크립트 | 실행 전 준비 |
| --- | --- | --- |
| 공개 투자자 상세 | `loadtest/league-investor-details-load.js` | `PUBLIC_ID`의 프로필·상세 공개 동의, 보유 종목과 투자 판단이 각각 1개 이상인 데이터 |
| 팔로잉 요약 | `loadtest/league-follow-summary-load.js` | 서로 다른 유효 Access Token을 가진 계정과 계정마다 공개 팔로우 대상 1명 이상 |

위 시드의 공개 투자자는 상세 조회 대상으로 사용할 수 있다. 팔로잉 요약용 로그인 토큰과 팔로우 관계는 시드가 만들지 않으므로 별도로 준비한다. 측정 전 실제 종목·판단·복기 수와 계정·팔로우 수를 기록한다.

공개 투자자 상세는 Actions의 `script`에 해당 스크립트를, `params`에 `{"PUBLIC_ID":"<공개 투자자 ID>","VUS":50,"RPS":50}`을 지정하거나 저장소 루트에서 실행한다. 아래 명령의 예시 origin과 공개 ID는 실제 측정 대상으로 바꾼다.

```bash
k6 run -e BASE_URL=https://dev.example.invalid -e PUBLIC_ID=replace-with-public-id \
  -e VUS=50 -e RPS=50 loadtest/league-investor-details-load.js
```

팔로잉 요약은 비공개 러너 파일을 `LEAGUE_FIXTURE_FILE`로 전달한다. 파일 형식은 `{ "users": [{ "accessToken": "<유효 토큰>" }] }`이며, 앞에서 최대 `VUS`명의 계정을 사용한다. 실행할 부하에 맞게 충분한 계정을 준비하고 전체 실행 동안 토큰이 유효한지 확인한다. 파일은 Git에 포함하지 않고 토큰을 Actions `params`나 명령행에 직접 넣지 않는다.

```bash
k6 run -e BASE_URL=https://dev.example.invalid \
  -e LEAGUE_FIXTURE_FILE=/private/path/accounts-league.json \
  -e VUS=50 -e RPS=50 loadtest/league-follow-summary-load.js
```

현재 Actions는 팔로잉 요약용 비공개 파일을 자동 생성하지 않는다. 해당 파일을 사용할 수 있는 러너·로컬 환경에서 실행한다. 계정별 사전 조회가 실패하거나 공개 팔로우 대상이 없으면 본 부하를 시작하지 않는다.

두 스크립트는 사전 조회 이후 구간별 RPS·p95·p99와 오류율·검사 성공률·누락 요청을 로그에 출력한다. HTTP 오류·응답 검사 실패·누락 요청이 있으면 실패한다. 별도 결과 JSON이나 응답 시간 합격 기준은 설정하지 않으므로 지연 기준은 실험 조건에 맞게 별도로 판정한다. API별 처리 한계 탐색은 별도 측정이 필요하다.

스크립트의 입력 검사·사전 조회·응답 검사·구간 구분·요약 계산은 실제 서버 호출 없이 확인할 수 있다.

```bash
node --test loadtest/league-load.test.mjs
```
