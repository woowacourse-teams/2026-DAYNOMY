# 개발 서버 메모리 준비와 두 포트 전환

이 절차는 `i-0f2e92d9c7bd8fe06` 개발 EC2에만 적용한다. 운영 EC2는 모든 실행의
인스턴스 검사에서 거절한다. GitHub development 환경의 dev 브랜치 보호 규칙을
유지하고, 이슈 브랜치를 직접 실행하는 대신 검증·머지 후 수동 실행한다.

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
120~1800초이며 기본 600초다. 이 모드는 일반 빌드·배포를 동시에 실행하지 않는다.
실제 적용 전 SHA256과 개발 EC2 ID를 확인한다. 예비 계획만 보려면 서버에서
`prepare_backend_development_memory.py`를 옵션 없이 실행한다.

- 기존 디스크에 1GiB swapfile을 추가한다. 최소 1GiB의 디스크 여유를 남기고,
  기존 swapfile·스왑 설정 파일을 덮어쓰지 않는다. 스왑이 이미 있으면 보존한다.
- 스왑은 다른 유휴 프로세스와 호스트의 완충 공간이다. JVM의 `MemorySwapMax=0`은
  유지한다. SwapFree를 MemAvailable에 더해 RAM 검증을 통과시키지 않는다.
- 힙 128MiB, C1 JIT, MALLOC_ARENA_MAX=2, 단일 서비스 상한 450MiB로 시험한다.
  C1은 JIT 메모리와 최적화 수준을 바꾸므로 응답 시간과 GC 영향을 따로 확인한다.
- 기존 스케줄러 유휴를 검사하고 기존 JVM을 먼저 종료한 뒤 메모리 한도를 적용한다.
  실행 중인 약 640MiB JVM의 한도를 갑자기 줄여 OOM을 유발하지 않는다.
- 원래 unit을 root 전용 디렉터리에 보관한다. 기동·헬스·요청 검증 실패나 재시작 감지 시
  원래 설정으로 복구한다. 복구 시 활성 스왑을 강제 해제하거나 파일을 삭제하지 않는다.
- 5초마다 RSS·Java swap·MemAvailable·SwapFree·재시작 수를 기록한다. 읽기 전용 뉴스와
  EMPTY 비회원 조회를 확인하며 AI 분석·실제 사용자 거래를 요청하지 않는다.

검증 종료 후에도 추가 앱 시작 기준은 여유 RAM 656MiB이며 초기 설치 기준은
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
이 시험은 동일 JAR의 blue→green→blue 요청 연속성 검증이며 서로 다른 기능 버전의
롤백 성공으로 확대 해석하지 않는다.
