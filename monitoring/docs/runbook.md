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
