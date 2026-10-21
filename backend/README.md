# DAYNOMY BACKEND

Google 로그인은 OIDC 경로 하나를 사용한다. Spring의 `OidcUserService`가 조회·검증한
사용자 정보를 회원 계정과 연결하고, 로그인 성공 시 기존 JWT 쿠키를 발급한다.

## 기술 스택

| 항목        | 버전            |
| ----------- | --------------- |
| Java        | 21              |
| Spring Boot | 4.1.0           |
| Gradle      | 9.5.1           |
| Database    | PostgreSQL      |
| ORM         | Spring Data JPA |

---

## 필수 설치 항목

- **JDK 21** 설치 (`build.gradle.kts`의 Java toolchain 기준)
- **PostgreSQL 17** 설치 및 실행
- Git

---

## 환경 설정

### 1. 저장소 클론

```bash
git clone https://github.com/woowacourse-teams/2026-DAYNOMY
cd 2026-DAYNOMY/backend
```

### 2. 데이터베이스 설정

PostgreSQL에서 데이터베이스를 생성합니다.

```sql
CREATE DATABASE daynomy;
```

### 3. 애플리케이션 설정

`src/main/resources/application.yaml`은 환경변수를 읽습니다. 실행하는 `backend`
디렉터리의 비공개 `.env` 또는 실행 환경에 다음 값을 설정합니다. 실제 인증 정보는
코드와 문서에 기록하지 않습니다.

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/daynomy
DB_USERNAME=<db-username>
DB_PASSWORD=<db-password>
GOOGLE_CLIENT_ID=<google-client-id>
GOOGLE_CLIENT_SECRET=<google-client-secret>
JWT_SECRET=<jwt-secret>
AWS_S3_PUBLIC_BASE_URL=<public-image-base-url>
PUBLIC_DATA_SERVICE_KEY=<public-data-service-key>
FRONTEND_BASE_URL=http://localhost:5173
COOKIE_SECURE=false
```

스키마는 Flyway 마이그레이션으로 관리하고 JPA는 `ddl-auto: validate`로 검증합니다.
기존 DB의 적용 이력을 먼저 확인하며 `update`나 `repair`로 불일치를 우회하지 않습니다.
Google 로그인 리디렉션 URI는 `http://localhost:8080/login/oauth2/code/google`입니다.
AI 분석에는 `OPENAI_API_KEY`, 이미지 업로드에는 `AWS_REGION`, `AWS_S3_BUCKET`과
실행 환경의 AWS 인증이 필요합니다. 상세 설정 이름과 기본값은 `application.yaml`을 따릅니다.
주요 인물 게시물은 운영 환경에서 매일 오전 6시(Asia/Seoul)에 등록된 X·Threads·YouTube
공식 프로필을 수집해 승인 대기 후보로 저장합니다. `PERSON_POST_COLLECTION_ENABLED`와
`PERSON_POST_COLLECTION_CRON`으로 실행 여부와 시각을 변경합니다. 자동 수집은 기본적으로
오늘을 포함한 최근 3일만 조회하며 `PERSON_POST_COLLECTION_LOOKBACK_DAYS`로 기간을 바꿀 수
있습니다. 관리자 수동 수집은 시작일과 종료일을 지정하되 최대 31일까지 조회합니다. X·Threads에는
`OPENAI_API_KEY`, YouTube에는 `YOUTUBE_API_KEY`가 필요합니다. 후보는 관리자가 승인해야
공개 게시물로 전환됩니다. 관리자는 후보를 개별 처리하거나 한 번에 최대 100개까지 선택해
일괄 승인·거절할 수 있으며, 일부 후보가 실패해도 나머지 후보는 계속 처리됩니다.

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

# 코드 규칙과 테스트 검증
./gradlew spotlessCheck test

# PR 검증
./gradlew clean build
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
주간 확정도 이 세 종류만 생성한다. `/api/league/weeks`, 순위·일별 조회와 공개 주간 이력은 KST 기준 이번 주를 포함한 최근 3주를 제공한다. 이전 계산·확정 기록은 보존한다. 같은 순위 점수는 공동 순위이며 다음 순위는 한 단계씩 증가한다(1·1·2위).

투자 리그 참여 신청·설정 저장에는 공유 포트폴리오의 모든 종목에 보유 이유가 필요하다.
숨긴 종목도 포함하며 기존 보유 이유·판단 기록을 인정한다. 상세 공개는 참여 조건이 아니다.
참여 중 새 종목을 가져올 때는 `holdings[].decision.reason`을 함께 보내야 하며,
자산과 보유 판단을 하나의 트랜잭션으로 저장한다. 참여 전의 기존 가져오기 요청도 지원한다.
보유 이유만 필수이고 나머지 판단 항목·결과 복기는 선택이다. 보유 기간·허용 하락률의
미입력을 허용하는 V32 마이그레이션을 배포 시 적용해야 한다. 새 기록 추가는 기존 판단을 덮어쓰지 않는다.
본인 투자 기록의 보유 이유는 `PUT /api/users/me/portfolio/decisions/{transactionId}/reason`에
`{"reason":"수정할 보유 이유"}`를 보내 수정한다. 이유는 공백을 제외한 내용이 있어야 하며 최대 500자다.
최초 수정 전에 원래 이유를 `original_decision_reason`에 보존한다. 같은 `requestKey`의 재요청은
수정된 이유가 아닌 최초 요청과 비교하므로, 이유를 여러 번 수정해도 원래 요청은 기존 기록과
현재 이유를 반환하고 같은 키에 다른 이유를 보내면 409를 반환한다. 기존 기록은 첫 수정 시
원래 이유를 보존하며, 배포에는 `V55__preserve_original_decision_reason.sql` 마이그레이션이 필요하다.
응답은 수정된 투자 기록과 기존 복기이며, 다른 사람의 기록은 404로 처리한다.
기록 ID·작성일·매수/매도/보유 유형·수량·가격·선택 판단 항목·복기는 유지한다.
원본·공유 보유 수량과 수익률은 변경하지 않으며, 수정 내용은 공개 중인 기록에도 반영된다.
본인 복기는 `PUT /api/users/me/portfolio/decisions/{transactionId}/reviews/{reviewId}`로 수정한다. 최초 판단과 복기 작성 시각은 유지하고 `updatedAt`을 응답한다.

## 투자 리그 집계

- Dev·Prod는 종가 자동 수집과 리그 집계를 기본으로 실행한다. 금융위원회 [주식시세정보](https://www.data.go.kr/data/15094808/openapi.do)는 제공 지연을 고려해 기준일 다음 영업일 14시부터 수집 가능하다고 판단하며 기본 월~금 14시 30분·18시 30분에 실행한다. 거래일 달력의 미수집 날짜에서 매 실행 최대 10일을 확인한다. 최신 미수집일을 우선하고 나머지는 DB에 저장한 마지막 시도 시각이 오래된 순서로 골라, 실패한 날짜가 계속 있어도 다른 누락일을 자동 수집한다. 확보한 종가는 날짜순으로 저장하고 가장 이른 수집일부터 리그를 재집계한다. 이미 수집한 날짜의 가격 정정에는 기간별 백필을 사용한다. 수집할 날짜가 없으면 외부 API와 재집계를 호출하지 않는다. KOSPI·KOSDAQ·ETF 중 데이터가 비어 있으면 수집 완료로 처리하지 않고 다음 실행에서 재시도한다. 제공처의 갱신이 늦으면 다음 예약 실행까지 수집 대기로 표시하며 당일 순위 표시를 보장하지 않는다. `STOCK_PRICE_SYNC_CRON`으로 시간을 변경하고 `STOCK_PRICE_SYNC_ENABLED=false`, `LEAGUE_CALCULATION_ENABLED=false`로 각각 중지할 수 있다. 로컬 기본값은 꺼짐이다. 예약 집계는 참가자별 마지막 계산일과 영향받은 주차부터, 가격 동기화는 가장 이른 변경일부터 다시 계산한다. 동기화 후 집계가 실패하면 다음 예약 실행에서 같은 범위를 재시도하고 서버 시작 직후 전체 이력을 재집계한다. 달력 정정과 수동 `/api/admin/league/recalculate`는 전체 거래일·주차를 재계산한다. 참가자별 트랜잭션으로 실패를 격리하며, 실패가 있으면 주간 순위를 확정하지 않는다.
- 관리자 종가 동기화 응답의 수신·신규·수정·건너뜀은 이번 실행에서 수집 완료한 모든 날짜의 합계다. `synchronizedDateCount`는 완료 날짜 수, `pendingDateCount`는 오늘까지의 미수집 거래일 수, `failedDateCount`는 이번 실행에서 제공처 조회 또는 저장에 실패한 날짜 수다. 일부 날짜만 성공해도 완료한 날짜부터 리그를 재집계하고 실패 날짜는 다음 자동 실행에서 재시도한다.
- 주간 확정 성능 측정: `LEAGUE_BENCHMARK=true LEAGUE_BENCHMARK_PROFILES=1000 LEAGUE_BENCHMARK_YEARS=3 ./gradlew test --tests 'org.grit.daynomy.league.service.LeagueFinalizationBenchmarkTest'`를 실행한다. PostgreSQL에 합성 이력을 넣고 결과를 `build/reports/league-finalization-benchmark.txt`에 기록한다. 2026-10-13 로컬 측정은 1,000명·3년·일별 790,000행에서 변경 주차 첫 실행 32.45초, 재실행 29.80초였다. 운영 DB의 실제 이력과 하드웨어에서 이 지표를 다시 확인해야 한다.
- 금액 하한은 없다. 신규 참여자의 첫 기준 종가는 해당 장 마감까지 등록한 공유 보유상태로 계산하고 0%부터 이번 주 순위에 포함한다. 장 마감 후 참여하면 다음 거래일이 첫 기준이다. 이후 보유상태 변경은 다음 거래일부터 적용한다. 등록 전 날짜를 제외한 해당 주의 일별 수익률을 복리로 누적하며 다음 주에 새로 시작한다. 첫 기준일 이전 가격이 없어도 0% 집계가 가능하다.
- 전체 누적 수익률과 참여 후 별도 수익률은 계산·응답하지 않는다. 일별 응답의 `cumulativeReturnRate`는 조회한 주 안의 누적 성과만 뜻한다. 기존 확정 JSON의 전체 누적·8주 필드는 과거 이력 호환을 위해 보존하고 새 확정 버전에서는 null로 둔다. 공개 API는 `threeWeekReturnRate`를 사용한다. 비참여·비공개·종가 누락 기간은 연속 성과에 이어 붙이지 않는다. MDD와 최대 종목 비중은 최근 3주, 변동성은 최근 20거래일이며 기록이 부족하면 `null`이다.
- `market_trading_days`의 거래일 달력과 `collected_at`을 따로 확인한다. 한 날짜의 가격 행이 있다고 수집 완료나 휴장으로 판단하지 않는다. 이번 주 종가가 수집 대기 중이면 직전 수집일까지의 잠정 순위를 기준일과 함께 유지하되, 그 이전 거래일이 누락된 참가자의 순위는 보류한다. 마지막 실제 거래일의 수집·참가자 계산 완료 후 주간 순위가 확정된다.
- V31의 2026년 달력을 초기값으로 사용하고, [한국거래소 공식 휴장일](https://open.krx.co.kr/contents/MKD/01/0110/01100305/MKD01100305.jsp)에서 전년도·올해·다음 해의 발표된 달력을 자동 갱신한다. Dev·Prod는 서버 시작 시와 매일 06:05·18:05(서울)에 실행한다. `MARKET_CALENDAR_SYNC_ENABLED=false`로 중지하고 `MARKET_CALENDAR_SYNC_CRON`으로 시간을 변경할 수 있다. 별도 API 키는 필요하지 않다.
- 기존 V1~V30과 저장 기록은 보존한다. V31은 과거 가격 행을 수집 완료로 소급 인정하지 않는다. V45는 미수집일의 마지막 수집 시도 시각을 추가하며, 적용 후 과거 미수집 거래일은 예약 실행으로 순차 수집한다. 수동 백필은 가격 정정이나 긴급 복구용이다. 대상 DB 적용과 운영 재집계는 별도 작업이다.
- 피드백 `V22`와 주요 인물 `V24`를 유지하기 위해 투자 리그 관련 마이그레이션은 `V26`~`V32`, 포트폴리오 거래 원장은 `V33`을 사용한다.
- 일간 결과는 실제 계산 값이 바뀔 때만 새 버전을 저장한다. `weekly_leaderboards`는 최종 순위·성과·버전·확정 시각·변경 이유를 보존한다. 공개 응답은 과거 확정값에도 현재 동의를 적용하며 닉네임·이메일·실제 금액을 확정 데이터에 복제하지 않는다.
- 기존 리그 경로를 유지한다. 프로필·순위·일별 응답은 주간 성과, 추적 시작일, 참여 가능일, 연속 기록 주차, 현재 집중도와 확정 메타데이터를 제공한다. `eligibleFrom`은 다음 월요일이 아니라 참여 시각 이후 첫 장 마감이 가능한 날짜이며 휴장 여부·실제 첫 집계일은 거래일 달력과 `trackingStartedOn`을 따른다. 최상위 성과의 `cumulativeReturnRate`와 `participationReturnRate`는 제거했다. 공개 상세와 최근 3주 이력에 주간 순위를 제공하며 확정 주차는 보존된 순위·수익률을 사용한다. 달력 미등록·진행 중·수집 대기·수집 완료·거래일 없음은 `collectionStatus`로 구분한다. 종목 상세에는 평균 매입가 대비 평가 수익률을 추가하고 기여도는 복리에 맞춰 계산한다.
- ETF·성장 리그의 기준, 입출금·현금·TWR은 미정이므로 구현하지 않는다.

### 거래일 달력 자동 갱신과 수동 복구

공식 조회 화면에 다음 연도가 등록되고 유효한 연간 휴장일 응답을 받은 뒤에만 해당 연도의
전체 날짜를 생성한다. 주말·연말 휴장·임시 휴장일을 반영하며 미발표 연도를 평일 기준으로
미리 채우지 않는다. 윤년도 처리한다. 공식 사이트의 조회 형식 변경·오류·불완전한 응답은
기존 달력을 유지하고 다음 예약 실행에서 재시도한다. 실패는 `market-calendar.sync.failed`로 기록한다.

같은 달력을 다시 받아도 기존 `collected_at`을 유지한다. 거래 여부가 바뀐 날짜만 수집 완료
상태를 초기화하고, 달력 저장이 커밋된 뒤 기존 일간 집계·주간 확정을 다시 실행한다.
기존 종가 행만으로 수집 성공을 추정하지 않는다. 새 거래일의 종가가 없으면 예약 수집이
자동으로 채우고 실패한 날짜는 다음 실행에서 재시도한다. 가격·계산 이력은 삭제하지 않는다.

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
- 차트는 먼저 `POST /api/users/me/portfolio/performance/prepare?from=YYYY-MM-DD&to=YYYY-MM-DD`를 호출한다. 응답은 `READY`, `PROCESSING`, `FAILED` 중 하나이고, `PROCESSING`이면 다시 조회한다. 실패 후 재시도는 `retry=true`를 보낸다. 서버는 조회 기간을 최근 5년으로 제한하고, 해당 기간 실제 보유한 국내·미국 주식·ETF의 공용 종가를 확인한다. 확인되지 않은 범위만 종목별 과거 API에서 수집하며 이미 저장된 `(종목, 날짜)` 행은 재사용한다. 서버 재시작 후 첫 조회에서도 스냅샷을 다시 확인한다.
- 국내 거래일은 저장된 KRX 달력(없는 연도는 KRX 휴장일 조회), 미국 거래일은 EODHD 거래소 휴장일로 확인한다. 거래일이 없는 구간은 정상 완료하고, 실제 거래일의 종가가 빠졌다면 그 기간 전체를 수집 완료로 표시하지 않고 재시도 대상으로 남긴다.
- 과거 보유 수량·평균 매입가는 해당 날짜까지의 보유·거래 이력을 재생해서 복원한다. 기록된 최초 보유일보다 앞선 기간을 현재 보유자산으로 임의 소급하지 않는다.
- 보유 종목 하나라도 기준일 종가 또는 일일 손익 계산에 필요한 직전 가격 기준일의 종가가 없으면 해당 날짜를 저장하지 않는다. 매입가나 0원으로 대체하지 않으며, 이미 저장된 값도 계산 조건을 충족하지 못하면 제거한다.
- 과거 거래 등록·수정·삭제는 영향받는 최초 거래일부터 어제까지 다시 계산한다. 수정으로 거래일이 이동하면 기존 날짜와 새 날짜 중 이른 날짜부터 갱신한다.
- 종가 동기화와 관리자 기간 백필이 커밋된 뒤 스냅샷을 다시 계산한다. 기존 보유 이력의 누락된 스냅샷과 정정된 종가도 반영한다. 종가를 직접 DB에서 수정한 경우에는 다음 스냅샷 예약 실행에서 반영된다.
- `PORTFOLIO_SNAPSHOT_ENABLED`는 예약 실행을 제어하며 종가 수집 완료 후 갱신은 별도로 실행된다. 포트폴리오별 잠금으로 동시 저장을 직렬화하고, 금액이 같은 스냅샷은 다시 저장하지 않는다.
- 최신 종가는 예약 동기화로 채우고, 과거 차트 범위의 종가는 조회 시 필요한 종목만 비동기로 수집한다. 관리자 기간 백필은 가격 정정이나 긴급 복구에도 사용할 수 있다. 거래창은 과거 거래일에도 최근 종가를 수정 가능한 참고 단가로 유지하며, 실제 체결가와 다를 수 있음을 표시한다.

### 리그 보유 이유와 기존 계약

- 보유 이유는 종목별 최신 판단 기록을 우선하고 판단이 없으면 기존 공유 보유자산의 `reason`을 사용합니다. 내 공유 자산·공개 상세·참여 자격이 같은 규칙을 사용하며 최초 판단과 과거 기록은 수정하지 않습니다.
- 공유 자산과 공개 상세는 공통 `StockPriceService.pricesAt`으로 같은 기준일의 종가를 일괄 조회합니다. 숨긴 자산도 비중 분모에 포함하며 누락 가격은 값을 만들지 않습니다.
- 리그 보유 판단 클래스·서비스·DTO는 `InvestmentDecision` 이름으로 정리했습니다. 저장 테이블 `portfolio_transactions`, 기존 API 경로·JSON 필드와 과거 BUY/SELL 값은 호환을 위해 유지합니다.
- 현재 화면에서 사용하지 않는 최초 보유자산 추가 `POST /api/users/me/portfolio/holdings`(`POST /api/portfolio/holdings` 포함)와 기간별 종가 조회 `GET /api/stocks/prices`를 제거했습니다. 새 종목은 기존 매수 거래 API로 등록하고 기간별 성과는 포트폴리오 성과 API로 조회합니다. 기존 보유잔고·변경 이력·종가는 보존합니다.

### 엔티티 생성·수정 시각

- `MarketTradingDay`, `LeagueParticipation`, `WeeklyLeaderboard`, `PortfolioAnalysisCheck`, `PortfolioAnalysisEvidence`는 `BaseEntity`의 `createdAt`·`updatedAt`을 사용합니다. Hibernate가 저장·변경을 DB에 반영할 때 기록하므로 아직 저장하지 않은 객체의 시각은 `null`입니다.
- V46은 해당 다섯 테이블에 `created_at`·`updated_at`을 추가합니다. 기존 행의 실제 생성 시각은 복원할 수 없어 마이그레이션 적용 시각으로 초기화합니다. 거래일·참여 시작·확정·수집·분석 확인 시각과 기존 데이터는 보존합니다.
- 달력 SQL 도구의 신규 등록은 DB 기본값을 사용하고, 거래 여부가 바뀌면 `updated_at`을 갱신합니다. 같은 내용 재적용은 기존 감사 시각을 유지합니다.
- `RefreshToken`은 자체 `@CreationTimestamp`로 생성 시각을 이미 기록하며 기존 저장 방식을 유지합니다.
- 운영 데이터의 종가 백필은 기존 관리자 API로 실행해야 한다. 이번 변경은 운영 DB에 직접 접속하거나 백필을 실행하지 않는다.

### 포트폴리오 거래 등록 부하 테스트

dev의 과거 매수 거래 등록 V1/V2 비교는 [부하테스트 문서](loadtest/README.md)를 따른다. 데이터 복원·53명 토큰 발급은 측정 전에 별도 준비하고, 기존 `k6-dev.yml`에서 k6를 직접 실행한다. Actions에는 DB 비밀번호나 JWT 서명키를 전달하지 않는다.

`script`에 `backend/loadtest/transactions-v1.js` 또는 `backend/loadtest/transactions-v2.js`, `params`에 기준일·과거 개월 수·RPS·측정 시간을 넣는다. 한 실행에서 한 RPS를 측정하며 워밍업·그룹별 지표·오류 중단은 유지한다. `run-loadtest.py`, 설정 JSON, `ACTION` 모드는 제거했다. **Run workflow는 실제 부하 실행**이므로 데이터·토큰·서버 상태를 준비한 뒤 사용한다.

`POST /api/v2/users/me/portfolio/transactions`는 V1과 같은 요청·응답 형식을 사용한다. 회원·포트폴리오 잠금과 단일 트랜잭션을 유지하면서 거래 이력·기존 스냅샷을 일괄 조회하고, 종가는 최대 31개 재계산 기준일 단위로 조회한다. 현재 보유자산과 과거 스냅샷은 응답 전에 갱신되며, 가격 누락·반올림·실패 시 롤백 규칙은 V1과 동일하다. V1 API와 수정·삭제 경로는 그대로 사용한다.

### 미국 주식·ETF 종목 및 일별 종가 (EODHD)

미국 주요 거래소(NASDAQ, NYSE, NYSE Arca, NYSE American, BATS)의 USD 보통주와 ETF를 수집한다.
공급자의 거래소·유형 분류를 기준으로 OTC, 우선주, 뮤추얼펀드, 워런트를 제외한다.
공급자가 보통주로 잘못 분류한 상품은 포함될 수 있다. 종목코드는 `AAPL.US`, `VTI.US`처럼 저장한다.
종목명은 공급자 원문을 사용하며 한국어 번역은 제공하지 않는다.
미국 종목은 `(category, asset_code)`로 식별한다. 공급자 목록에는 같은 ISIN을 공유하는 티커가 있어
미국 ISIN은 중복 가능한 참고 정보로 저장하며, 국내 종목의 ISIN 고유 제약은 유지한다.

백엔드 실행 환경 또는 로컬의 Git 추적 제외 파일 `backend/.env`에 `EODHD_API_TOKEN`을 설정한다.
키가 없어도 애플리케이션은 시작되며 미국 수집 요청만 `EODHD_API_NOT_CONFIGURED`(503)로 거부한다.
키는 브라우저에 전달하지 않는다. EODHD 오류의 원본 URL·응답·예외는 키 노출 방지를 위해 로그에 남기지 않는다.

관리자 인증과 CSRF 토큰을 사용해 아래 순서대로 호출한다.

관리자 화면의 **주식·ETF 동기화**에서 **미국 종목 적재 → 미국 종가 가져오기** 순서로 실행할 수 있다.
미국 거래일 입력을 비우면 최근 종가를 가져오고, 날짜를 선택하면 해당 날짜만 재수집한다.
화면에서 기준일과 처리 건수, 진행 상태 및 오류를 확인할 수 있다.

1. `POST /api/admin/stocks/us/sync`: 현재 상장 종목 목록 동기화. 생성·갱신·상장폐지 처리 수를 반환한다.
2. `POST /api/admin/stocks/us/prices/sync`: 뉴욕 기준 어제부터 최대 10일을 역순으로 조회해 최근 제공 종가를 저장한다.
3. `POST /api/admin/stocks/us/prices/sync?date=2026-10-16`: 지정한 과거 날짜만 수집한다. 휴장 등으로 데이터가 없으면 다른 날짜로 대체하지 않고 오류를 반환한다. 누락된 날짜의 재수집에도 사용한다.

`GET /api/stocks?q=AAPL&country=US`로 미국 종목을 검색한다. `country` 생략 시 기존 국내 검색(`KR`)을 유지한다.
검색 응답에 `currency`(KRW/USD)를 추가했다. `GET /api/stocks/{assetId}/price`도 같은 필드를 반환하며,
`baseDate`는 공급자가 반환한 미국 거래일이다. 가격은 `adjusted_close`가 아닌 실제 `close`를 사용하며 소수점 4자리까지 저장한다.
0·음수·누락 가격은 저장하지 않는다. 국내 수집은 미국 종목 상태를 변경하지 않고, 미국 수집도 국내 종목 및 국내 거래일 달력의 수집 완료 상태를 변경하지 않는다.

자동 수집은 기본적으로 꺼져 있다. `US_STOCK_SYNC_ENABLED=true`를 설정하면 뉴욕 시간 매일 06:15에
종목 목록과 최근 종가를 차례로 수집한다. `US_STOCK_SYNC_CRON`으로 일정을 변경할 수 있고 서머타임은
`America/New_York` 시간대로 처리한다. 실패 시 종목 목록/가격 API를 각각 다시 실행할 수 있다.
`.env` 변경은 백엔드를 다시 시작해야 적용된다. 예약 시각에 서버가 실행 중이어야 하며, 로컬 설정은
배포 환경에 전달되지 않으므로 배포 서버에도 토큰과 활성화 값을 별도로 설정해야 한다.
일별 수집은 최근 종가만 대상으로 하므로 여러 거래일 동안 중단되었다면 날짜 지정 API로 누락일을 재수집한다.

전체 종목 목록 1회는 1 API call, 하루치 미국 전체 종가 1회는 100 API calls를 소비한다.
날짜를 생략한 종가 수집은 빈 응답이 이어질 때 최대 10회(1,000 calls) 요청할 수 있다.
개인용 EOD Historical All-World 플랜에 이 두 기능이 포함된다.
공급자 문서: https://eodhd.com/financial-apis/exchanges-api-list-of-tickers-and-trading-hours
및 https://eodhd.com/financial-apis/bulk-api-eod-splits-dividends

거래창에서 국내/미국을 전환해 미국 주식·ETF를 검색하고, 저장된 최근 종가를 기본 거래 단가로 사용할 수 있다.
단가는 실제 체결가로 수정할 수 있고 종가의 기준일·통화를 표시한다. 과거 거래일을 선택해도 기본 단가는 최근 종가이므로
당시 체결가를 직접 확인해야 한다. 미국 거래의 단가·수수료·세금은 USD, 적용 환율은 1 USD당 KRW다.

`GET /api/exchange-rates/usd-krw?date=YYYY-MM-DD`는 EODHD `USDKRW.FOREX`의 거래일 이하 최근 일별 종가를
참고 환율로 반환한다(`requestedDate`, `baseDate`, `rate`, `baseCurrency=USD`, `quoteCurrency=KRW`).
뉴욕 당일의 미확정 환율을 사용하지 않으며, 휴일에는 최대 14일 전까지 찾는다. 최근 요청은 6시간 캐시하고,
조회 당시 이미 확정된 과거 요청만 영구 재사용한다. 최근 날짜에 저장한 잠정 값은 날짜가 지나도
영구 캐시로 바뀌지 않으며, 만료 후 다시 조회한다. 증권사의 실제 환율은 거래창에서 직접 수정할 수 있다.
환율을 임의로 1로 대체하지 않는다. 참고 환율이 확정적으로 없으면 직접 입력한 양수 환율로 거래를 기록하고
평가는 대기한다. 과거 스냅샷 재계산에 필요한 환율을 조회하다 공급자 통신 오류가 발생하면 거래 변경을
시작하기 전에 오류를 반환하며, 기존 원장·보유·스냅샷을 유지한다.
공개 환율 조회의 지원 요청일은 `2000-01-01`부터 한국 기준 오늘까지다. 서버 인스턴스당 최근 60초에
최대 120회 요청을 허용하고 초과 요청에는 `429 EXCHANGE_RATE_RATE_LIMITED`를 반환한다.
빈 환율 결과도 6시간 동안 최대 1,024개 날짜를 메모리에 캐시해 같은 날짜의 반복 외부 호출을 줄인다.
이 제한과 빈 결과 캐시는 인스턴스별이며 재시작 시 초기화된다. 외부 통신 실패는 빈 결과로 캐시하지 않는다.
동시 최초 조회는 PostgreSQL upsert로 저장하며, 늦게 완료된 과거 요청이 더 새 캐시를 덮어쓰지 않는다.
[공급자 환율 문서](https://eodhd.com/financial-apis/list-supported-forex-currencies).

`EXCHANGE_RATE_SYNC_ENABLED=true`로 환율 자동 수집을 활성화한다. 기본은 비활성화이며,
뉴욕 시간 매일 06:10(한국 기준 미국 서머타임 기간 19:10, 미국 표준시 기간 20:10)에 실행한다.
`EXCHANGE_RATE_SYNC_CRON`으로 일정을 변경할 수 있다. 미국 종가 수집(06:15)과 별도 작업이므로
환율 수집 실패가 종목·종가 수집을 막지 않는다. 예약 수집은 6시간 캐시를 건너뛰고 EODHD를 다시 조회해
한국 기준 오늘의 참고 환율과 실제 기준일을 저장한다. 휴일에는 최근 제공된 일별 종가를 사용하며,
실패 시 기존 DB 값을 보존하고 실패 로그를 남긴다. 다음 예약 실행 또는 캐시 만료 후 조회 시 다시 시도한다.
서버 실행 중에만 예약 작업이 동작하며, 중단 중 놓친 실행은 재생하지 않는다. 거래창의 요청 시 조회는 유지한다.
이 작업은 참고 환율 테이블만 갱신하며, 저장된 거래의 적용 환율·매입 원가·실현손익을 변경하지 않는다.
`.env` 변경 후 백엔드를 재시작해야 하며 배포 환경에서도 활성화 값을 별도로 설정해야 한다.

V1/V2 거래 등록과 거래 수정 API의 선택 필드 `exchangeRate`는 미국 거래에서 필수(최대 소수점 6자리)이며,
국내 거래는 생략 또는 1만 허용한다. `unitPrice`는 최대 소수점 4자리, 수수료·세금은 2자리다.
원장에는 달러 금액과 거래 환율을 함께 보관한다. 매수 원가는 `(단가 × 수량 + 수수료 + 세금) × 환율`,
매도 순수입은 `(단가 × 수량 - 수수료 - 세금) × 환율`로 환산하고, 원화 이동평균 원가를 차감해 실현손익을 구한다.
보유·이력의 `averagePurchasePrice`와 평가액·손익·합계는 KRW이고, `closePrice`는 종목 원통화다.
저장 보유·보유 평가·보유 이력 응답은 `averagePurchasePriceCurrency="KRW"`를 별도로 반환한다.
`currency`는 종목의 원통화로 유지하므로 USD 종목의 평균 매입가 표시에 사용하지 않는다.
보유 정정 API의 `SavedPortfolioHoldingUpdateRequest.averagePurchasePrice` 역시 **KRW 기준**이며,
USD 단가를 그대로 넣으면 안 된다. 실제 거래 단가·환율을 바꾸려면 거래 수정 API를 사용한다.
미국 거래 이력의 `currency=USD`, `exchangeRate`를 함께 반환한다. 원가는 소수점 2자리 HALF_UP으로 보관한다.

현재 평가와 과거 자산 추이는 각 종가 기준일의 참고 환율을 적용한다. 필요한 환율을 날짜별로 한 번씩
포트폴리오 트랜잭션 시작 전에 준비하고, 계산에는 환율 맵을 전달한다. 외부 HTTP 요청은 DB 트랜잭션과
회원·포트폴리오 잠금을 잡지 않은 상태에서 수행하며, 환율 저장만 짧은 DB 트랜잭션으로 처리한다.
잠금을 얻은 뒤에는 기존처럼 최신 원장을 다시 조회한다. 준비 이후 동시 변경으로 추가된 날짜에 환율이
없으면 그 날짜의 스냅샷을 보존하고 다음 변경·예약 수집 때 재계산한다.
스냅샷 후보일과 이전 거래일은 해당 포트폴리오의 보유·이력 자산 가격에서 구한다.
종가나 환율이 없으면 현재 평가는 대기/부분 평가로 표시하고, 과거 스냅샷을 임의 가격으로 만들지 않는다.
이미 저장된 스냅샷은 가격·환율 누락 시 마지막 계산값을 보존한다. 따라서 데이터가 복구되어 재계산될
때까지 최근 거래 변경을 반영하지 않을 수 있다. 해당 날짜에 실제 보유 자산이 없는 경우에만 삭제한다. 거래 수정·삭제 시 저장한 거래 환율을 사용해 원장을 다시 계산한다.
비회원 거래도 USD·환율을 브라우저 저장소에 유지하고 원화 원가와 실현손익을 재생한다.
리그는 국내 원화 종목만 지원하므로 미국 종목의 리그 등록은 거부한다. 외화 현금 잔고·별도 환전 거래와 소수점 수량은
이번 범위에 포함하지 않는다. 지연 시세와 실시간 스트리밍은 다음 작업으로 남겨두며 일별 확정 종가와 분리한다.

검증은 MockWebServer와 PostgreSQL Testcontainers를 사용한다. 실제 EODHD 계정의 권한·커버리지와
데이터 누락 여부는 키 설정 후 최초 수집 결과로 확인해야 한다.

2026-10-20(KST) 실제 API 검증에서는 별도 PostgreSQL Testcontainer에 주식 6,122개와 ETF 5,437개를
저장하고, 2026-10-16 종가 11,439개(주식 6,018개, ETF 5,421개)를 저장했다. 나머지 120개는 해당 날짜의
유효한 종가가 없어 저장하지 않았다. 뉴욕 기준 당일을 제외하고 이전 날짜부터 조회하므로 당시에는
10월 18·17일의 빈 응답을 건너뛰어 16일을 선택했다. AAPL·MSFT·NVDA·SPY·QQQ·VOO·VTI의
검색/종가 HTTP API에서 USD 금액과 기준일을 확인했고, 같은 날짜 재수집 시 추가 생성·갱신은 0건이었다.
이 검증은 애플리케이션에 설정된 실제 DB를 변경하지 않았으며, 종목 수와 가격 커버리지는 수집일에 따라 달라진다.

동일한 임시 DB에서 실제 `USDKRW.FOREX` 환율(2026-10-16, 1 USD = 1,334.61 KRW)과 테슬라 종가
354.72 USD를 이용한 거래도 검증했다. 수수료 없이 2주 매수 후 1주를 364.72 USD에 매도하는 테스트에서
원화 평균 원가 473,412.86원, 실현손익 13,346.10원이 저장됐다. 매도가는 계산 검증을 위해 정한 입력값이며 실제 체결 내역이 아니다.

### V51 배포 전 운영 확인

`stock_daily_prices.close_price`의 정밀도 변경은 테이블 재작성과 강한 잠금을 수반할 수 있다.
운영 DB의 실제 크기와 잠금 대기, 운영 데이터 복제본의 마이그레이션 시간을 확인한 후 저부하 시간에 배포한다.
다음 조회는 읽기 전용이며 `estimated_rows`는 통계 기반 추정치다.

```sql
SELECT reltuples::bigint AS estimated_rows,
       pg_size_pretty(pg_total_relation_size(oid)) AS total_size
FROM pg_class
WHERE oid = 'stock_daily_prices'::regclass;
```

배포용 Flyway 연결에는 PostgreSQL 세션 옵션 `lock_timeout=5s`를 적용해 잠금을 오래 기다리지 않게 한다
(예: JDBC URL의 `options=-c%20lock_timeout%3D5s`; 기존 URL 옵션이 있으면 `&`로 추가).
타임아웃이 발생하면 배포를 중단하고 장기 트랜잭션과 작업 시간대를 확인한 후 재실행한다.
`lock_timeout`은 잠금 획득 대기에만 적용되므로 테이블 재작성 실행 시간을 제한하지 않는다.
운영 행 수·소요 시간은 로컬 Testcontainers 결과로 대신 판단할 수 없으며, 이번 변경에서 운영 DB에
접속하거나 마이그레이션을 실행하지 않았다.

### 포트폴리오 실패 로그 점검 (#551)

대시보드가 호출하는 거래 등록·수정·삭제, 보유정보, 계산, 추이와 스냅샷 경로의 실패 로그 및 공통 HTTP 요청 추적을 보완한다. AI 처리 로그 추가와 로그 레벨 변경은 작업 범위에서 제외한다. API 동작·응답과 거래 처리 구조는 변경하지 않는다.

| 실패 사례 | 확인할 로그 |
| --- | --- |
| 거래·계산의 예상하지 못한 예외 | `http.request.failed` (ERROR)의 `errorCode`, `errorType`, 호출 스택. 동일 `requestId`·`traceId`의 `http.request.completed`에서 메서드·URI·HTTP 상태·처리 시간을 확인한다. |
| 잘못된 입력·보유 수량 부족·존재하지 않는 거래 | 기존 `http.request.completed` (INFO)에 `errorCode`를 추가한다. 예상 가능한 4xx에 별도 WARN/ERROR를 중복 기록하지 않는다. |
| 스냅샷 개별 생성·종가 수집 후 갱신 | `portfolio.snapshot.failed` (ERROR), 개별 생성은 `portfolioId`·`baseDate` 포함. 다른 포트폴리오 처리는 계속하고 완료 로그의 `failureCount`로 실패 건수를 확인한다. 스케줄러 작업에는 HTTP 요청 ID가 없을 수 있다. |
| 공통 비동기 HTTP 요청 | ASYNC 재디스패치에서 최초 요청의 `requestId`·`automation`을 복원한다. `traceId`·`spanId`는 Micrometer가 제공한 값을 유지한다. 완료 로그는 최초 요청의 MDC를 사용해 AsyncListener에서 한 번만 남긴다. |

스냅샷 스케줄러는 실패 로그를 남긴 뒤 원래 예외를 그대로 다시 던져 기존 예외 전파 동작을 유지한다.

공통 예외와 스냅샷의 애플리케이션 로그는 예외 메시지를 제외하고 타입·프레임·원인·suppressed 예외의 호출 위치만 기록한다. 이 로그에 SQL 값이나 토큰이 그대로 출력되지 않도록 한다. 스케줄러에서 전파한 원래 예외를 기록하는 Spring 기본 오류 로그는 기존 동작을 유지한다. 요청 본문·헤더·쿼리 문자열도 로그에 추가하지 않는다.

로컬 재현은 `GlobalExceptionHandlerTest`의 POST/PATCH/DELETE 500 및 400, `RequestLoggingFilterTest`의 비동기 상관관계, `PortfolioSnapshotServiceTest`·`PortfolioSnapshotSchedulerTest`의 스냅샷 실패를 실행한다. 가짜 입력과 Mock으로 재현하며 운영 실패를 유발하지 않는다.

이 문서는 코드·로컬 테스트 확인 방법이며 운영 로그 수집 결과를 의미하지 않는다.

### 자산 추이 종가 준비

차트는 저장된 스냅샷을 먼저 조회하고 과거 종가는 별도로 준비한다. 국내 수집 범위는 주말·휴장일을 제외한 다음 영업일 14시가 지난 날짜까지로 제한한다. 준비 중이거나 일부 수집에 실패해도 저장된 그래프는 표시하며 안내 문구를 함께 보여 준다. 준비가 끝나면 성공·실패 여부와 관계없이 저장된 스냅샷을 다시 조회해 부분 수집 결과도 반영한다.
