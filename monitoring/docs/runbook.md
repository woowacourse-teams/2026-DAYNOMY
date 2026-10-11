# DAYNOMY 알림 대응 Runbook

알림을 받으면 먼저 알림 제목의 환경(`DEV` 또는 `PROD`), 상태(`FIRING` 또는
`RESOLVED`), 인스턴스를 확인한다. 운영 장애를 확인하기 위해 운영 서버를 고의로
중단하지 않는다. 장애 훈련은 dev에서만 수행한다.

## PublicEndpointUnavailable

1. 브라우저 또는 `curl`로 알림의 URL에 접속한다.
2. server EC2가 실행 중인지 확인한다.
3. Nginx와 백엔드 프로세스 상태를 확인한다.
4. 백엔드 로그에서 같은 시각의 오류를 확인한다.
5. DB 연결 실패가 함께 발생했는지 확인한다.

## BackendMetricsUnavailable

server EC2에서 다음을 확인한다.

```bash
curl --fail http://127.0.0.1:8081/actuator/health
curl --fail http://127.0.0.1:8081/actuator/prometheus
```

- 둘 다 실패: 백엔드 프로세스와 배포 상태를 확인한다.
- health만 성공하고 prometheus 실패: Actuator 설정을 확인한다.
- 둘 다 성공: Alloy 상태와 monitoring EC2로의 네트워크를 확인한다.

## ProductionEc2MetricsMissing

1. 해당 EC2 실행 상태를 확인한다.
2. EC2가 실행 중이면 Alloy 프로세스와 로그를 확인한다.
3. Alloy가 정상이면 monitoring EC2의 `9090` 접근 경로와 Security Group을 확인한다.
4. 공개 URL도 실패하면 사용자 영향이 있는 실제 장애로 우선 대응한다.

## BackendHighErrorRate

1. Grafana에서 발생 시각 전후의 요청량과 5xx 비율을 확인한다.
2. Loki에서 같은 시각의 백엔드 ERROR 로그를 확인한다.
3. 특정 URI, DB, OpenAI·공공데이터·S3 중 어디서 실패했는지 좁힌다.
4. 최근 배포 직후 시작됐다면 배포 변경점을 우선 확인한다.

## BackendHighP95Latency

1. Grafana에서 p95, 요청량, DB 풀 사용률을 함께 확인한다.
2. Hikari 대기와 PostgreSQL 연결 상태를 확인한다.
3. CPU·메모리·디스크가 동시에 악화됐는지 확인한다.
4. 느린 URI와 같은 시각의 로그를 확인한다.

## DatabaseConnectionPoolWaiting / PostgresUnavailable

1. DB EC2와 PostgreSQL 프로세스 상태를 확인한다.
2. 현재 연결 수와 장시간 실행 쿼리를 확인한다.
3. 백엔드 Hikari 사용량과 대기 요청 수를 확인한다.
4. DB 재시작은 진행 중인 작업과 데이터 영향을 확인한 뒤 수행한다.

## JvmHeapUsageHigh

1. Heap 사용률이 계속 증가하는지 또는 GC 뒤 감소하는지 확인한다.
2. 요청량 증가와 배치·뉴스 생성 작업 실행 여부를 확인한다.
3. 프로세스를 즉시 재시작하기 전에 반복 증가 여부와 관련 로그를 남긴다.

## HostDiskSpaceLow

1. 어떤 마운트 지점이 부족한지 확인한다.
2. 애플리케이션 로그, Docker 데이터, PostgreSQL 데이터 증가량을 확인한다.
3. 삭제 대상을 정확히 확인하지 않은 상태에서 재귀 삭제 명령을 실행하지 않는다.

## LeagueCalculationFailed

1. Grafana의 리그 대상·성공·실패와 일간 제외 사유를 확인한다. 참가자 실패와 자동 실행 오류를 구분한다.
2. 같은 시각의 `LEAGUE_CALCULATION_FAILED` 로그에서 예외 종류와 DB·종가 수집 상태를 확인한다. 금액·판단 원문을 장애 기록에 복사하지 않는다.
3. `MISSING_PRICE` 또는 `PRICE_COLLECTION_PENDING`이 증가하면 아래 달력·수집 상태를 확인하고 필요한 날짜를 최대 31일씩 관리자 종가 백필한다.
4. 다음 종가·달력 갱신 이벤트 또는 예약 집계가 재시도한 뒤 실패 값이 0으로 복구됐는지 확인한다. 관리자 재집계도 일간 성과 계산과 주간 확정을 함께 실행한다.

## LeagueCalendarMissing

1. 참여 기간부터 현재 주까지 `market_trading_days`가 주말·휴장일을 포함해 하루 한 행씩 있는지 확인한다. 아래 조회의 날짜 범위는 필요한 기간으로 바꾼다.

```sql
SELECT day::date AS missing_date
FROM generate_series(DATE '2026-10-01', DATE '2026-10-31', INTERVAL '1 day') AS day
LEFT JOIN market_trading_days calendar ON calendar.base_date = day::date
WHERE calendar.base_date IS NULL;
```

2. `MARKET_CALENDAR_SYNC_ENABLED`와 `market-calendar.sync.completed`·`market-calendar.sync.failed` 로그를 확인한다. 서버 시작 및 매일 06:05·18:05(서울)에 전년도·올해·다음 해의 발표된 KRX 달력을 갱신한다. 미발표 연도나 잘못된 응답을 평일 달력으로 대체하지 않는다. 공식 조회 장애나 범위 밖의 과거 날짜는 [수동 복구 절차](../../backend/README.md#거래일-달력-자동-갱신과-수동-복구)에 따라 CSV·SQL을 검토하고 승인된 대상 DB에 적용한다.
3. 추가·변경된 거래일은 종가 백필하고 자동 재집계·확정 후 누락 지표의 복구를 확인한다. 기존 마이그레이션·가격·성과 이력을 수정·삭제하지 않는다.

## LeagueFinalizationDelayed

1. 해당 주 달력이 완전하고 마지막 실제 거래일이 올바른지 확인한다. 달력 자체가 누락된 경우 `LeagueCalendarMissing`부터 해결한다.

```sql
SELECT base_date, trading, collected_at
FROM market_trading_days
WHERE base_date BETWEEN DATE '2026-10-05' AND DATE '2026-10-11'
ORDER BY base_date;
```

2. 거래일의 `collected_at`이 없으면 필요한 기간과 직전 거래일의 종가를 백필한다. 가격 행이 일부 있다는 이유로 수집 완료 값을 수동으로 채우지 않는다.
3. 수집이 끝났다면 최신 일간 결과의 미완료 사유를 집계한다. 금액·계정 정보를 조회할 필요는 없다.

```sql
WITH latest AS (
  SELECT DISTINCT ON (portfolio_id, base_date) eligible, ineligible_reason
  FROM portfolio_daily_returns
  WHERE base_date BETWEEN DATE '2026-10-05' AND DATE '2026-10-11'
  ORDER BY portfolio_id, base_date, calculation_version DESC
)
SELECT eligible, ineligible_reason, COUNT(*)
FROM latest GROUP BY eligible, ineligible_reason;
```

4. 자동 집계 뒤 해당 주 API의 `confirmed`, `asOfDate`, `calculationVersion`과 지연 값 0을 확인한다. 가격 정정은 `PRICE_RECALCULATION`, 마지막 거래일 변경은 `CALENDAR_RECALCULATION`으로 이전 버전을 보존해야 한다.

## 점검·배포 중 Silence

예정된 dev 중단이나 점검은 Alertmanager에서 해당 환경과 시간에 맞는 Silence를 먼저
등록한다. 점검 완료 후 Silence가 만료됐는지 확인한다.

## 장애 기록

실제 장애가 끝난 후 다음을 기록한다.

- 발생·탐지·복구 시각
- 사용자 영향
- 최초로 도착한 알림
- 직접 원인과 근본 원인
- 수행한 조치
- 다시 발생하지 않도록 변경할 항목
- 알림이 너무 빠르거나 늦었다면 임계값 조정 내용
