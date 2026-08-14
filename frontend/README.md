# DAYNOMY FRONTEND

## 기술 스택

| 항목 | 버전 |
|------|------|
| React | 19 |
| TypeScript | 6.0 |
| Vite | 8.2 |

---

## 필수 설치 항목

- **Node.js 20.19 이상(20.x) 또는 22.12 이상** 설치
- Git

---

## 환경 설정

### 1. 저장소 클론

```bash
git clone https://github.com/woowacourse-teams/2026-DAYNOMY
cd daynomy/frontend
```

### 2. 의존성 설치

```bash
npm install
```

---

## 빌드

```bash
# 프로덕션 빌드
npm run build
```

빌드 결과물은 `dist/` 폴더에 생성됩니다.

---

## 실행

```bash
# 개발 서버 실행
npm run dev
```

서버가 정상 기동되면 `http://localhost:5173`으로 접근할 수 있습니다.

---

## 테스트

```bash
# 단위 테스트
npm test

# 린트 실행
npm run lint
```

---

## Sentry 오류 모니터링

### 런타임 환경 변수

배포 환경에 다음 값을 설정합니다. 로컬 기본값은 `.env.example`을 참고합니다.

| 변수 | 값 |
|------|----|
| `SENTRY_DSN` | Sentry 프로젝트의 Client Key(DSN) |
| `SENTRY_ENVIRONMENT` | `staging` 또는 `production` |

Sentry는 프로덕션 빌드이면서 환경이 `staging` 또는 `production`일 때만 초기화됩니다. 개발 서버와 `local` 환경에서는 전송하지 않습니다.

### 소스맵과 릴리스

배포 빌드에 다음 값을 설정합니다.

| 변수 | 용도 |
|------|------|
| `SENTRY_AUTH_TOKEN` | 소스맵 업로드용 CI Secret |
| `SENTRY_ORG` | Sentry 조직 slug |
| `SENTRY_PROJECT` | Sentry 프로젝트 slug |
| `SENTRY_RELEASE` | 배포한 Git commit SHA |

네 값이 모두 있을 때만 숨김 소스맵을 생성해 Sentry에 업로드하고, 업로드가 끝나면 배포 결과물에서 소스맵을 삭제합니다. `SENTRY_AUTH_TOKEN`은 브라우저에 전달하지 않고 CI Secret으로만 관리합니다.

### 배포 검증

1. 배포된 사이트의 브라우저 콘솔에서 아래 오류를 한 번 발생시킵니다.

   ```js
   setTimeout(() => {
     throw new Error("Sentry deployment verification");
   }, 0);
   ```

2. Sentry에서 이벤트 수집, 원본 TS/TSX 스택, `release`의 Git SHA, `environment` 값을 확인합니다.
3. 이벤트의 사용자, 요청 헤더·쿠키·본문·쿼리 문자열이 제거됐는지 확인한 뒤 테스트 이슈를 처리 완료합니다.

오류 메시지 자체에는 토큰이나 개인정보를 넣지 않습니다.

### 알림 규칙

Sentry 프로젝트의 Alerts에서 아래 Issue Alert를 설정합니다.

- 새 이슈 또는 회귀 발생 시 즉시 알림
- 같은 이슈가 5분 안에 10회 이상 발생하면 알림
- 알림 대상은 프론트엔드 담당 채널 또는 이메일

반복 오류 임계값은 첫 운영 주의 실제 트래픽과 오류량을 확인한 뒤 조정합니다.
