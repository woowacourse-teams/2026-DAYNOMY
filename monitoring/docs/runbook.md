# DAYNOMY CloudWatch 알림 대응 Runbook

알림을 받으면 환경(dev/prod), 인스턴스 ID, Alarm 상태(ALARM/OK), 발생 시각을 먼저
확인한다. 운영 장애를 검증하기 위해 prod 서버를 고의로 중단하지 않는다.

## EC2 상태 검사 실패

1. EC2 콘솔에서 인스턴스가 실행 중이고 상태 검사가 통과하는지 확인한다.
2. 시스템 로그와 콘솔 스크린샷에서 부팅·커널 오류를 확인한다.
3. SSM 새 세션으로 접속을 시도한다.
4. 복구가 필요하면 재부팅을 먼저 사용하고, 종료 또는 볼륨 분리는 영향 범위를 확인한
   뒤 수행한다.

## 백엔드 프로세스 없음

1. SSM으로 해당 server EC2에 접속한다.
2. 백엔드 systemd 서비스 상태와 최근 배포 결과를 확인한다.
3. `curl --fail http://127.0.0.1:8081/actuator/health`로 상태를 확인한다.
4. CloudWatch Logs의 같은 시각 ERROR 로그를 확인한다.

## 메모리 사용률 높음

1. CloudWatch에서 서버 메모리와 Java 프로세스 RSS 추이를 함께 확인한다.
2. JVM Heap과 GC가 같이 증가했는지 확인한다.
3. 요청량 또는 배치 실행 증가와 같은 시각인지 확인한다.
4. 프로세스를 즉시 재시작하기 전에 원인 시각과 지표를 장애 문서에 남긴다.

## 디스크 사용률 높음

1. SSM에서 `df -h`로 부족한 마운트 지점을 확인한다.
2. 애플리케이션 로그와 패키지 캐시 등 큰 디렉터리를 확인한다.
3. 삭제 대상을 확인하지 않은 상태에서 재귀 삭제 명령을 실행하지 않는다.
4. EBS를 확장했다면 파티션과 파일시스템 확장 여부를 함께 확인한다.

## JVM Heap 사용률 높음

1. Heap 사용률이 GC 뒤 감소하는지, 계속 우상향하는지 확인한다.
2. GC 정지시간과 JVM 스레드 수를 함께 확인한다.
3. 특정 API 요청량 또는 뉴스 생성 작업 증가 여부를 확인한다.
4. 반복 증가하면 Heap dump 등 추가 진단을 별도 승인 후 진행한다.

## DB 커넥션 대기

1. `hikaricp_connections_active`, `max`, `pending`을 함께 확인한다.
2. CloudWatch Logs에서 DB 연결 실패와 쿼리 지연 오류를 확인한다.
3. DB EC2와 PostgreSQL 프로세스 상태를 확인한다.
4. DB 재시작은 진행 중인 작업과 데이터 영향을 확인한 뒤 수행한다.

## CloudWatch Agent 지표 없음

1. EC2 상태 검사와 SSM 연결 여부를 확인한다.
2. Agent 상태를 확인한다.

```bash
sudo /opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -m ec2 -a status
```

3. Agent 로그에서 권한 또는 설정 검증 오류를 확인한다.

```bash
sudo tail -n 100 \
  /opt/aws/amazon-cloudwatch-agent/logs/amazon-cloudwatch-agent.log
```

4. Actuator 지표만 없다면 다음 요청을 확인한다.

```bash
curl --fail http://127.0.0.1:8081/actuator/prometheus
```

## 장애 기록

- 발생·탐지·복구 시각
- 사용자 영향
- 최초로 감지한 Alarm과 로그
- 직접 원인과 근본 원인
- 수행한 조치
- 재발 방지 변경
- 임계값이 너무 빠르거나 늦었다면 조정 내용
