# 프론트엔드 릴리스와 이전 버전 재배포

프론트엔드는 동일한 코드와 Dockerfile을 개발·운영 EC2에 배포합니다.
`dev`와 `main`에 프론트 변경을 푸시하면 GitHub Actions가 Docker 이미지를
빌드하고 해당 EC2에 자동 배포합니다. `dev`는 SHA 이미지를 사용합니다.
`main`은 새 프론트 버전일 때 버전 이미지를 사용하고, 그 외 변경은 SHA
이미지를 사용합니다. 백엔드 JAR·DB의 버전은 이 절차에 포함하지 않습니다.

## 버전 증가 기준

| 구분  | 증가할 때                                                        |
| ----- | ---------------------------------------------------------------- |
| Major | 사용자 또는 관리자 화면의 기존 사용 방식이 호환되지 않게 바뀔 때 |
| Minor | 기존 사용 방식을 유지하며 기능을 추가할 때                       |
| Patch | 기존 기능의 오류를 수정할 때                                     |

첫 운영 릴리스는 `v1.0.0`으로 시작합니다. 릴리스 버전은
`frontend/package.json`의 `version`에서 읽고, `package-lock.json`도 함께
갱신합니다.

## 버전 릴리스

1. 프론트 변경과 함께 `package.json`·`package-lock.json` 버전을 올리고,
   [CHANGELOG.md](CHANGELOG.md)에 같은 버전의 변경 내역을 기록합니다.

   ```bash
   cd frontend
   npm version 1.0.1 --no-git-tag-version
   ```

2. `dev`에서 확인한 뒤 `main`에 병합합니다. **Build and deploy frontend**가
   자동으로 테스트, Docker 빌드, 이미지 발행, EC2 배포와 화면/API 확인을
   수행합니다. 모두 성공하면 해당 커밋에 `v1.0.1` Git tag를 자동 생성합니다.
3. Actions에서 Git tag, 빌드 커밋, GHCR 이미지의
   `org.opencontainers.image.version`·`revision` 라벨, Sentry release가
   일치하는지 확인합니다. 동일 버전 이미지가 이미 있다면 commit 라벨을
   확인하고 재사용합니다.
4. 운영 URL에서 공개·관리자 화면과 현재 백엔드 API 연결을 확인합니다.
   배포 워크플로의 자동 검사는 프론트 HTML, `/api/news`, `/api/auth/csrf`,
   Google 로그인 리디렉션까지 확인합니다. 나머지 사용자 흐름은 직접 확인합니다.

빌드나 배포 확인이 실패하면 Git tag는 생성되지 않습니다. 해당 Actions 실행을
재시도하면 이미 발행된 버전 이미지는 같은 commit인지 확인한 뒤 재사용합니다.
버전 태그가 이전 커밋에 있으면 이후 `main` 변경은 기존대로 SHA 이미지로
자동 배포됩니다. 새 버전을 발행할 때에만 버전을 올립니다.

## 이전 프론트 버전 재배포

운영 EC2에서 보관된 GHCR 버전 이미지를 재빌드하지 않고 배포합니다.
배포 전 해당 프론트가 **현재 운영 백엔드 API**와 호환되는지 확인합니다.
운영 Compose 파일과 포트는 평소 배포와 동일합니다.

```bash
export FRONTEND_IMAGE=ghcr.io/woowacourse-teams/2026-daynomy-frontend:v1.0.0
docker login ghcr.io
docker compose --file /opt/daynomy/frontend/compose.yml pull
docker compose --file /opt/daynomy/frontend/compose.yml up --detach --wait --remove-orphans
```

재배포 후 `https://daynomy.com`의 공개·관리자 화면과 필요한 API를 확인합니다.
호환되지 않거나 문제가 있으면 직전에 동작하던 이미지 태그로
`FRONTEND_IMAGE`를 다시 설정해 같은 명령을 실행합니다. 사용한 이미지 태그와
Git commit을 배포 기록에 남깁니다. 이후 `main` 자동 배포가 실행되면
그 커밋의 SHA 이미지로 다시 교체됩니다.
