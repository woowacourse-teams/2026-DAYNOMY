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

- 후보 슬롯 준비 → Nginx 전환과 공개 API 확인 → 향후 배치 소유권 전환 → 기존 작업 완료 후 이전 JVM 종료.
- 비활성 후보의 예약·시작 작업은 작업 잠금을 기다리기 전에 건너뛴다. 활성 작업은
  파일 잠금을 획득한 뒤 소유권을 다시 검사하므로 대기 중 소유권을 잃으면 실행하지 않는다.
  소유권 전환 전에 시작한 배치는 잠금을 유지한 채 완료한다. 전환 후 새 배치는 같은
  호스트의 POSIX 잠금으로 직렬 실행한다. 실행 중복 방지이며 예약 시각별 정확히 한 번을 보장하지 않는다.
- 15초 동안 새 앱 준비·메모리를 확인한다. 이전 JVM의 종료는 배포 명령과 분리한다.
  `retirement.json`에 이전 PID·시작 시각·JAR 해시·Nginx worker를 전환 전에 저장한다.
- `daynomy-dev-retirement.timer`(운영은 `daynomy-retirement.timer`)가 15초 간격으로
  이전 worker 종료, 비동기 작업 pending 0, 예약 작업 scheduled.active 0을 확인한다.
  모두 충족한 뒤 종료 근거를 먼저 저장하고 이전 JVM을 정상 종료한다. 완료 시간 제한으로 배치를 죽이지 않는다.
- 상태를 조회할 수 없거나 PID·JAR가 달라졌으면 종료하지 않는다. 작업 완료 확인 전 JVM이
  사라지면 `unexpected_exit`로 남겨 검토한다. 이전 JVM이 남아 있는 동안 다음 배포는 거절한다.
  API 포트에는 이 관리 endpoint를 노출하지 않는다.
- 이전 JAR가 scheduled 계수를 제공하지 않는 최초 업그레이드에서는 소유권 전환 후
  전역 작업 잠금을 비차단 획득한 기록과 비동기 작업 완료를 함께 사용한다.
- 준비·설정·공개 API·메모리 검증 실패 시 원래 HTTP 경로를 먼저 복구하고 배치 소유권을 원복한다.
- 이전 JVM을 보존 중일 때 새 앱 준비 검사가 실패하면 timer도 이전 HTTP 경로로 복구한다.
  복구 역시 배치 잠금을 기다리지 않는다. 이는 애플리케이션 경로 복구이며 DB 변경을 되돌리지는 않는다.
- 이전 JVM이 남아 있는 동안 새 JVM의 OOM 우선순위 500을 unit 설정에도 유지한다.
  재시작해도 이 우선순위가 유지되며, 부팅 시 소유권 조건은 복구한다. 이전 JVM 종료 후
  임시 우선순위 설정을 제거하고 활성 JVM의 기존 우선순위 -500을 복구한다.
- `last-deployment.json`의 previous_release를 같은 명령에 전달하면 이전 버전도 준비 후 전환한다.
  DB 변경은 별도 호환성 검토가 필요하며 이 스크립트는 변경된 Flyway SQL을 거절한다.
- 운영 workflow 변경은 이번 dev 반영에서 제외한다. 운영 서버를 최초 이관할 때는
  기존 재시작 workflow와 동시에 사용하지 않도록 별도 연결·배포 동결 계획이 필요하다.

### 이슈 #961 설치와 검증 범위

- 기존 두 슬롯 설치 후, 검토한 동일 커밋의 스크립트로 개발 전용
  `operate_backend_development.py --operation install-retirement --expected-current-sha256 <확인한 해시> --apply`
  를 실행한다. EC2 ID와 현재 JAR 해시를 확인하며 앱 재시작·Nginx 전환은 하지 않는다.
  이후 배포는 timer가 활성 상태여야 시작한다. 운영 서버 적용은 별도 절차다.
- 배포와 timer는 같은 `deploy.lock`으로 상태 변경을 직렬화한다. `prepared` 상태에서
  제어기가 중단돼 소유권과 기록이 다르면 자동 종료하지 않는다. 실제 HTTP 경로·두 JVM·작업
  상태를 확인하고 검토한 복구 절차를 적용한다. 기록 삭제나 배치 강제 중단으로 우회하지 않는다.
- 기존 896MiB 공유 제한에서 유휴 전환을 통과한 사실만으로 장시간 배치 공존을 승인하지 않는다.
  실제 배치 진행·완료, 이전 PID 유지·자동 종료, OOM·swap·RSS·호스트 여유 메모리를 별도 관측한다.
- 개발 API를 같은 간격으로 연속 호출하고 응답 코드·슬롯·지연을 저장한다. 기존 재시작과
  배치 실행 중 전환의 실패 건수와 실패 요청 시작 시각 범위를 비교한다. 실패 0건은 해당
  관측 간격에서 실패를 보지 못했다는 뜻이며 0ms 중단을 증명하지 않는다.
- 수동 배치 중단 후 복구, 유휴 전환, 배치 때문에 중단된 배포는 각각 별도 실행으로 기록한다.
  이 절차 문서 자체는 실제 배치 중 전환 성공의 증거가 아니다.

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

## 공유 상태와 비동기 종료 보호 추가 (2026-11-24 후속 작업)

- 사용자 승인으로 V99의 Spring Session 두 테이블과 비회원 분석 상태 테이블을 추가했다.
  기존 PostgreSQL을 사용하며 신규 서버를 추가하지 않는다.
- 앱 메모리에 있던 비회원 체험 상태는 세션 ID 해시별 DB 문서로 변경했다. 두 앱의
  동시 예약은 DB 행 잠금으로 조정한다. AI 호출은 상태 트랜잭션 밖에서 수행한다.
  세션 유효 기간을 연장하지 않으며 문서는 24시간 비활성 후 시간 단위로 정리한다.
- 최초 설치 때만 --allow-shared-state-migration을 명시할 수 있다. 검토된 V99와, 기존 V98이 없는 JAR에서는 거래 요청 V98 원본만 함께 추가
  가능하며 기존 SQL 수정·삭제는 허용하지 않는다. 추가 테이블은 실패 복구 시 남겨 둔다.
  기존 메모리 세션을 DB 세션으로 이전하는 기능은 없으므로 최초 이관 시 세션 초기화와
  진행 중 작업 부재를 확인해야 한다. 최초 설치를 무중단 전환 실적으로 보고하지 않는다.
- Spring Session 기본 on-save 설정에서는 기존 OAuth 재사용 차단 인수 테스트가 실패했다.
  즉시 저장(immediate) 설정으로 수정하고 테스트를 유지했다. 실패 실행도 실험 기록에 남겼다.
- 제출한 작업의 pending 수를 추적한다. Future 취소·HTTP 타임아웃만으로 작업 완료로
  판단하지 않으며, 접수 거절된 작업은 pending에서 제외한다.
- 운영 기준 JAR는 f361d6f96a1a3db002ed8ce0642af832f5e4aa39로 다시 대조했다.
  읽기 전용 2초 간격 5개 표본의 MemAvailable 중앙값은 389.68MiB였다.
  현재 초기 설치의 406MiB와 후보 기동의 656MiB 조건을 모두 충족하지 않는다.
  조건을 낮춰 배포하지 않았으며 운영 JAR·unit·Nginx·DB는 변경하지 않았다.
- 개발 브랜치에는 운영 main과 무관한 기능 차이가 있으므로, 이 브랜치의 전체 JAR를
  바로 운영에 복사하지 않는다. 승인된 배포 변경만 포함한 운영 이관 아티팩트를 별도로
  대조·검증하거나 정상적인 릴리스 절차를 거친다.
- 후속 로컬 시험과 최종 테스트 결과는 별도 결과 파일로 기록한다. 초기 공유 상태 JAR의
  전환 시험과 OAuth·작업 추적 보완 후 최종 JAR 시험은 해시로 구분한다.

### 후속 검증 결과

요약 원본: [validation.json](../loadtest/results/bluegreen-shared-state-20261124/validation.json).

- 최종 전체 `spotlessCheck test bootJar` 통과: 2274개, 실패 0개, 오류 0개, 기존 조건부 5개 건너뜀.
- 운영 Python 환경의 배포·초기 설치·롤백·준비 상태·작업 종료 검사 23개 통과.
- DB 인수 경계에서 서로 다른 앱 객체의 동시 예약 한 번, 진행 상태·완료 결과 공유,
  다른 세션의 조회·재시도 거절을 확인했다.
- 초기 공유 상태 JAR의 300초 전환 시험은 혼합 요청 3000건과 Nginx 관측 3000건 실패 0건,
  거래 195건·스냅샷 3000건 불일치 0건이었다. 최대 800.02MiB, max 279회, OOM 0회다.
  이 아티팩트에는 최종 OAuth 설정과 정확한 pending 추적이 없어 최종 배포 근거로 쓰지 않는다.
- 최종 JAR(9968d29cc845800f012e6fb26543a306734b6b7d969f9d10770f0ba3c64fba97)의 실제
  두 JVM에서 같은 세션의 작업 ID 공유와 기존 JVM 종료 후 상태 조회를 확인했다.
  이 HTTP 시험은 잘못된 합성 종목으로 만든 FAILED 상태를 사용했으며 AI 성공을 뜻하지 않는다.
  다른 세션 조회 404, API 포트의 관리 endpoint 404, 네 종류 실행기 pending 조회를 확인했다.
  최대 800MiB, max 41회, OOM 0회다. 정상 AI 완료 결과 보존은 별도 DB 테스트 결과다.
- 최종 아티팩트의 전체 Nginx 혼합 부하·모든 외부 배치 장기 시험과 운영 전후 측정은 남아 있다.
  두 메모리 시험 모두 제한에 닿았으므로 여유 자원 확보 완료로 해석하지 않는다.

## 개발 서버 반영과 검증

- 이번 PR의 대상은 dev다. 운영 backend-cd.yml은 변경하지 않는다. 개발 CD는 최초
  설치 전에는 기존 daynomy-dev 재시작을 유지한다. deployment-dev 디렉터리가 있으면
  --environment development로 전환 제어기를 실행한다. 불완전한 설치 상태에서
  재시작 방식으로 자동 우회하지 않는다.
- 개발 대상은 i-0f2e92d9c7bd8fe06으로 제한한다. 개발 서비스는 daynomy-dev와
  daynomy-dev-green, 상태 디렉터리는 deployment-dev, 공개 검증 주소는
  https://dev.daynomy.com이다. CLI의 환경과 실제 EC2 ID가 다르면 변경을 거절한다.
- 머지 후 개발 CD의 실제 실행 결과에서 JAR 해시·API·메모리·Nginx 경로를 확인한다.
  backend-dev-cd.yml을 verify_only=true로 수동 실행하면 배포 없이 같은 상태를 조회한다.
- 최초 설치는 관측기로 재시작 기준값을 기록하면서 다음 순서로 진행한다.
  inspect_backend_development.py로 실제 unit·환경 파일 경로·Nginx 사이트를 확인하고,
  bootstrap_backend.py --environment development --artifact ... --site ...
  --environment-file ...로 사전 검사한다. 통과 후에만 --expected-current-sha256 ...
  --apply를 추가한다. 개발 DB에 V99가 이미 적용된 경우 SQL 추가 허용 옵션은 필요 없다.
  SSM 대신 개발 CD를 bootstrap_bluegreen=true로 수동 실행할 수도 있다.
  nginx_site·backend_environment_file은 직전 inspect 결과와 일치시킨다.
  이 실행도 동일한 사전 검사와 JAR 해시 검사를 통과해야 하며 메모리 기준을 우회하지 않는다.
- 설치 후 같은 제어기로 새 버전 전환과 이전 버전 복구를 검증한다. 저장 성공·원장 및
  스냅샷 일치·배치 실행·분석 작업 조회·메모리를 별도로 대조하며, 개발 서버 결과를
  운영 성과로 표기하지 않는다. 운영 최초 이관과 자동 배포 전환은 후속 작업이다.

## 개발 DB 마이그레이션 충돌 수정 (2026-11-25)

- 실제 dev DB의 V98은 `V98__create_portfolio_transaction_requests.sql`, 체크섬 `-569021117`이다.
- 공유 세션을 V98로 배포해 검증에 실패하고 이전 JAR로 자동 원복됐다. DB 이력은 수정하지 않는다.
- 적용된 거래 V98을 동일 원본으로 보존하고, 세션·비회원 상태 테이블은 V99로 추가한다.
- 실제 V98 상태에서 V99 업그레이드 후 기존 체크섬·테이블 식별자 보존과 세 테이블 생성을 검증한다.
- 임시 이슈 브랜치 진단과 암호화 로그 수집 코드는 수정 PR에서 제외한다.
