# DAYNOMY CloudWatch 모니터링

DAYNOMY의 1차 백엔드 모니터링은 CloudWatch로 통합한다. 별도의 Prometheus 서버,
Alertmanager, Loki, Grafana는 사용하지 않는다. 프론트엔드 오류와 성능은 Sentry에서
관리한다.

## 데이터 흐름

```text
server-dev / server-prod
  ├─ AWS/EC2 기본 지표 ───────────────────────┐
  ├─ CloudWatch Agent                          │
  │   ├─ 메모리·디스크·Swap·Java 프로세스 ────┤
  │   ├─ 기존 백엔드 로그 ──> CloudWatch Logs │
  │   └─ 127.0.0.1:8081/actuator/prometheus   │
  │       └─ JVM·HTTP·HikariCP ────────────────┤
  └────────────────────────────────────────────> CloudWatch
                                                  ├─ Dashboard
                                                  └─ Alarm ─> SNS ─> Slack
```

`/actuator/prometheus`는 인터넷에 공개하거나 Prometheus 서버로 전송하지 않는다.
백엔드 EC2에 설치된 CloudWatch Agent가 `127.0.0.1`에서만 읽는다.

## 저장소 파일

| 파일 | 역할 |
|---|---|
| `cloudwatch/agent/backend-infrastructure.json` | 호스트 메모리·디스크·Swap·Java 프로세스 수집 |
| `cloudwatch/agent/backend-prometheus.json` | Actuator에서 필요한 JVM·HTTP·HikariCP 지표만 CloudWatch로 변환 |
| `cloudwatch/agent/prometheus.yaml` | 로컬 Actuator 수집 대상 정의 |
| `cloudwatch/dashboards/backend-dev.json` | dev 백엔드 CloudWatch 대시보드 본문 |
| `docs/alert-policy.md` | 1차 CloudWatch Alarm 기준 |
| `docs/runbook.md` | 알림별 확인 및 대응 절차 |

## 수집 지표

### EC2 기본 지표 (`AWS/EC2`)

- `CPUUtilization`
- `CPUCreditBalance`
- `NetworkIn`, `NetworkOut`
- `StatusCheckFailed`

### 서버와 프로세스 (`CWAgent`)

- `mem_used_percent`, `mem_available_percent`
- `disk_used_percent`, `disk_inodes_free`
- `swap_used_percent`
- `procstat_lookup_pid_count`
- `procstat_cpu_usage`, `procstat_memory_rss`, `procstat_num_threads`

### 백엔드 애플리케이션 (`DAYNOMY/Application`)

- JVM Heap: `jvm_memory_used_bytes`, `jvm_memory_max_bytes`
- GC: `jvm_gc_pause_seconds_count`, `jvm_gc_pause_seconds_sum`, `jvm_gc_pause_seconds_max`
- JVM 스레드: `jvm_threads_live_threads`
- HTTP: `http_server_requests_seconds_count`, `http_server_requests_seconds_sum`,
  `http_server_requests_seconds_max`
- DB 풀: `hikaricp_connections_active`, `hikaricp_connections_idle`,
  `hikaricp_connections_pending`, `hikaricp_connections_max`

CloudWatch 비용과 지표 카디널리티를 제한하기 위해 Actuator의 전체 지표를 보내지 않고
위 지표만 선택한다.

## 최초 적용

### 1. IAM 확인

server-dev와 server-prod의 인스턴스 프로파일에 AWS 관리형 정책
`CloudWatchAgentServerPolicy`가 있어야 한다. 기존 로그가 CloudWatch Logs에 정상적으로
들어오고 있다면 먼저 현재 권한을 확인하고, 같은 정책을 중복 추가하지 않는다.

### 2. 백엔드 확인

dev 배포 후 server-dev에서 다음 두 요청이 성공해야 한다.

```bash
curl --fail http://127.0.0.1:8081/actuator/health
curl --fail http://127.0.0.1:8081/actuator/prometheus
```

### 3. Agent 설정 배치

기존 로그 설정을 보존하기 위해 `fetch-config`로 덮어쓰지 않고 새 설정을
`append-config`로 추가한다.

```bash
sudo install -d -m 755 /opt/daynomy-monitoring/cloudwatch

sudo install -m 644 monitoring/cloudwatch/agent/backend-infrastructure.json \
  /opt/daynomy-monitoring/cloudwatch/backend-infrastructure.json

sudo install -m 644 monitoring/cloudwatch/agent/backend-prometheus.json \
  /opt/daynomy-monitoring/cloudwatch/backend-prometheus.json

sudo install -m 644 monitoring/cloudwatch/agent/prometheus.yaml \
  /opt/aws/amazon-cloudwatch-agent/var/daynomy-prometheus.yaml
```

저장소를 EC2에 checkout하지 않았다면 위 세 파일을 SSM Run Command나 배포 파이프라인으로
동일한 경로에 전달한다.

### 4. 설정 적용

```bash
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a append-config -m ec2 -s \
  -c file:/opt/daynomy-monitoring/cloudwatch/backend-infrastructure.json

sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a append-config -m ec2 -s \
  -c file:/opt/daynomy-monitoring/cloudwatch/backend-prometheus.json
```

### 5. 검증

```bash
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -m ec2 -a status

sudo tail -n 100 \
  /opt/aws/amazon-cloudwatch-agent/logs/amazon-cloudwatch-agent.log
```

적용 후 최대 2~3분 뒤 CloudWatch Metrics에서 다음 namespace를 확인한다.

- `CWAgent`: 호스트·프로세스
- `DAYNOMY/Application`: JVM·HTTP·HikariCP

## 환경 적용 순서

1. `server-dev`에 적용한다.
2. dev 대시보드와 알람을 검증한다.
3. 같은 Agent 설정을 `server-prod`에 적용한다.
4. prod 인스턴스 ID와 `environment=prod`를 사용하는 대시보드·알람을 만든다.

두 환경의 백엔드 설정에는 각각 `environment=dev`, `environment=prod` 태그가 있으므로
애플리케이션 지표가 섞이지 않는다.

## 범위 밖

- 프론트엔드 모니터링은 Sentry가 담당한다.
- DB EC2와 PostgreSQL 내부 지표는 백엔드 CloudWatch 적용을 검증한 뒤 2차로 진행한다.
- 분산 추적과 커스텀 비즈니스 지표는 이번 범위에 포함하지 않는다.
