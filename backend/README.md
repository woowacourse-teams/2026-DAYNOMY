# DAYNOMY BACKEND

Google 로그인은 OIDC 경로 하나를 사용한다. Spring의 `OidcUserService`가 조회·검증한
사용자 정보를 회원 계정과 연결하고, 로그인 성공 시 기존 JWT 쿠키를 발급한다.

## 기술 스택

| 항목 | 버전 |
|------|------|
| Java | 17 |
| Spring Boot | 3.5.16 |
| Gradle | 9.5.1 |
| Database | PostgreSQL |
| ORM | Spring Data JPA |

---

## 필수 설치 항목

- **JDK 17** 이상 설치
- **PostgreSQL 17** 설치 및 실행
- Git

---

## 환경 설정

### 1. 저장소 클론

```bash
git clone https://github.com/woowacourse-teams/2026-DAYNOMY
cd daynomy
```

### 2. 데이터베이스 설정

PostgreSQL에서 데이터베이스를 생성합니다.

```sql
CREATE DATABASE daynomy;
```

### 3. 애플리케이션 설정

`src/main/resources/application.yaml`에 DB 접속 정보를 추가합니다.

```yaml
spring:
  application:
    name: daynomy
  datasource:
    url: jdbc:postgresql://localhost:5432/daynomy
    username: <db-username>
    password: <db-password>
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

---

## 빌드

**macOS / Linux**

```bash
# 의존성 다운로드 및 빌드
./gradlew build

# 테스트 제외하고 빌드
./gradlew build -x test
```

**Windows**

```bash
# 의존성 다운로드 및 빌드
gradlew.bat build

# 테스트 제외하고 빌드
gradlew.bat build -x test
```

---

## 실행

```bash
# 애플리케이션 실행
./gradlew bootRun
```

또는 빌드된 JAR 파일로 직접 실행합니다.

```bash
java -jar build/libs/daynomy-0.0.1-SNAPSHOT.jar
```

서버가 정상 기동되면 `http://localhost:8080`으로 접근할 수 있습니다.

---

## 테스트

```bash
# 전체 테스트 실행
./gradlew test
```

## 보유 이력

보유 변경 이력은 원본과 공유 포트폴리오에서 각각 관리하며 과거 성과 계산에 사용한다.
수량·평균 매수가가 실제로 바뀔 때만 추가하고, 공개 숨김 변경에는 추가하지 않는다.
동일 시각의 이력은 ID로 순서를 결정한다. 기존 이력 데이터와 DB 컬럼은 보존한다.
리그 종목 제거는 `DELETE /api/users/me/shared-portfolio/holdings/{assetId}`를 사용한다.
본인 소유 종목만 제거하며 `REMOVED` 이력을 남기고 원본 포트폴리오·판단·복기는 보존한다.
마지막 종목을 제거하면 같은 트랜잭션에서 공개와 리그 참여를 종료한다. 응답은 남은 공유 포트폴리오다.

## 투자 리그 참여와 공개

리그 종류는 `WEEKLY_RETURN`(주간 수익률), `STABLE`(안정형), `BEGINNER`(초보리그)만 지원한다.
주간 확정도 이 세 종류만 생성한다. 순위 선택용 `/api/league/weeks`는 KST 기준 이번 주부터 최근 3주를 최신순으로 반환하며, 기존 8주 상세 성과 지표와 과거 조회 API는 유지한다.

투자 리그 참여 신청·설정 저장에는 공유 포트폴리오의 모든 종목에 보유 이유가 필요하다.
숨긴 종목도 포함하며 기존 보유 이유·판단 기록을 인정한다. 상세 공개는 참여 조건이 아니다.
참여 중 새 종목을 가져올 때는 `holdings[].decision.reason`을 함께 보내야 하며,
자산과 보유 판단을 하나의 트랜잭션으로 저장한다. 참여 전의 기존 가져오기 요청도 지원한다.
보유 이유만 필수이고 나머지 판단 항목·결과 복기는 선택이다. 보유 기간·허용 하락률의
미입력을 허용하는 V32 마이그레이션을 배포 시 적용해야 한다. 기존 판단은 덮어쓰지 않는다.

## 투자 리그 집계

- Dev·Prod는 종가 자동 수집과 리그 집계를 기본으로 실행한다. 종가는 기본 월~토 07시·18시에 기존 수집기로 제공된 최근 2거래일을 저장하고 리그를 재집계한다. 외부 API에 당일 종가가 없으면 제공될 때까지 대기하며 당일 순위 표시를 보장하지 않는다. `STOCK_PRICE_SYNC_CRON`으로 시간을 변경하고 `STOCK_PRICE_SYNC_ENABLED=false`, `LEAGUE_CALCULATION_ENABLED=false`로 각각 중지할 수 있다. 로컬 기본값은 꺼짐이다. 예약 집계는 참가자별 마지막 계산일과 영향받은 주차부터, 가격 동기화는 가장 이른 변경일부터 다시 계산한다. 동기화 후 집계가 실패하면 다음 예약 실행에서 같은 범위를 재시도하고 서버 재시작 후 첫 예약 실행은 전체 이력을 확인한다. 달력 정정과 수동 `/api/admin/league/recalculate`는 전체 거래일·주차를 재계산한다. 참가자별 트랜잭션으로 실패를 격리하며, 실패가 있으면 주간 순위를 확정하지 않는다.
- 주간 확정 성능 측정: `LEAGUE_BENCHMARK=true LEAGUE_BENCHMARK_PROFILES=1000 LEAGUE_BENCHMARK_YEARS=3 ./gradlew test --tests 'org.grit.daynomy.league.service.LeagueFinalizationBenchmarkTest'`를 실행한다. PostgreSQL에 합성 이력을 넣고 결과를 `build/reports/league-finalization-benchmark.txt`에 기록한다. 2026-10-13 로컬 측정은 1,000명·3년·일별 790,000행에서 변경 주차 첫 실행 32.45초, 재실행 29.80초였다. 운영 DB의 실제 이력과 하드웨어에서 이 지표를 다시 확인해야 한다.
- 금액 하한은 없다. 신규 참여자의 첫 기준 종가는 해당 장 마감까지 등록한 공유 보유상태로 계산하고 0%부터 이번 주 순위에 포함한다. 장 마감 후 참여하면 다음 거래일이 첫 기준이다. 이후 보유상태 변경은 다음 거래일부터 적용한다. 등록 전 날짜를 제외한 해당 주의 일별 수익률을 복리로 누적하며 다음 주에 새로 시작한다. 첫 기준일 이전 가격이 없어도 0% 집계가 가능하다.
- 전체 누적 수익률과 참여 후 별도 수익률은 계산·응답하지 않는다. 일별 응답의 `cumulativeReturnRate`는 조회한 주 안의 누적 성과만 뜻한다. 기존 확정 JSON의 전체 누적 필드는 과거 이력 호환을 위해 보존하고 새 확정 버전에서는 null로 둔다. 비참여·비공개·종가 누락 기간은 연속 성과에 이어 붙이지 않는다. MDD는 최근 8주, 변동성은 최근 20거래일이며 기록이 부족하면 `null`이다.
- `market_trading_days`의 거래일 달력과 `collected_at`을 따로 확인한다. 한 날짜의 가격 행이 있다고 수집 완료나 휴장으로 판단하지 않는다. 이번 주 종가가 수집 대기 중이면 직전 수집일까지의 잠정 순위를 기준일과 함께 유지하되, 그 이전 거래일이 누락된 참가자의 순위는 보류한다. 마지막 실제 거래일의 수집·참가자 계산 완료 후 주간 순위가 확정된다.
- V31의 2026년 달력을 초기값으로 사용하고, [한국거래소 공식 휴장일](https://open.krx.co.kr/contents/MKD/01/0110/01100305/MKD01100305.jsp)에서 전년도·올해·다음 해의 발표된 달력을 자동 갱신한다. Dev·Prod는 서버 시작 시와 매일 06:05·18:05(서울)에 실행한다. `MARKET_CALENDAR_SYNC_ENABLED=false`로 중지하고 `MARKET_CALENDAR_SYNC_CRON`으로 시간을 변경할 수 있다. 별도 API 키는 필요하지 않다.
- 기존 V1~V30과 저장 기록은 보존한다. V31은 과거 가격 행을 수집 완료로 소급 인정하지 않으므로 운영 적용 후 필요한 기간을 기존 `/api/admin/stocks/prices/backfill`로 수집한다. 한 요청은 최대 31일이다. 대상 DB 적용과 운영 재집계는 별도 작업이다.
- 피드백 `V22`와 주요 인물 `V24`를 유지하기 위해 투자 리그 관련 마이그레이션은 `V26`~`V32`, 포트폴리오 거래 원장은 `V33`을 사용한다.
- 일간 결과는 실제 계산 값이 바뀔 때만 새 버전을 저장한다. `weekly_leaderboards`는 최종 순위·성과·버전·확정 시각·변경 이유를 보존한다. 공개 응답은 과거 확정값에도 현재 동의를 적용하며 닉네임·이메일·실제 금액을 확정 데이터에 복제하지 않는다.
- 기존 리그 경로를 유지한다. 프로필·순위·일별 응답은 주간 성과, 추적 시작일, 참여 가능일, 연속 기록 주차, 현재 집중도와 확정 메타데이터를 제공한다. `eligibleFrom`은 다음 월요일이 아니라 참여 시각 이후 첫 장 마감이 가능한 날짜이며 휴장 여부·실제 첫 집계일은 거래일 달력과 `trackingStartedOn`을 따른다. 최상위 성과의 `cumulativeReturnRate`와 `participationReturnRate`는 제거했다. 공개 상세와 8주 이력에 주간 순위를 제공하며 확정 주차는 보존된 순위·수익률을 사용한다. 달력 미등록·진행 중·수집 대기·수집 완료·거래일 없음은 `collectionStatus`로 구분한다. 종목 상세에는 평균 매입가 대비 평가 수익률을 추가하고 기여도는 복리에 맞춰 계산한다.
- ETF·성장 리그의 기준, 입출금·현금·TWR은 미정이므로 구현하지 않는다.

### 거래일 달력 자동 갱신과 수동 복구

공식 조회 화면에 다음 연도가 등록되고 유효한 연간 휴장일 응답을 받은 뒤에만 해당 연도의
전체 날짜를 생성한다. 주말·연말 휴장·임시 휴장일을 반영하며 미발표 연도를 평일 기준으로
미리 채우지 않는다. 윤년도 처리한다. 공식 사이트의 조회 형식 변경·오류·불완전한 응답은
기존 달력을 유지하고 다음 예약 실행에서 재시도한다. 실패는 `market-calendar.sync.failed`로 기록한다.

같은 달력을 다시 받아도 기존 `collected_at`을 유지한다. 거래 여부가 바뀐 날짜만 수집 완료
상태를 초기화하고, 달력 저장이 커밋된 뒤 기존 일간 집계·주간 확정을 다시 실행한다.
기존 종가 행만으로 수집 성공을 추정하지 않는다. 새 거래일의 종가가 없다면 기존 종가 수집이나
해당 기간 백필이 필요하며, 가격·계산 이력은 삭제하지 않는다.

아래 수동 도구는 자동 조회 장애나 자동 갱신 범위보다 오래된 날짜를 복구할 때 사용한다.
수동 정정 동안 자동 갱신 중지가 필요하다면 위 환경변수를 사용한다.

신규 연도는 주말·휴장일을 포함한 전체 날짜를, 임시 휴장 정정은 변경 날짜만 CSV로 준비한다.
[한국거래소 공식 일정](https://open.krx.co.kr/contents/MKD/01/0110/01100305/MKD01100305.jsp)과 날짜별 거래 여부를 직접 대조한다. 도구는 CSV의 형식을 검증하며 휴장일의 사실 여부를 대신 판정하지 않는다.

```csv
base_date,trading
2026-10-09,false
2026-10-12,true
```

백엔드 디렉터리에서 검토용 SQL을 생성한다. 아래 예시는 연간 등록이 아닌 날짜별 수정이다.

```bash
python3 scripts/market_calendar.py /tmp/market-calendar.csv \
  --source https://open.krx.co.kr/contents/MKD/01/0110/01100305/MKD01100305.jsp \
  --output /tmp/market-calendar.sql
python3 -m unittest discover -s scripts -p 'test_*.py'
```

수동 연간 등록에는 `--year 2027`처럼 대상 연도를 지정한다. 해당 연도의 전체 365일 또는 366일이 없거나 날짜·열이 중복/누락되면 SQL을 생성하지 않는다. 정상 자동 갱신 중에는 매년 SQL을 만들거나 배포할 필요가 없다.

1. 생성 SQL의 대상 DB·날짜·거래 여부·공식 출처를 검토하고 운영 적용 승인을 받는다. 도구는 DB에 연결하거나 SQL을 실행하지 않는다.
2. 승인된 SQL을 적용한다. 거래 여부가 같으면 `collected_at`을 유지하고, 바뀌면 초기화해 종가 수집을 다시 확인한다. 가격·보유·성과·확정 이력은 삭제하지 않는다. 이미 적용된 Flyway 파일을 수정하거나 `repair`하지 않는다.
3. 기존 관리자 `POST /api/admin/stocks/prices/backfill?from=YYYY-MM-DD&to=YYYY-MM-DD`로 필요한 종가와 직전 거래일을 수집한다. 한 요청은 최대 31일이다.
4. 수집 완료 이벤트가 일간 재집계와 주간 확정을 실행한다. 직접 `POST /api/admin/league/recalculate`를 호출해도 일간 계산과 주간 확정을 함께 수행한다.
5. 해당 주 `collectionStatus`, `confirmed`, `asOfDate`, `calculationVersion`과 [리그 메트릭·경보](../monitoring/METRICS.md)를 확인한다. 마지막 거래일이 바뀐 재확정은 `CALENDAR_RECALCULATION`, 같은 기준일의 계산 변경은 `PRICE_RECALCULATION`으로 새 버전을 남긴다.

### 관심 투자자 순위 비교

기존 팔로우 요약에 `previousRank`, `rankChange`, `comparisonWeekStart`를 추가한다. 이번 주 순위와 바로 지난주의 확정된 `WEEKLY_RETURN` 순위를 비교하며, `rankChange = previousRank - rank`로 양수는 상승이다. 지난주 확정값이나 어느 한쪽 순위가 없으면 차이는 `null`이다. 더 오래된 주차를 대신 비교하지 않으며 현재 공개·리그 참여 동의를 적용한다. 새 복기 수는 상세 공개와 종목 숨김을 확인해 현재 공개된 복기만 센다.


### 포트폴리오 일일 스냅샷

- `GET /api/users/me/portfolio/performance?from=YYYY-MM-DD&to=YYYY-MM-DD`는 저장된 과거 스냅샷을 날짜순으로 반환한다. 오늘 값은 현재 보유자산과 최근 종가로 계산하는 임시 값이며 DB에 저장하지 않는다.
- 과거 보유 수량·평균 매입가는 해당 날짜까지의 보유·거래 이력을 재생해서 복원한다. 기록된 최초 보유일보다 앞선 기간을 현재 보유자산으로 임의 소급하지 않는다.
- 보유 종목 하나라도 기준일 종가 또는 일일 손익 계산에 필요한 직전 가격 기준일의 종가가 없으면 해당 날짜를 저장하지 않는다. 매입가나 0원으로 대체하지 않으며, 이미 저장된 값도 계산 조건을 충족하지 못하면 제거한다.
- 과거 거래 등록·수정·삭제는 영향받는 최초 거래일부터 어제까지 다시 계산한다. 수정으로 거래일이 이동하면 기존 날짜와 새 날짜 중 이른 날짜부터 갱신한다.
- 종가 동기화와 관리자 기간 백필이 커밋된 뒤 스냅샷을 다시 계산한다. 기존 보유 이력의 누락된 스냅샷과 정정된 종가도 반영한다. 종가를 직접 DB에서 수정한 경우에는 다음 스냅샷 예약 실행에서 반영된다.
- `PORTFOLIO_SNAPSHOT_ENABLED`는 예약 실행을 제어하며 종가 수집 완료 후 갱신은 별도로 실행된다. 포트폴리오별 잠금으로 동시 저장을 직렬화하고, 금액이 같은 스냅샷은 다시 저장하지 않는다.
- 운영 데이터의 종가 백필은 기존 관리자 API로 실행해야 한다. 이번 변경은 운영 DB에 직접 접속하거나 백필을 실행하지 않는다.
