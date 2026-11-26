# 단일 EC2 운영 Blue/Green 적용·검증

## 범위와 제약

개발 브랜치에 이미 반영된 배포 소유권·작업 종료 확인·공유 세션 코드는 유지하고 운영 배포 설정만 보완한다. 변경은 dev에서 검증한 뒤 dev → main 릴리스 PR로 반영한다. 추가 EC2·로드밸런서는 사용하지 않는다.

Blue는 8080/8181, Green은 8082/8182를 사용한다. API와 관리 포트는 loopback에 바인딩한다. Nginx가 새 버전의 readiness를 확인한 뒤 API 경로를 전환한다. 이전 JVM의 진행 중 요청·예약 배치·분석 작업이 끝날 때까지 이전 JVM을 유지하고 retirement timer가 종료한다. 이 기간에는 다음 배포를 거부한다.

앱 두 개의 합계 메모리 한도는 1GiB, 각 JVM은 `-Xmx192m -XX:TieredStopAtLevel=1 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError`, `MALLOC_ARENA_MAX=2`다. 앱 swap은 허용하지 않는다. 호스트의 기존 swap은 바꾸지 않는다. 후보 기동 전 여유 RAM 736MiB, 기동 후 256MiB를 요구한다. 필요하면 유휴 CI runner와 CodeDeploy 에이전트의 cold page만 기존 swap으로 회수한다. 앱·실행 중 runner·프론트 컨테이너·보안 에이전트는 회수 대상이 아니다. 자원이 부족하면 전환을 거부한다. 개발 환경은 기존 backend CD 자체 runner의 cold page 회수 경로를 유지하며, 다른 실행 중 frontend runner는 제외한다.

이 구성은 배포 시 기동 공백을 줄이기 위한 것이다. EC2·Nginx 장애, DB의 비호환 스키마 변경, 새 버전의 업무 오류를 해결하지 않는다. 트래픽 전환 후에는 새 버전의 오류가 전체 요청에 영향을 줄 수 있다. DB 변경은 자동 전환에서 허용하지 않으며, 양쪽 앱이 호환되는 별도 이관을 먼저 검토해야 한다.

## 최초 이관

1. 운영 인스턴스 identity·기존 JAR SHA·배치 유휴·Nginx·Alloy 구성을 검증하고 백업한다. 외부 관측기를 먼저 실행한다.
2. 운영 DB는 V90까지 적용되어 있고 추가 대상 테이블이 없음을 읽기 전용 확인한다. 기존 적용 SQL은 수정하지 않는다.
3. `bootstrap_backend.py --environment production --artifact … --allow-shared-state-migration` 사전 검사를 통과해야 한다. `--apply --expected-current-sha256 …`는 별도 승인된 최초 설치에만 사용한다. 이 단계는 재시작을 포함하며 무중단 전환 성과에 합치지 않는다.
4. `bootstrap_backend.install_retirement_timer()`를 운영 환경으로 실행해 종료 정리 timer를 설치한다. timer 설치는 JVM을 재시작하지 않는다.
5. 기동 후 Flyway 이력·API·공유 세션·업무 배치·메모리·JVM 재시작 횟수를 확인한다. 실패하면 저장한 기존 unit·Nginx·Alloy와 기존 JAR로 복구한다. 추가 테이블은 기존 코드가 사용하지 않으므로 임의 DROP 또는 Flyway repair를 하지 않는다.

V99만 먼저 적용해 V91~V98을 건너뛰지 않는다. 개발과 동일한 SQL 바이트·버전 순서를 유지한다.

| 버전 | 이관 내용 |
|---|---|
| V91 | 공유·댓글 테이블 생성 |
| V92~V95 | V91에서 새로 생성한 공유·댓글 테이블의 제약·인덱스 정리 |
| V96 | 검토 제안 테이블 생성 |
| V97 | 새 공유 테이블의 암호화 토큰 컬럼 추가 |
| V98 | 거래 요청의 멱등성 키·처리 상태를 저장하는 테이블 생성 |
| V99 | Spring Session 2개 테이블과 비회원 분석 상태 테이블 생성 |

총 7개 새 테이블이며 기존 업무 테이블에 UPDATE/DELETE하는 SQL은 없다. V93의 UPDATE/DELETE 대상은 V91에서 새로 만든 공유·댓글 테이블뿐이다. V90→V99 회귀 테스트에서 기존 테이블 OID·버전별 체크섬 보존과 추가 테이블 목록을 확인한다.

## 이후 배포

운영 CD는 기존 `systemctl restart`로 되돌아가지 않는다. 최초 설치 전에는 아티팩트만 만들고 서버 변경 없이 중단한다. 설치 후 `deploy_backend.py --environment production --artifact … --version …`으로 전환한다. 이전/신규 JAR의 migration 파일 해시가 다르면 자동 배포를 거부한다.

운영 CD의 `--verify-existing-release`는 최초 설치 직후나 재시도에서 실행 중 JAR와 새 아티팩트의 SHA256이 같으면 중복 전환을 생략한다. 정상 readiness·공개 프록시·일치하는 라우팅·퇴역 완료·비활성 PID0·256MiB 여유 RAM을 확인한 뒤 검증 기록을 남긴다. 다른 JAR에는 기존 후보 기동 기준736MiB를 그대로 적용한다. 이 옵션 없는 명시적 같은 JAR 전환 실험은 실제 Blue/Green 전환을 수행한다.

프론트엔드 배포는 해당 push에 백엔드 변경이 포함되면 같은 커밋의 운영 백엔드 CD 성공을 먼저 확인한다. 최초 설치 전 CD가 중단되면 기존 프론트가 유지된다. 승인된 관측·최초 설치를 완료한 뒤 백엔드 실패 job을 재실행하고, 성공을 확인한 다음 프론트 실패 job을 재실행한다. DB 이관 전에 새 프론트를 먼저 전환하지 않는다.

실패한 릴리스 뒤에 배포 스크립트 보완 커밋이 추가됐다면 이전 커밋의 프론트 job을 재시도하지 않는다. `Build and deploy frontend`의 `workflow_dispatch`를 최신 main에서 실행한다. 수동 실행도 같은 커밋의 운영 백엔드 push CD 성공을 반드시 확인한다. 해당 push CD가 없거나 실패했다면 프론트는 유지한다.

## API 공백 비교

최초 설치·DB 이관과 다음 두 실험을 별도 기록한다. 재시작 기준값 측정도 실제 운영 API 공백을 발생시키므로 승인된 시간에만 실행한다.

- 동일 JAR SHA·JVM 옵션·Nginx·공개 API·관측 호스트·0.5초 요청 간격·3초 timeout을 고정한다.
- 재시작: 유휴 상태에서 현재 활성 서비스를 재시작한다. 전후 60초 이상의 외부 요청을 보존한다.
- Blue/Green: 실제 배치의 시작·진행·완료 로그와 PID를 확인하며 동일 JAR를 후보 슬롯에 기동해 전환한다. 배치를 강제 중단하거나 테스트 sleep으로 대체하지 않는다.
- HTTP 상태뿐 아니라 응답 JSON의 `items` 배열을 확인한다. timeout·JSON 오류·미전송을 따로 집계한다.
- 실패 요청 수, 최초~마지막 실패 요청 시작시각의 차이, 실패 후 첫 정상 응답 관측시각, API p95, 관측 건수, OOM·재시작·배치 완료 여부를 기록한다.
- 실패 0건은 해당 간격에서의 관측 결과다. 이를 실제 중단시간 0ms로 표현하지 않는다. 재시작과 Blue/Green의 배치 유무 등 다른 조건은 함께 명시한다.

개발 서버의 69.5초 및 9,942회 실패 0건은 개발 환경 결과다. 운영 결과와 합치거나 운영 실측으로 바꾸어 쓰지 않는다.
