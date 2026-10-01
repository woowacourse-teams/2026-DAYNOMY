# DAYNOMY 메트릭 운영

이 문서는 메트릭 담당 범위만 다룬다. Loki와 로그 수집은 로그 담당 설정을 따른다.

## 데이터 흐름

```text
server EC2: Alloy(unix + Actuator) ─┐
database EC2: Alloy(unix + postgres) ├─ remote write ─> Prometheus
                                    │                    ├─> Grafana
public URL: Blackbox Exporter ───────────────────────────┘
                                                        └─> Alertmanager
```

monitoring EC2의 Prometheus systemd 서비스는 Alloy의 전송을 받도록 반드시
`--web.enable-remote-write-receiver` 옵션으로 실행한다. Grafana, Loki, Alloy와 동일하게
Prometheus, Alertmanager, Blackbox Exporter도 systemd 서비스로 운영한다.

## Alloy 환경변수

애플리케이션 서버는 `metrics-server.alloy`를 사용한다.

```text
DAYNOMY_ENVIRONMENT=dev
DAYNOMY_INSTANCE=ec2-daynomy-server-dev
PROMETHEUS_REMOTE_WRITE_URL=http://<monitoring-private-ip>:9090/api/v1/write
```

DB 서버는 `metrics-database.alloy`를 사용한다. DSN은 저장소에 커밋하지 않고
권한이 `600`인 환경 파일 또는 systemd credential로 주입한다.

```text
DAYNOMY_ENVIRONMENT=dev
DAYNOMY_INSTANCE=ec2-daynomy-db-dev
PROMETHEUS_REMOTE_WRITE_URL=http://<monitoring-private-ip>:9090/api/v1/write
POSTGRES_EXPORTER_DSN=postgresql://<monitor-user>:<password>@127.0.0.1:5432/daynomy?sslmode=disable
```

운영 서버는 `DAYNOMY_ENVIRONMENT=prod`와 운영 인스턴스 이름만 사용한다.

## PostgreSQL 모니터링 계정

각 DB에서 별도의 비로그인 관리 계정을 재사용하지 말고 모니터링 전용 계정을 만든다.
비밀번호는 아래 SQL에 직접 기록하거나 저장소에 남기지 않는다.

```sql
CREATE USER daynomy_monitor WITH PASSWORD '<secret>';
GRANT pg_monitor TO daynomy_monitor;
GRANT CONNECT ON DATABASE daynomy TO daynomy_monitor;
```

## 네트워크

- monitoring EC2의 `9090`은 server/db EC2의 Security Group에서만 허용한다.
- Actuator `8081`은 `127.0.0.1`에만 바인딩하므로 외부 인바운드 규칙을 만들지 않는다.
- Alertmanager `9093`, Blackbox Exporter `9115`는 monitoring EC2 내부에서만 사용한다.
- Prometheus와 Alertmanager 관리 화면을 인터넷에 공개하지 않는다.

## Slack 알림

현재 1차 알림 채널은 Slack이다. Slack에서 모니터링 전용 채널과 Incoming Webhook을
하나 만든다. Webhook URL은 저장소에 기록하지 않고 monitoring EC2에만 저장한다.

```text
/opt/daynomy-monitoring/secrets/slack_webhook_url
```

파일에는 Slack Incoming Webhook URL 한 줄만 넣고 권한을 `600`으로 제한한다.
Alertmanager systemd 서비스가 이 경로를 읽는다.

Webhook URL 자체는 팀원 간에도 공개 채널이나 문서에 남기지 않는다. 나중에 이메일을
추가하려면 각 receiver에 `email_configs`를 함께 추가하면 두 채널로 동시에 전송된다.

알림 임계값과 재전송 정책은 [docs/alert-policy.md](docs/alert-policy.md), 장애별 확인
절차는 [docs/runbook.md](docs/runbook.md)를 따른다.

## 검증 순서

1. 백엔드에서 `curl --fail http://127.0.0.1:8081/actuator/prometheus`를 실행한다.
2. Alloy UI 또는 로그에서 `backend`, `node`, `postgres` scrape 성공을 확인한다.
3. Prometheus에서 `up`, `probe_success`, `pg_up`을 조회한다.
4. 각 시계열에 `environment`, `service`, `instance` 라벨이 있는지 확인한다.
5. Grafana에서 `DAYNOMY Metrics Overview`의 dev/prod 전환을 확인한다.
6. dev 백엔드를 중지하여 Down Slack 알림과 복구 Slack 알림을 확인한다.
7. 같은 dev 장애를 유지해 4시간 뒤에만 반복 알림이 오는지 확인한다. 실제 검증에서
   4시간을 기다리기 어렵다면 dev의 `repeat_interval`을 잠시 `10m`으로 바꿔 검증한 뒤
   반드시 `4h`로 되돌린다.

## EC2 자체 장애 감지

Alloy가 함께 종료되면 `up == 0` 시계열도 더 이상 전송되지 않으므로,
`node_uname_info`가 일정 시간 동안 완전히 사라지는 조건을 별도 알림으로 사용한다.

- prod server/db: 3분간 호스트 메트릭 없음 → critical
- dev server/db: 5분간 호스트 메트릭 없음 → warning

이 알림은 중앙 Prometheus가 살아 있을 때 EC2·Alloy·네트워크 중단을 감지한다.
monitoring EC2 자체 장애 감시는 현재 범위에서 제외한다.

## 범위 밖

- 프론트엔드 오류와 성능은 Sentry에서 관리한다.
- 분산 추적과 로그 기반 알림은 이번 메트릭 범위에 포함하지 않는다.
- 실제 운영 장애를 만들지 않고 dev에서만 통제된 장애 훈련을 수행한다.
