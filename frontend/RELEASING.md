# 프론트엔드 릴리스와 배포 복구

`dev`·`main`에 프론트 변경을 푸시하면 검사·빌드 후 각 EC2에 자동 배포합니다.
`dev`는 SHA 이미지, `main`은 새 버전이면 버전 이미지, 그 외에는 SHA 이미지를 사용합니다.

## 버전 릴리스

| 구분  | 증가할 때                                                |
| ----- | -------------------------------------------------------- |
| Major | 사용자·관리자 화면의 기존 사용 방식과 호환되지 않는 변경 |
| Minor | 기존 사용 방식을 유지하는 기능 추가                      |
| Patch | 기존 기능의 오류 수정                                    |

1. `package.json`·`package-lock.json` 버전을 올리고 [CHANGELOG.md](CHANGELOG.md)를 갱신합니다.

   ```bash
   cd frontend
   npm version 1.0.1 --no-git-tag-version
   ```

2. `dev`에서 확인한 뒤 `main`에 병합합니다. 포맷·린트·타입 검사·테스트·빌드와
   배포 검증이 모두 성공하면 해당 커밋에 `v1.0.1` Git tag를 생성합니다.
3. Git tag·이미지 버전·Sentry release가 일치하는지 확인하고 공개·관리자 기능을 직접 확인합니다.
   자동 검사는 프론트 HTML, `/api/news`, `/api/auth/csrf`, Google 로그인 리디렉션까지입니다.

실패하면 Git tag를 만들지 않습니다. Actions 재시도 시 같은 커밋의 기존 버전 이미지를 재사용합니다.
버전은 개발자가 올리며, 이미 태그가 있는 버전의 후속 변경은 SHA 이미지로 배포합니다.

## 배포 실행과 캐시

같은 환경은 한 번에 하나씩 배포하고 개발·운영은 독립적으로 실행합니다.
실행 중인 배포는 다음 푸시로 취소하지 않습니다. 대기열 진입 순서로 처리하며 푸시 순서는 보장하지 않습니다.

| 대상                         | Cache-Control                         |
| ---------------------------- | ------------------------------------- |
| HTML·SPA 경로                | `no-cache`                            |
| 콘텐츠 해시가 있는 정적 파일 | `public, max-age=31536000, immutable` |
| 해시가 없는 public 파일      | `no-cache`                            |
| 없는 `/assets/` 파일         | 404 반환, 장기 캐시 미적용            |

병합 후 공개 URL에서도 헤더를 확인합니다. 정적 파일 경로는 페이지 소스의 실제 파일명으로 바꿉니다.
운영 확인 시 도메인을 `daynomy.com`으로 바꿉니다.

```bash
curl -I https://dev.daynomy.com/
curl -I https://dev.daynomy.com/assets/index-실제해시.js
```

## 배포된 버전·커밋 확인

Actions의 **Frontend deployment** 요약에서 요청한 버전·커밋과 배포·롤백 결과를 확인합니다.
EC2에서는 실제 실행 중인 이미지의 라벨을 확인합니다. 롤백 후에는 이전 버전이 표시되어야 합니다.

```bash
active_image="$(docker inspect frontend --format '{{.Image}}')"
docker image inspect "$active_image" --format 'version={{index .Config.Labels "org.opencontainers.image.version"}} commit={{index .Config.Labels "org.opencontainers.image.revision"}}'
curl --fail --silent --header 'Cache-Control: no-cache' https://dev.daynomy.com/ \
  | grep -o 'name="daynomy-deployment" content="[^"]*"'
```

## 자동 롤백

- 교체 전 정상 컨테이너의 실제 이미지 ID를 보관합니다. pull·라벨 검사 실패 시 기존 컨테이너를 유지합니다.
- 컨테이너 상태·이미지 ID·공개 HTML의 환경·커밋 검증이 실패하면 이전 이미지를 로컬에서 복구하고 재검증합니다.
- 복구에 성공해도 Actions는 실패로 유지하며 새 Git tag를 만들지 않습니다.
- API·OAuth 검사만 실패하면 검증된 프론트 이미지를 유지합니다. 백엔드·DB는 롤백하지 않습니다.

정상인 이전 이미지가 없는 첫 배포, 롤백 실패, EC2·Docker 장애나 프로세스 강제 종료는 수동 복구가 필요합니다.

## 실패 확인과 수동 복구

Actions에서 처음 실패한 step과 배포 요약을 확인한 뒤 EC2에서 상태·설정·로그를 확인합니다.
로그를 공유할 때 개인정보·인증 정보를 가립니다.

```bash
docker inspect frontend --format '{{json .State}}'
docker exec frontend nginx -t
docker logs --tail 100 frontend
```

자동 복구가 실패하면 현재 백엔드와 호환되는 이전 GHCR 버전 이미지를 운영 EC2에서 재배포합니다.

```bash
export FRONTEND_IMAGE=ghcr.io/woowacourse-teams/2026-daynomy-frontend:v1.0.0
docker login ghcr.io
docker compose --file /opt/daynomy/frontend/compose.yml pull frontend
docker compose --file /opt/daynomy/frontend/compose.yml up --detach --wait --wait-timeout 120 --no-deps frontend
```

복구 후 화면·API와 실행 이미지의 버전·커밋을 확인합니다.
다음 `main` 자동 배포가 실행되면 해당 커밋의 이미지로 교체됩니다.

## 검증

Docker·Compose·Python 3가 설치된 환경에서 임시 컨테이너로 캐시·롤백을 검사합니다.

```bash
cd frontend
bash scripts/test-deploy-frontend.sh
```

병합 후 개발 EC2에서 배포 실패·복구와 공개 URL 캐시를 확인한 다음 운영에 적용합니다.
