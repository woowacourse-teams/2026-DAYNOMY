# 프론트엔드 릴리스와 배포 복구

`dev`·`main` 변경은 검사·빌드 후 각각 개발·운영 EC2에 자동 배포합니다.

## 버전 릴리스

| 구분  | 증가할 때                                                |
| ----- | -------------------------------------------------------- |
| Major | 사용자·관리자 화면의 기존 사용 방식과 호환되지 않는 변경 |
| Minor | 기존 사용 방식을 유지하는 기능 추가                      |
| Patch | 기존 기능의 오류 수정                                    |

1. `dev` 기준의 작업 브랜치에서 `package.json`·`package-lock.json` 버전을 올리고
   [CHANGELOG.md](CHANGELOG.md)를 갱신합니다. 이번 릴리스 대상은 `2.0.0`입니다.

   ```bash
   cd frontend
   npm version 2.0.0 --no-git-tag-version
   ```

2. `dev`에서 확인한 뒤 `main`에 병합합니다. 포맷·린트·타입 검사·테스트·빌드와
   배포 검증이 모두 성공하면 해당 커밋에 `v2.0.0` Git tag를 생성합니다.
3. Git tag·이미지 버전·Sentry release가 일치하는지 확인하고 공개 기능을 직접 확인합니다.
   자동 검사는 프론트 HTML, `/api/news`, `/api/auth/csrf`, Google 로그인 리디렉션까지입니다.

실패하면 Git tag를 만들지 않습니다. 재시도 시 같은 커밋의 기존 버전 이미지를 재사용합니다.
버전은 개발자가 올리며, 이미 태그가 있는 버전의 후속 변경은 SHA 이미지로 배포합니다.

## v2.0.0 반영 순서

이번 버전 준비 작업은 이슈 #590과 `chore/FE/#590` 브랜치에서 진행합니다. 버전 파일·CHANGELOG만으로 운영 배포 완료를
판단하지 않으며 다음 순서로 확인합니다.

1. `package.json`과 `package-lock.json`의 최상위·루트 패키지 버전이 모두 `2.0.0`인지 확인합니다. 이미 변경되었다면 `npm version`을 다시 실행하지 않습니다.
2. `CHANGELOG.md`의 `## v2.0.0`에 v1.1.3 이후 변경을 정리하고, `Unreleased`는 다음 릴리스용으로 유지합니다.
3. 작업 브랜치에서 포맷·린트·타입 검사·테스트·빌드와 릴리스 스크립트 검사를 완료하고, `chore/FE/#590 → dev` PR의 CI 결과를 확인합니다.
4. 개발 배포에서 스크린 리더·키보드 접근성, 첫 방문 랜딩·사용 가이드, 미국 종목·달러 거래, 투자 캘린더, 소식·인물 탐색, 리그 기록·복기와 관리자 수집 화면을 확인합니다.
5. `dev → main` 릴리스 PR에 버전·변경 내용·실제 검증 결과를 적습니다. 미국 종목·환율·달러 거래, 투자 캘린더, 리그와 인물 게시물 검토에 필요한 백엔드 API가 대상 환경에 반영되어 있는지 확인합니다. 현재 저장소의 Flyway 최신 버전은 V53이며, 대상 DB의 적용 이력과 비교해 누락된 마이그레이션을 백엔드 배포 절차로 적용합니다. 미국 시세·환율 설정과 V51 배포 전 확인은 [백엔드 README](../backend/README.md#미국-주식etf-종목-및-일별-종가-eodhd)를 따릅니다.
6. 운영 프론트 배포·라우팅 검증과 자동 `v2.0.0` 태그 발행을 확인합니다. 태그·GHCR 이미지·Sentry release가 같은 배포 커밋을 가리키는지 확인하고 운영 기능을 직접 점검합니다.

개발·운영 GitHub Environment에는 `VITE_POSTHOG_PROJECT_TOKEN`·`VITE_POSTHOG_HOST`를 각각
설정할 수 있습니다. 두 값이 모두 있을 때만 PostHog를 사용하고, 값 변경 후 다시 빌드합니다.
설정 상세 내용은 [프론트 README](README.md#환경별-배포)를 따릅니다.

다음 명령은 로컬 검증이며 실제 개발·운영 배포 검증을 대신하지 않습니다.

```bash
cd frontend
npm run format:check
npm run lint
npm run typecheck
npm test
npm run build
bash scripts/test-release-version.sh
# 브라우저 흐름과 Docker 배포 검증은 해당 실행 환경에서 수행
npm run test:e2e
bash scripts/test-deploy-frontend.sh
```

일반 `npm run build`의 정적 파일에는 릴리스 버전이 별도로 표시되지 않습니다. 운영 CI는
`resolve-release-version.sh`의 `image_tag`를 Docker 이미지 태그·`org.opencontainers.image.version`
라벨과 `SENTRY_RELEASE`에 공통으로 사용합니다. 새 릴리스에서는 세 값이 `v2.0.0`인지 확인합니다.
HTML의 `daynomy-deployment` 메타 태그는 버전 대신 환경·커밋을 기록하므로 같은 이미지의
`org.opencontainers.image.revision`과 커밋이 일치하는지 대조합니다.

프론트 `v2.0.0` 버전은 백엔드 Gradle 버전, OpenAPI 버전, 분석 스키마·알고리즘 버전,
브라우저 저장 키 버전과 독립적입니다. 이 값들은 단순 릴리스 번호 변경에 맞춰 올리지 않습니다.

## 무중단 배포와 자동 롤백

각 EC2에서 `frontend-3000`·`frontend-3001`을 번갈아 사용합니다.
같은 환경은 한 번에 하나씩 배포하고 개발·운영은 독립적으로 실행합니다.

```text
현재 컨테이너 서비스 유지
└─ 이전 Nginx 요청 종료 확인 → 대기 포트에 새 컨테이너 실행
   └─ 상태·이미지 ID·환경·커밋 검증 → 해시 자산 보관
      └─ 호스트 Nginx upstream 변경·설정 검사·reload
         ├─ 공개 HTML 검증 성공 → 이전 컨테이너를 복구용으로 유지
         └─ 실패 → 이전 upstream으로 복구·재검증
```

- 전환·롤백에 노출된 컨테이너는 진행 중 요청 종료 후 재사용합니다. 120초를 넘으면 배포를 중단하고 재시도합니다.
- 복구해도 Actions는 실패이며 새 Git tag를 만들지 않습니다.
- API·OAuth 검사 실패는 프론트 롤백 대상이 아닙니다. 백엔드·DB는 변경하지 않습니다.
- 무중단 범위는 프론트 버전 교체입니다. 최초 배포에는 이전 컨테이너가 없으며 롤백 실패·서버 장애는 수동 복구합니다.

## EC2 최초 설정

개발·운영 EC2 각각의 저장소 `frontend` 디렉터리에서 **최초 한 번** 설정합니다.
이후 upstream 파일을 초기화하지 않습니다. 기존 인증서·API·OAuth 설정은 유지합니다.
Runner 사용자가 비대화형 `sudo`로 설정 설치·Nginx 검사·reload를 실행할 수 있어야 합니다.

```bash
sudo install -d -o "$(id -un)" -g "$(id -gn)" -m 755 /opt/daynomy/frontend/assets
if docker inspect frontend >/dev/null 2>&1; then
  docker cp frontend:/usr/share/nginx/html/assets/. /opt/daynomy/frontend/assets/
fi
sudo install -m 644 host-nginx.upstream.conf /etc/nginx/conf.d/daynomy-frontend-upstream.conf
```

기존 `server` 블록에 아래 화면·해시 자산 설정을 적용합니다. 기존 프록시 헤더는 유지하고,
`add_header`의 상속 변경을 고려해 기존 보안 헤더도 해당 location에 적용합니다.
진행 중 요청을 강제 종료하는 `worker_shutdown_timeout`은 설정하지 않습니다.

```nginx
location ~ "^/assets/([^/]+-[A-Za-z0-9_-]{8}\.[^/]+)$" {
    alias /opt/daynomy/frontend/assets/$1;
    add_header Cache-Control "public, max-age=31536000, immutable";
}

location / {
    proxy_pass http://daynomy_frontend;
    add_header X-Daynomy-Frontend $upstream_addr always;
    # 기존 proxy_set_header 설정 유지
}
```

```bash
sudo nginx -t && sudo systemctl reload nginx
```

`/etc/nginx/nginx.conf`가 `/etc/nginx/conf.d/*.conf`를 http 블록에서 include하는지 확인합니다.
첫 배포는 기존 `frontend:3000`을 유지한 채 3001로 전환하고,
다음 배포에서 진행 중 요청이 끝난 뒤 기존 단일 컨테이너를 정리합니다.
개발·운영 모두 저장소의 `compose.yml`을 사용합니다.

## 캐시와 이전 자산

| 대상                                 | 정책                                  |
| ------------------------------------ | ------------------------------------- |
| HTML·SPA 경로, 해시 없는 public 파일 | `no-cache`                            |
| 콘텐츠 해시가 있는 정적 파일         | 호스트 Nginx에서 1년 `immutable` 캐시 |
| 없는 `/assets/` 파일                 | 404 반환, 장기 캐시 미적용            |

이전 HTML에서도 파일을 읽을 수 있도록 해시 자산을 `/opt/daynomy/frontend/assets`에 누적 보관합니다.
디스크 사용량을 확인하고, 지원할 이전 버전 기간을 정하기 전에는 일괄 삭제하지 않습니다.

## 버전·실패 원인 확인

Actions의 **Frontend deployment** 요약에서 버전·커밋·복구 결과를 확인합니다.
**Frontend CD stages** 요약에는 GHCR 로그인·이미지 다운로드·배포·API/OAuth 검사의 결과가 표시됩니다.
로그인은 2분, 이미지 다운로드는 5분을 넘으면 실패하고 배포를 시작하지 않습니다.
배포 로그와 요약의 `Stage`에서 이미지·호스트 검사, 이전 버전 확인, 요청 종료 대기,
자산 보관, 후보 컨테이너 실행·검증, Nginx 전환, 공개 응답 검증 중 어디서 멈췄는지 확인합니다.

AWS IAM·SSH 없이 개발·운영 서버 상태를 확인하려면 Actions의 **Diagnose deployment runner**를
수동 실행하고 `target_environment`를 선택합니다. 기본값은 `development`이며 `frontend-dev` 러너를
사용합니다. `production`은 `frontend-prod` 러너를 사용합니다. 진단 코드가 반영된 브랜치에서
실행하며, 기본 브랜치에도 해당 workflow가 반영돼 있어야 합니다.
서버 자원·서비스 상태와 GitHub/GHCR/아티팩트 저장소 연결을 읽기 전용으로 조회합니다.
`log_period`의 기본값 `last-hour`는 진단 실행 시점부터 최근 1시간의 러너·시스템 로그를
조회하고 오류 유형·시각만 출력합니다. `incident-2026-10-06`은 기존 장애 시간대인
2026-10-06 06:00~10:00 UTC를 조회합니다. 배포 실패 후 진단을 실행해 해당 시간대의 기록을 확인합니다.
조회 권한 부족·로그 삭제·분류되지 않은 오류는 결과가 없을 수 있습니다.
익명 연결 검사는 인증된 이미지·아티팩트 다운로드 성공을 보장하지 않습니다.
진단과 시간 제한 추가 후에도 실제 배포 성공 여부와 별개로 당시 지연 원인을 확인해야 합니다.

EC2에서는 upstream·응답 포트·이미지 라벨·공개 HTML을 대조합니다. 운영은 도메인을 바꿉니다.

```bash
cat /etc/nginx/conf.d/daynomy-frontend-upstream.conf
curl -I https://dev.daynomy.com/ # X-Daynomy-Frontend와 upstream 포트 대조
curl -I https://dev.daynomy.com/assets/index-실제해시.js
docker ps --filter name=frontend
# upstream 포트가 3001인 경우
active_image="$(docker inspect frontend-3001 --format '{{.Image}}')"
docker image inspect "$active_image" --format 'version={{index .Config.Labels "org.opencontainers.image.version"}} commit={{index .Config.Labels "org.opencontainers.image.revision"}}'
curl --fail --silent --header 'Cache-Control: no-cache' https://dev.daynomy.com/ \
  | grep -o 'name="daynomy-deployment" content="[^"]*"'
```

실패하면 Actions의 첫 오류, `sudo nginx -t`, 해당 컨테이너의 `docker inspect`·`docker logs`를 확인합니다.
파일과 실제 연결 포트가 다르면 정상 컨테이너로 upstream을 수정하고 검사·reload합니다.
로그 공유 전 개인정보·인증 정보를 가립니다.

## 이전 버전 수동 재배포

운영 EC2의 저장소 `frontend` 디렉터리에서 현재 백엔드와 호환되는 GHCR 버전을 선택합니다.

```bash
export FRONTEND_IMAGE=ghcr.io/woowacourse-teams/2026-daynomy-frontend:v1.0.0
docker login ghcr.io
docker pull "$FRONTEND_IMAGE"
export COMPOSE_FILE="$PWD/compose.yml" DEPLOY_ENV=production DEPLOY_URL=https://daynomy.com
export DEPLOY_VERSION="$(docker image inspect "$FRONTEND_IMAGE" --format '{{index .Config.Labels "org.opencontainers.image.version"}}')"
export GITHUB_SHA="$(docker image inspect "$FRONTEND_IMAGE" --format '{{index .Config.Labels "org.opencontainers.image.revision"}}')"
bash scripts/deploy-frontend.sh
```

재배포 후 화면·API를 확인합니다. 다음 `main` 자동 배포가 실행되면 해당 커밋의 이미지로 전환합니다.

## 검증

Docker·Compose·Python 3 환경에서 연속 요청·진행 중 응답·이전 자산·롤백을 검증합니다.

```bash
cd frontend
bash scripts/test-deploy-frontend.sh
```

병합 후 개발 사이트에서 전환·롤백·캐시와 공개·관리자 기능을 확인한 뒤 운영에 적용합니다.
