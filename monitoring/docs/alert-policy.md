# DAYNOMY CloudWatch 1차 알림 정책

## 원칙

- 모든 단건 오류를 알리지 않고 일정 시간 지속된 장애만 알린다.
- dev와 prod의 Alarm을 분리한다.
- 정상화 시 `OK` 복구 알림도 전송한다.
- 최초 임계값은 1~2주간 수집한 정상 범위를 바탕으로 다시 조정한다.
- 프론트엔드 오류와 성능은 Sentry 알림을 사용한다.

## 알림 경로

```text
CloudWatch Alarm
  └─ 상태 ALARM 또는 OK
      └─ SNS Topic
          └─ Amazon Q Developer in chat applications
              └─ Slack 모니터링 채널
```

CloudWatch Alarm은 같은 상태가 계속된다고 5분마다 반복 전송하지 않는다. 상태가
`OK → ALARM` 또는 `ALARM → OK`로 바뀔 때 SNS 작업을 실행한다. 지속 장애의 정기
재알림이 필요하면 2차에서 EventBridge 또는 별도 알림 로직을 추가한다.

## 1차 기준

| 영역 | 환경 | 조건 | 평가 |
|---|---|---|---|
| EC2 상태 | prod | `StatusCheckFailed >= 1` | 1분 주기 2회 중 2회 |
| EC2 상태 | dev | `StatusCheckFailed >= 1` | 1분 주기 5회 중 5회 |
| 백엔드 프로세스 | prod | `procstat_lookup_pid_count < 1` | 1분 주기 2회 중 2회 |
| 백엔드 프로세스 | dev | `procstat_lookup_pid_count < 1` | 1분 주기 5회 중 5회 |
| 메모리 | prod | `mem_used_percent >= 90` | 1분 주기 5회 중 5회 |
| 디스크 | dev/prod | `disk_used_percent >= 85` | 1분 주기 10회 중 10회 |
| JVM Heap | prod | Heap 사용률 `>= 90%` | 1분 주기 15회 중 15회 |
| DB 풀 | prod | `hikaricp_connections_pending >= 1` | 1분 주기 5회 중 5회 |

HTTP 5xx 비율과 지연 알림은 먼저 CloudWatch에 실제 요청량이 쌓이는지 검증한 뒤 만든다.
저트래픽 서비스에서 오류 1건만으로 비율 알림이 발생하지 않도록 다음 조건을 함께 쓴다.

- 최근 5분 전체 요청 20건 이상
- 최근 5분 5xx 요청 5건 이상
- 같은 구간 5xx 비율 5% 초과

## Missing data

- 프로세스 생존과 EC2 상태 Alarm은 missing data를 `breaching`으로 취급한다.
- 임계값 기반 리소스 Alarm은 missing data를 `missing`으로 둔다.
- dev 점검 중 발생한 Alarm은 SNS 작업을 잠시 비활성화하고, 점검이 끝나면 반드시
  다시 활성화한다.

## 조정 기록

임계값을 변경할 때 다음 내용을 PR 또는 장애 문서에 기록한다.

- 변경 날짜
- 이전 값과 변경 값
- 오탐 또는 미탐 사례
- 변경한 이유
