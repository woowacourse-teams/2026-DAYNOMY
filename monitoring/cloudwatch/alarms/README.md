# CloudWatch Alarm 적용

이 디렉터리는 현재 수집 중인 EC2 기본 지표와 CloudWatch Logs만 사용한다. CloudWatch
Agent, 대시보드, Actuator 설정은 변경하지 않는다.

## 생성되는 리소스

### 메트릭 Alarm

- DEV EC2 상태 검사 5분 연속 실패
- PROD EC2 상태 검사 2분 연속 실패
- DEV/PROD CPU 크레딧 15분 연속 20 미만

### 로그 Alarm

- DEV 백엔드: 5분 동안 `ERROR` 10건 이상
- PROD 백엔드: 5분 동안 `ERROR` 5건 이상

로그 Alarm은 기존 로그 그룹에 Metric Filter를 추가해 `DAYNOMY/Logs` namespace의
카운터를 만든다. 단일 ERROR 한 건마다 알리지 않고 짧은 시간에 오류가 누적될 때만
알린다.

아직 실제 데이터가 확인되지 않은 메모리, 디스크, procstat, JVM, HTTP, HikariCP
Alarm은 이 템플릿에 포함하지 않는다.

## 배포 전 확인

CloudWatch 콘솔의 `로그 그룹`에서 다음 두 그룹이 존재하는지 확인한다.

```text
/daynomy/dev/backend
/daynomy/prod/backend
```

둘 중 하나라도 없다면 해당 로그 그룹을 먼저 생성하거나, 템플릿 배포 시 실제 이름을
파라미터로 입력해야 한다.

EC2 콘솔에서 다음 인스턴스 ID도 최신 값인지 확인한다.

```text
DEV  i-0583a6a613e3d85a9
PROD i-0e487a6bc5126421c
```

## AWS 콘솔에서 배포

1. AWS 콘솔에서 `CloudFormation`을 연다.
2. 리전이 `ap-northeast-2`인지 확인한다.
3. `스택 생성 → 새 리소스 사용(표준)`을 선택한다.
4. `기존 템플릿 선택 → 템플릿 파일 업로드`를 선택한다.
5. `backend-alerts.yaml`을 업로드한다.
6. 스택 이름을 `daynomy-cloudwatch-alerts`로 입력한다.
7. dev/prod 인스턴스 ID와 로그 그룹 이름을 확인한다.
8. 나머지는 기본값으로 진행하고 스택을 생성한다.
9. 상태가 `CREATE_COMPLETE`인지 확인한다.

## Slack 연결

1. CloudFormation 스택의 `출력` 탭에서 `MonitoringAlarmTopicArn`을 복사한다.
2. AWS 콘솔에서 `Amazon Q Developer in chat applications`를 연다.
3. Slack workspace를 연결하고 알림을 받을 채널을 선택한다.
4. SNS Topic에 위 ARN을 추가한다.
5. Slack 채널에서 `/invite @Amazon Q`를 실행한다.

CloudWatch Alarm이 `ALARM`으로 바뀔 때 장애 알림이, `OK`로 복구될 때 복구 알림이
같은 SNS Topic을 통해 Slack으로 전송된다.

## 검증

CloudWatch 콘솔의 `경보 → 모든 경보`에서 다음 6개가 생성됐는지 확인한다.

```text
daynomy-dev-backend-error-burst
daynomy-prod-backend-error-burst
daynomy-dev-ec2-status-check-failed
daynomy-prod-ec2-status-check-failed
daynomy-dev-cpu-credit-low
daynomy-prod-cpu-credit-low
```

실제 EC2나 prod 백엔드를 중단해 시험하지 않는다. Slack 전달 검증은 SNS Topic의 게시
기능 또는 별도의 dev 테스트 Alarm을 사용한다.
