# 개발 서버 메모리 준비와 두 포트 전환

이 절차는 `i-0f2e92d9c7bd8fe06` 개발 EC2에만 적용한다. 운영 EC2는 모든 실행의
인스턴스 검사에서 거절한다. GitHub development 환경의 dev 브랜치 보호 규칙을
유지하고, 이슈 브랜치를 직접 실행하는 대신 검증·머지 후 수동 실행한다.
운영 스크립트·문서·부하 실험 fixture·이 workflow만 변경되면 일반 애플리케이션 CD를 실행하지 않는다.
애플리케이션 코드·리소스·빌드 설정 변경은 기존 자동 배포 대상으로 유지한다.

## 실제 실패 및 현재 후보 상태 (2026-11-25)

128MiB 힙은 단기 관측을 통과했으나 실제11,005개 리그 프로필 마지막 구간에서
Java heap space로 종료되어 기각했다(18:46:39UTC, NRestarts1). 실행45463685085가
원래 unit을 복구했고 내부·외부200 및 기존 세션 유지를 확인했다. 스왑은 보존했다.
이 결과를 전체 배치 통과 또는 무중단 적용 성공으로 사용하지 않는다.

192MiB/합계800MiB 로컬 후보도 OOM1로 실패했다. 같은192MiB/합계896MiB 후보는
5분 동안 Nginx3000건 성공, 거래승인195건과 DB증분 일치, 스냅샷3000 불일치0,
배치비활성·중복0, OOM0이었다. 이 로컬 결과와 별도로 실서버 전체 배치·전환 검증이 필요하며 실행 결과는 이슈 #943에 기록한다.
추가 EC2·디스크 증설 없이 기존 RAM 안에서 시험하며 JVM swap0과 RAM reserve256MiB를 유지한다.

## 확인한 기준 상태 (2026-11-25)

- RAM 1,890,876KiB, 스왑 없음, 디스크 여유 약 6GB.
- 단일 JVM RSS 약 627MiB, systemd MemoryCurrent 약 638MiB, 메모리 상한 없음.
- 명시적 힙·JIT 메모리 조정 없음. 다른 프로세스는 보안 에이전트·runner·Alloy·Docker 등이다.
  보안 에이전트나 다른 배포 runner를 종료하지 않는다.
- Nginx는 site 안 API 프록시 2개와 별도 portfolio-analysis snippet 안 1개를 사용한다.
  두 파일을 함께 전환·원복해야 한다. 추가 미검토 백엔드 프록시는 설치를 거절한다.

## 스왑과 JVM 실험

Deploy backend development의 수동 실행에서 `development_memory_trial=true`와
직전 확인한 `expected_current_sha256`을 지정한다. `memory_observe_seconds`는
120~7200초이며 기본 600초다. 이 모드는 일반 빌드·배포를 동시에 실행하지 않는다.
실제 적용 전 SHA256과 개발 EC2 ID를 확인한다. 전체 검증은 `require_full_batch=true`와
`memory_observe_seconds=7200`으로 실행한다. 최소120초 관측 후 전체 리그 재계산의
성공 지표·스케줄러 유휴·재시작0을 모두 확인해야 조기 완료한다. 미완료는 성공으로
처리하지 않으며, 배치 실패·시간 초과·재시작·RAM reserve 위반 시 원래 unit으로 복구한다.
단기 탐색 결과와 전체 배치 적합성은 구분한다. 이전 원복의 백업은 현재 원본 unit과
정확히 같을 때만 재사용하고 덮어쓰지 않는다. 예비 계획만 보려면 서버에서
`prepare_backend_development_memory.py`를 옵션 없이 실행한다.

- 기존 디스크에 1GiB swapfile을 추가한다. 최소 1GiB의 디스크 여유를 남기고,
  기존 swapfile·스왑 설정 파일을 덮어쓰지 않는다. 스왑이 이미 있으면 보존한다.
- 스왑은 다른 유휴 프로세스와 호스트의 완충 공간이다. JVM의 `MemorySwapMax=0`은
  유지한다. SwapFree를 MemAvailable에 더해 RAM 검증을 통과시키지 않는다.
- 힙 192MiB, C1 JIT, MALLOC_ARENA_MAX=2, 단일 서비스 상한 512MiB로 시험한다.
  C1은 JIT 메모리와 최적화 수준을 바꾸므로 응답 시간과 GC 영향을 따로 확인한다.
- 기존 스케줄러 유휴를 검사하고 기존 JVM을 먼저 종료한 뒤 메모리 한도를 적용한다.
  실행 중인 약 640MiB JVM의 한도를 갑자기 줄여 OOM을 유발하지 않는다.
- 원래 unit을 root 전용 디렉터리에 보관한다. 기동·헬스·요청 검증 실패나 재시작 감지 시
  원래 설정으로 복구한다. 복구 시 활성 스왑을 강제 해제하거나 파일을 삭제하지 않는다.
- 5초마다 RSS·Java swap·MemAvailable·SwapFree·재시작 수를 기록한다. 읽기 전용 뉴스와
  EMPTY 비회원 조회를 확인하며 AI 분석·실제 사용자 거래를 요청하지 않는다.

검증 종료 후에도 추가 앱 시작 기준은 여유 RAM 736MiB이며 초기 설치 기준은
406MiB다. 장기 관측·거래/재계산/배치 공존 결과와 함께 판단하고, 부족하면 기준을
임의로 낮추지 않는다. 이미 설치한 Blue/Green unit에 이 단일 JVM 실험을 덮어쓰지 않는다.

## 개발 Nginx 설치

기존 `bootstrap_bluegreen=true` 절차를 사용한다. 실제 site와 snippet의 알려진 세
프록시만 허용하고 `nginx -T` 전체에서 다른 8080 프록시가 발견되면 거절한다.
두 파일과 unit·Alloy를 함께 백업하고, 실패 시 두 파일 모두 복구한다. 운영의
기존 site 내 네 경로 검증은 유지한다. 최초 한 번의 설정 이관은 재시작을 포함하며
이후 Blue/Green 전환의 무중단 성과와 별도로 기록한다.

## 실제 수행 결과

코드 반영이나 실험 시작을 적용 성공으로 간주하지 않는다. 실행 ID·JAR 해시·스왑
생성 여부·최소 여유 RAM·RSS·GC·응답 실패·배치 및 원장 정합성 결과를 이슈 #943과
개인 실험 기록에 남긴다. 개발 검증과 운영 적용은 구분한다.

일반 빌드를 반복하지 않고 이미 검증·배포된 같은 JAR로 실제 설치와 전환을 시험하려면
`development_existing_release_operation=bootstrap` 또는 `switch`를 선택한다. 이 모드도
`dev` 브랜치·development 환경 보호·개발 EC2 ID·현재 활성 JAR 해시를 모두 검사한다.
메모리 실험 및 verify_only 모드와 동시에 실행하지 않는다. switch의 숫자 counter는
불변 릴리스 이름의 `-bgN` 접미사이며 두 번째 전환에는 다른 번호를 사용한다.
개발 설치는 실제 실행 JVM의192MiB/C1 인수, 단일512MiB·swap0 설정, 재시작0 및
완료된 리그 전체 재계산 지표를 확인한다. 원복된 기존 JVM이나 실패한128MiB 설정은
설치를 거절한다. 기존 JAR 인계에서는 스케줄러 유휴도 재확인한다. 설치 후 두 JVM은
공유896MiB/swap0 슬라이스를 사용하며 운영의128MiB/800MiB 설정은 변경하지 않는다.
개발 서버에서 현재와 동일한 SHA256의 JAR일 때만 green을 비활성 슬롯으로 기동한다.
준비 검사·Nginx 전환 확인 후 작업 소유권을 green으로 넘겨, 방금 완료한 시작 배치를
새 JVM에서 즉시 반복하지 않는다. 시작 작업을 건너뛰는 것은 일반 대기 슬롯의 소유권
검사와 동일하며 예약 배치를 비활성화하는 설정을 추가하지 않는다. 최초 설치는 기존
JVM을 정지하는 구간이 있어 여전히 무중단 성과로 집계하지 않는다. 실패하면 green을
정지하고 원래 unit·프록시·Alloy를 복구한다. 다른 바이너리·운영 서버·미완료 또는 실패한
재계산에는 이 인계 모드를 허용하지 않는다.

이 시험은 동일 JAR의 green→blue→green 요청 연속성 검증이며 서로 다른 기능 버전의
롤백 성공으로 확대 해석하지 않는다.

## 유휴 관리 프로세스의 제한된 스왑 회수

실패한128MiB 후보의 단기 실측에서 스왑 생성만으로 RAM이 자동 확보되지는 않았다. 최초 적용은 JVM RSS를
약656MiB에서409MiB로 줄였지만, runner 작업 중 RAM 여유는 약475MiB였고 스왑은
약7MiB만 사용됐다. 기본 256MiB 보호 여유와 736MiB 후보 시작 기준을 낮추지 않는다.

개발 인스턴스이며 호스트 스왑이 약1GiB 이상, 미사용 스왑이512MiB 이상일 때만
허용 목록의 runner·CodeDeploy agent·Docker 서비스에 커널 `memory.reclaim`을
제한적으로 요청한다. 앱·DB·보안 에이전트·Alloy·프론트 컨테이너는 대상이 아니다.
다른 frontend runner 작업이나 agent hook이 있으면 해당 대상을 건너뛴다. 서비스
중지·memory.max 변경·전역 swappiness 변경은 하지 않는다. 커널의 부분 회수나
실패는 성공량으로 간주하지 않고 실제 MemAvailable과 swap 사용 변화를 기록한다.
각 회수 뒤 기존 앱 헬스를 확인하고, 736MiB를 확보하지 못하면 새 JVM을 시작하지 않는다.

지원되는 커널에서는 요청별 swappiness를 사용하고, 지원되지 않으면 기존 숫자형
ABI로 회수한다. 회수 중 유휴 관리 프로세스의 후속 접근에 page fault 지연이 생길 수
있으므로 실제 API 응답과 실행 시간·OOM을 계속 관측한다. 보호 여유와 JVM swap0은
그대로 유지한다.

192MiB 후보의 기동 전 RAM 기준은736MiB(후보480MiB+보호256MiB)로 높였다. 관리 페이지 회수 목표는768MiB이며 실제 회수량만 판단에 사용한다. 공유896MiB 한도는 물리 RAM 증설을 뜻하지 않으며, 전환 중 실제 여유 RAM·두 JVM 공존·OOM을2초 간격으로 기록한다.

## GC 실제 선택 확인 및 명칭 정정

초기 기록의192MiB 기본 후보를 G1이라고 부른 것은 추정 오류였다. 동일 Corretto·메모리
제한의 PrintFlagsFinal 및 실제 개발 PID의 VM.flags에서 UseSerialGC=true,
UseG1GC=false를 확인했다(개발 진단45475961620). 명시적 UseSerialGC 시험은
다른 GC와의 비교가 아니라 이미 자동 선택된 SerialGC의 반복 시험이다.

| 실제 조건 | 단일 RSS 관측 최대 | 두 JVM800MiB |
| --- | ---: | --- |
| 자동 선택 SerialGC | 약448.9MiB | 후보기동 OOM1 |
| 명시적 SerialGC | 약444.3MiB | 후보기동 OOM1 |

위 RSS 차이를 GC 변경의 효과로 해석하지 않는다. 이 자료가 뒷받침하는 결론은
같은192MiB/SerialGC 조건에서800MiB 공존이 반복 실패했고896MiB의5분 시험은
통과했다는 것이다. 거래195건은 워밍업·단일 측정을 포함한 전체 실험 합계이며,
공존5분의 승인 거래는150건이다.

현재 장기 시험의 JVM을 변경하지 않는다. 이후 개발 Blue/Green unit에는
-XX:+UseSerialGC를 명시하고, 최초 설치 전에도 현재 JVM의 실제 collector를 확인해
실험과 다른 GC이면 거절한다. 운영 unit 생성 설정은 변경하지 않는다.

GC 일반 지침은 다음을 참고하되 실제 선택 결과를 직접 확인한다.
[Oracle Java21 GC 선택 지침](https://docs.oracle.com/en/java/javase/21/gctuning/available-collectors.html)

동일 이미지·2CPU·heap192MiB·C1·MaxRAM1846MiB의 플래그 조회 대조에서 cgroup896MiB는SerialGC, cgroup3GiB는G1을 자동 선택했다. 실제 서비스 프로세스는SerialGC로 확인했다. 이 차이 때문에 개발 배포 unit의 collector를 명시하고, 기동 전 실제 플래그가 다르면 설치를 거절한다. 이는 진행 중인 시험의 GC를 바꾸는 작업이 아니다.
