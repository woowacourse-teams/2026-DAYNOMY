# 단일 EC2 백엔드 배포 절차

작업 이슈: [#943](https://github.com/woowacourse-teams/2026-DAYNOMY/issues/943)

작업 브랜치: `chore/BE/#943`, 기준: `dev`의 `d5480879d2973f576cac2550928abf63285c0b9d`.
운영 JVM·Nginx 전환 전이다. 아래 사전 실험은 운영 소스
`f361d6f96a1a3db002ed8ce0642af832f5e4aa39`에 변경을 적용한 결과이며,
최신 dev 재검증과 운영 전환 결과로 간주하지 않는다.

## 설치 전

1. 기존 unit, Nginx 네 API location, 환경 파일 경로, 원본 JAR 해시를 대조한다.
   환경 파일 내용은 출력하지 않는다. CodeDeploy가 중지 상태인지, 메모리 여유가 있는지 확인한다.
2. 운영과 같은 JVM의 로컬 거래·재계산·스냅샷 및 Nginx 전환 시험을 완료한다.
3. 두 unit에 SyslogIdentifier=daynomy를 지정하고 Alloy의 기존 backend selector를
   SYSLOG_IDENTIFIER=daynomy로 바꾼다. Alloy validate·reload를 수행하며 실패하면 원복한다.
   관리 지표 주소는 8081을 유지한다.
   참고: [systemd SyslogIdentifier](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec),
   [Alloy journal match](https://grafana.com/docs/alloy/latest/reference/components/loki/loki.source.journal/).
4. 기존 예약 배치가 실행 중이지 않은 시점에 외부 읽기 전용 관측기를 먼저 시작한다.
   첫 설치는 기존 앱을 한 번 중단하므로 실제 실패 구간을 기준값으로 저장한다.
5. 새 JAR는 검증된 소스에서 빌드하여 releases 안의 새 파일로 배치한다.
   `bootstrap_backend.py --artifact ...`로 사전 점검한다.
6. `bootstrap_backend.py --artifact ... --expected-current-sha256 ... --apply`를 실행한다.
   오류 시 백업 unit·Nginx로 복구하고 원래 앱을 시작한다. 기존 JAR와 env는 덮어쓰지 않는다.
   실패 후 남은 deployment 디렉터리는 백업과 함께 확인해야 하며 무조건 재실행하지 않는다.

## 이후 배포

`sudo python3 backend/scripts/deploy_backend.py --artifact /path/to/new.jar --version <40자리 SHA>`

- 후보 슬롯 준비 → 현재 배치 완료 대기 → Nginx 전환과 공개 API 확인 → 배치 소유권 전환.
- 15초 동안 준비·메모리를 확인하고 이전 Nginx worker가 종료된 뒤 이전 JVM을 종료한다.
- worker가 120초 내 종료되지 않으면 이전 JVM을 살려 두고 retirement.json에 기록한다.
  다음 배포는 기존 요청이 끝날 때까지 중단된다. 이를 성공적으로 정리하기 전 추가 배포를 강행하지 않는다.
- 준비·설정·공개 API·메모리 검증 실패 시 원래 HTTP 경로를 먼저 복구하고 배치 소유권을 원복한다.
- `last-deployment.json`의 previous_release를 같은 명령에 전달하면 이전 버전도 준비 후 전환한다.
  DB 변경은 별도 호환성 검토가 필요하며 이 스크립트는 변경된 Flyway SQL을 거절한다.
- 현재 main의 기존 재시작 workflow와 동시에 사용하지 않는다. 새 workflow가 병합되기 전에는
  배포 동결을 유지해야 한다. 본 브랜치의 workflow만 두 포트 제어기를 호출한다.

## 운영 커밋 기준 사전 검증

- Java 소유권·스케줄러·별도 JVM 잠금 4개 테스트. 실제 Spring 등록 경로·기본/명명 스케줄러를 포함한다.
- Python 배포·부팅 복구 9개 및 첫 설치·복구 4개: 성공, Nginx 검사 실패, 공개 경로 실패, 요청 drain 대기,
  스키마 변경, 메모리 부족, 원복 중 후보 요청 drain, 관측기 reload 실패 시 원래 앱 복구, 기존 배치 실행 중 설치 거절.
- `python3 backend/scripts/verify_posix_ownership_lock.py --build backend/build`:
  Linux의 Python 잠금 동안 Java가 대기하고 해제 후 변경된 소유권을 읽음. 통과.
- `./gradlew spotlessCheck test`: 2,158개, 실패 0, 기존 조건부 3개 건너뜀.
- 실제 새 JAR 두 개 + Nginx + 혼합 부하의 사전 결과는 아래에 기록했다.
  최초 handoff-01은 예약 작업의 Spring 내부 wrapper를 놓쳤으므로 배포 승인 근거에서 제외한다.
  HTTP 3,000건 성공과 별개로 예약 배치 보호는 실패했다. 이를 actual @Scheduled 등록 테스트로 보완했다.

운영 전환 전후의 중단 시간·실패율 수치는 아직 측정하지 않았다. 이력서에 완료 성과로 쓰지 않는다.

CodeDeploy는 중지 상태만 확인했으며 부팅 시 자동 시작 설정은 유지했다. 재부팅 후 메모리 조건을 다시 확인해야 한다.

## 2026-11-24 수정 후 로컬 결과

운영과 같은 Corretto 21.0.13 / glibc의 합계 800MiB cgroup, CPU 2개,
격리된 PostgreSQL 16과 합성 회원 50명·종목 30개·가격 60일을 사용했다.

- 두 앱 공존 300초 동안 혼합 업무 요청 3,000건 성공, 실패·누락 0건.
- 같은 기간 별도 Nginx 읽기 관측 3,000건 성공, 실패·누락 0건.
- 관측 경로는 blue → green → blue였고, 전환 전 배치 완료를 26.013초·9.845초 기다렸다.
- 예약 작업 시작은 blue 4회·green 1회, 비활성 실행 및 JVM 간 중복 0회.
- 준비·단독 측정 포함 성공 거래 195건과 DB BUY 이력 증가 195건이 일치했다.
- 스냅샷 3,000건, 원장·현재 수량·스냅샷 평가액 불일치 0건.
- cgroup 최대 800.03MiB, max 이벤트 252회, OOM 및 OOM kill 0회.
  제한에 닿아 메모리 회수가 일어났으므로 넉넉한 메모리라는 뜻은 아니다.
- Nginx 관측 응답 p95 6.48ms, p99 13.37ms. 운영 수치가 아니다.
- 외부 수집·이메일 등 다른 배치와 운영 데이터 전체는 이 시험에 포함하지 않았다.

집계·환경·전환 시각: `../loadtest/results/bluegreen-handoff-20261124/`.
원시 요청·메모리·앱 로그는 개인 로컬 `.local/bluegreen-handoff-02/`에 보존한다.

두 backend unit은 모두 부팅 대상으로 등록하지만 ExecCondition이 활성 슬롯만 기동한다.
배포 중 후보만 일시적으로 조건을 해제하고 OOM 점수를 높인다. 활성 슬롯이 시작될 때
root 소유의 `/usr/local/libexec/daynomy/deploy_backend.py --prepare-slot`이 저장된 소유권에
맞춰 Nginx 경로를 복구한다. 따라서 전환 중 재부팅으로 route와 owner 파일이 어긋나도
다음 시작에서 소유권을 기준으로 복구한다. 이 동작은 단위 테스트로 검증했으며
운영 EC2를 재부팅하는 장애 실험은 아직 수행하지 않았다.

## 이슈 #943에서 다시 시작한 개발 절차

- 기존 이슈 없는 원격·로컬 브랜치는 삭제하고 원본 커밋·패치를 로컬에 백업했다.
- 이슈 #943을 등록한 뒤 최신 dev에서 `chore/BE/#943`을 새로 만들었다.
- 기존 운영 커밋 기준 변경을 3-way로 재적용하고 최신 코드의 검증 결과를 별도로 기록한다.
- 일회성 SSM 클래스 패치 전송 도구는 제품 변경에서 제외했다. 배포에는 검증된 소스의 전체 JAR를 사용한다.
- PR 생성·main 병합·운영 재시작·기존 자동 배포 중지는 수행하지 않았다.

## 최신 dev 재적용 검증 (2026-11-24)

- 기준 dev: `d5480879d2973f576cac2550928abf63285c0b9d`.
- `./gradlew clean build --offline --no-daemon` 성공: 2268개, 실패 0개, 오류 0개, 조건부 건너뜀 5개.
- 운영 Python 3.9.25에서 배포·설치·부팅 복구 테스트 13개 통과.
- 최신 dev JAR의 두 JVM 부하 재시험과 운영 전환 실측은 아직 진행하지 않았다.
