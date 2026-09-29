# DAYNOMY FRONTEND

## 기술 스택

| 항목       | 버전 |
| ---------- | ---- |
| React      | 19   |
| TypeScript | 6.0  |
| Vite       | 8.2  |

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

# 환경별 배포 빌드
npm run build:development
npm run build:production
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

### 전략

테스트는 구현 계층이 아니라 실패 위험에 맞춰 작성합니다. 핵심 사용자 흐름이나
복잡한 상태 변화, API 계약, 재발 가능한 버그를 우선 검증하고 단순 렌더링, CSS
세부 값, 외부 라이브러리 내부 동작은 테스트하지 않습니다.

| 종류                 | 대상                                        | 도구                           |
| -------------------- | ------------------------------------------- | ------------------------------ |
| 단위·API 계약 테스트 | 순수 로직, 요청·응답 변환, 데이터 검증      | `node:test`                    |
| 컴포넌트 통합 테스트 | 사용자 입력, 화면 상태, URL과 주요 상호작용 | Vitest, Testing Library, jsdom |
| 브라우저 E2E 테스트  | 로그인·권한, 검색, 뉴스 상세의 연결된 흐름  | Playwright Chromium            |

API 테스트는 실제 서버 대신 Axios adapter로 응답을 대체합니다. 컴포넌트 테스트는
구현 세부사항보다 사용자가 확인하는 텍스트, 버튼, 링크, URL과 오류 상태를
검증합니다. 기능 변경이나 버그 수정 시 기존 테스트로 회귀를 확인하고, 기존
테스트가 실패를 재현하지 못할 때 해당 시나리오를 추가합니다.

E2E는 여러 화면을 거치는 흐름과 브라우저 동작을 검증합니다. 빌드된 프론트를
열고 `/api` 응답을 테스트에서 고정하므로 CI는 Google 계정이나 개발 서버에
의존하지 않습니다. 실제 Google OAuth와 백엔드 연동은 개발 배포 후 별도로 확인합니다.

### 현재 자동화 범위

| 기능            | 검증 내용                                                                     |
| --------------- | ----------------------------------------------------------------------------- |
| 뉴스 목록 API   | 페이지 변환, 응답 매핑, 잘못된 응답 계약                                      |
| 뉴스 탐색 화면  | 목록·상세 표시, 카테고리 변경, 오류·비로그인 상태                             |
| 뉴스 검색 API   | 검색 조건과 URL, 응답 매핑, 오류 응답                                         |
| 뉴스 검색 화면  | 입력 검증, 결과·빈 화면·오류·재시도, URL·카테고리·페이지·브라우저 탐색 동기화 |
| 종목 목록 API   | 응답 매핑, 잘못된 응답 계약                                                   |
| 로그인 화면     | Google 로그인 시작, OAuth 실패 안내                                           |
| 관심 종목 화면  | 북마크 추가·해제와 저장, API 실패 시 대체 상태                                |
| 포트폴리오 화면 | 분석 결과, 실패 안내와 재시도                                                 |
| 오류 모니터링   | Sentry 이벤트 개인정보 및 URL 쿼리 제거                                       |

뉴스 검색 화면은 사용자 입력, API 요청, URL 상태, 필터, 페이지네이션, 브라우저
탐색과 오류 복구가 함께 동작하는 대표 사용자 흐름이므로 컴포넌트 통합 테스트
대상으로 선정했습니다. 뉴스 탐색, 로그인, 관심 종목과 포트폴리오는 각 기능의
대표 정상 흐름과 사용자 대응이 필요한 실패 흐름을 검증합니다.

### 실행

```bash
# 단위·API 계약 테스트
npm run test:unit

# 컴포넌트 통합 테스트
npm run test:ui

# 전체 테스트
npm test

# 브라우저 E2E (최초 1회 Chromium 설치)
npx playwright install chromium
npm run build && npm run test:e2e

# 전체 검증
npm run format:check
npm run lint
npm run typecheck
npm run build
```

`dev` 또는 `main` 대상 Pull Request에서 프론트엔드 파일이 변경되면 Frontend CI가
포맷, 린트, 타입 검사, 기존 테스트, Docker 배포 검증, 빌드와 브라우저 E2E를
순서대로 실행합니다. `Frontend checks`는 `dev`·`main`의 필수 검사이며,
E2E 실패 시 스크린샷과 trace를 Actions 아티팩트에서 확인할 수 있습니다.

---

## 환경별 배포

프론트 버전 기준, Git tag 발행 및 이전 이미지 재배포 절차는
[RELEASING.md](RELEASING.md)에 기록합니다. 버전별 변경 내역은
[CHANGELOG.md](CHANGELOG.md)를 확인합니다.
`main`에 새 프론트 버전을 병합하면 기존 Docker 배포 흐름에서 자동으로 배포하고,
검증이 성공한 뒤 Git tag를 생성합니다. Actions 수동 실행은 필요하지 않습니다.

| 브랜치 | GitHub Environment | URL                       | 호스트 포트   |
| ------ | ------------------ | ------------------------- | ------------- |
| `dev`  | `development`      | `https://dev.daynomy.com` | `3000`·`3001` |
| `main` | `production`       | `https://daynomy.com`     | `3000`·`3001` |

`.github/workflows/deploy-frontend.yml`은 브랜치에 맞는 Vite mode로 빌드하고,
`dev`는 개발 EC2의 `frontend-dev` 라벨 Runner로, `main`은 운영 EC2의
`frontend-prod` 라벨 Runner로 배포합니다. 각 EC2에서 프론트 컨테이너 두 개를
`127.0.0.1:3000`·`3001`에 번갈아 실행하고, 검증 후 호스트 Nginx 연결을 전환합니다.
개발·운영 모두 저장소의 `frontend/compose.yml`을 사용하며 앱 EC2와 DB는 각각 분리합니다.
최초 Nginx 설정과 이전 해시 파일 보관·실패 복구는 [RELEASING.md](RELEASING.md)를 확인합니다.

GitHub의 `development`, `production` Environment에는 다음 값을 각각 설정합니다.

| 종류     | 이름                 | 용도                                         |
| -------- | -------------------- | -------------------------------------------- |
| Variable | `GA_MEASUREMENT_ID`  | 환경별 별도 GA4 웹 데이터 스트림 ID          |
| Variable | `SENTRY_DSN`         | 환경별 별도 Sentry 프로젝트 DSN              |
| Variable | `SENTRY_ENVIRONMENT` | development는 `staging`, 운영은 `production` |
| Variable | `SENTRY_ORG`         | Sentry 조직 slug                             |
| Variable | `SENTRY_PROJECT`     | Sentry 프로젝트 slug                         |
| Secret   | `SENTRY_AUTH_TOKEN`  | 소스맵 업로드 토큰                           |

개발·운영은 각각 다른 GA4 Measurement ID와 Sentry 프로젝트 DSN을 사용합니다.
개발 Sentry 이벤트는 `staging`, 운영 이벤트는 `production`으로 구분합니다.
각 Environment의 `SENTRY_AUTH_TOKEN` Secret은 빌드 중 소스맵 업로드에 사용하며,
설정 변경 후에는 프론트를 다시 빌드·배포해야 합니다.

각 EC2의 호스트 Nginx는 화면 요청을 프론트엔드 컨테이너로, `/api`와 OAuth 요청을
같은 EC2의 백엔드로 전달합니다.

배포 확인은 각 URL의 HTML에 해당 환경과 Git commit SHA가 표시되는지,
`/api/news`와 `/api/auth/csrf`의 JSON 응답, Google 로그인 시작 및 OAuth
리디렉션을 검사합니다. 이는 프록시 연결 확인입니다. 백엔드 CI는
별도로 API 테스트를 실행하지만 배포된 서버의 전체 API 기능 검증은 아직 연결되지
않았습니다. 공용 관리자 계정은 자동 테스트에 사용하지 않습니다. 개발 전용 자동
인증과 테스트 데이터 정리 절차가 준비되면 로그인·쓰기 검증을 개발 배포에 연결합니다.
실패 원인은 Actions 로그와 다음 명령으로 확인합니다.

```bash
docker ps --filter name=frontend
# 실제 활성 포트에 해당하는 컨테이너 선택
docker logs frontend-3001
```

---

## TypeScript와 외부 입력

- 앱·Node 설정은 `strict`를 사용하고 테스트 설정은 앱 설정을 상속합니다.
- DTO와 일반 객체 형태는 기존 코드처럼 `type`을 우선 사용합니다. 합집합·교차·유틸리티 타입도 `type`으로 표현하고, 선언 병합이나 확장 가능한 객체 계약이 실제로 필요할 때 `interface`를 사용합니다.
- 새로 작성하거나 수정하는 코드에서 `as`는 `as const`처럼 값을 좁히거나 런타임 검사 뒤 컴파일러가 관계를 추론하지 못할 때 사용합니다. 응답 타입으로 바로 단언하거나 `any`로 오류를 숨기지 않습니다. `!`는 값의 존재가 보장되는 테스트·DOM 경계에서만 사용하고 앱 코드에서는 먼저 확인합니다.
- API JSON과 브라우저 저장소 값은 실행 시 타입이 보장되지 않습니다. `request<T>`의 `T`도 서버 응답을 검증하지 않습니다. 런타임 검증이 필요한 경계에서는 값을 `unknown`으로 받고, 필요한 필드를 확인한 뒤 사용합니다.
- 현재 뉴스 목록·상세와 CSRF·오류 응답은 경계에서 확인합니다. 관리자·검색·포트폴리오 API와 브라우저 저장소는 기존 검증을 유지합니다. 그 밖의 서버 DTO 응답은 현재 API 계약을 따르며, 계약 변경이나 오류가 발견되면 해당 경계의 검증을 추가합니다.

---

## 모니터링 구축

### Sentry 오류 모니터링

Sentry는 프론트엔드 오류의 원인과 발생 환경을 확인하기 위해 사용합니다.

| 환경         | 수집 여부     | 목적                  |
| ------------ | ------------- | --------------------- |
| `production` | 수집          | 실제 사용자 오류 대응 |
| `staging`    | 수집          | 배포 전 오류 확인     |
| `local`      | 수집하지 않음 | 개발 중 오류 제외     |

현재 적용된 설정:

- `Sentry.ErrorBoundary`를 통한 렌더링 오류 수집
- 운영·스테이징 환경에서만 Sentry 초기화
- `sendDefaultPii: false` 적용
- 사용자 정보 제거
- 요청 헤더·쿠키·본문·쿼리 문자열 제거
- URL 쿼리 문자열 제거
- 숨김 소스맵 생성 및 Sentry 업로드
- 배포 결과물에서 소스맵 삭제
- 일반 배포는 Git commit SHA, 버전 배포는 Git tag를 Sentry release로 기록

### Sentry 환경 변수

| 변수                 | 용도                            |
| -------------------- | ------------------------------- |
| `SENTRY_DSN`         | Sentry 프로젝트 Client Key(DSN) |
| `SENTRY_ENVIRONMENT` | `staging` 또는 `production`     |
| `SENTRY_AUTH_TOKEN`  | 소스맵 업로드용 CI Secret       |
| `SENTRY_ORG`         | Sentry 조직 slug                |
| `SENTRY_PROJECT`     | Sentry 프로젝트 slug            |
| `SENTRY_RELEASE`     | 일반 배포 SHA 또는 버전 Git tag |

`SENTRY_AUTH_TOKEN`은 브라우저에 전달하지 않고 GitHub Actions Secret으로만 관리합니다.

### Sentry 알림

Sentry Alerts에서 다음 조건을 설정합니다.

- 새 오류 또는 회귀 발생
- 치명적 오류 발생
- 짧은 시간 동안 오류 급증

반복 오류 임계값은 실제 운영 트래픽을 확인한 뒤 조정합니다.

### Custom Integration, Session Replay, Performance Monitoring

- Sentry 오류 발생 시 GitHub Issue를 자동 생성하는 Custom Integration은 추후 필요할 때 추가합니다.
- Session Replay는 Sentry 이벤트만으로 재현하기 어려운 오류가 반복될 때 추가합니다.
- Performance Monitoring은 검색·뉴스 API의 운영 트래픽이 충분히 쌓인 뒤 추가합니다.

### GA4 사용자 행동 모니터링

개발·운영은 각자의 `GA_MEASUREMENT_ID`를 사용합니다. 공개 화면에서만 수집하고 `/login`·`/admin`은 제외합니다. URL과 referrer의 쿼리·fragment 및 검색어 원문은 전송하지 않습니다.

#### 이벤트

| 이벤트             | 발생 시점                    | 주요 파라미터                    |
| ------------------ | ---------------------------- | -------------------------------- |
| `page_view`        | 공개 화면의 경로 변경        | `page_path` (쿼리 없는 경로)     |
| `view_news_list`   | 뉴스 목록 진입·카테고리 변경 | `category` (뉴스 카테고리 코드)  |
| `view_news_detail` | 뉴스 상세 진입               | `news_id` (기사 식별 번호)       |
| `search_news`      | 검색어 변경                  | `search_length` (검색어 글자 수) |

#### GA4 관리 화면 설정

개발·운영 웹 스트림 각각에서 확인합니다.

1. `향상된 측정`의 **사이트 검색**을 끕니다. 자동 페이지 조회는 양쪽 스트림에서 꺼져 있습니다.
2. `이벤트 → 데이터 수정`에서 URL 쿼리 매개변수 `q`를 등록합니다.
3. 운영 팀의 고정 IP는 내부 트래픽 필터를 `Testing`으로 검증한 뒤 `Active`로 전환합니다. 유동 IP 테스트는 Tag Assistant와 개발자 트래픽 필터를 사용합니다.
4. GA4가 자동 제외하지 못한 봇 트래픽은 실제 보고서에서 확인한 경우에만 추가로 처리합니다.

2026-09-26 확인: 양쪽 스트림의 사이트 검색·자동 페이지 조회는 꺼짐, URL 쿼리 `q` 수정은 등록됨. 양쪽 속성의 데이터 보관은 2개월, Google 신호 데이터는 꺼짐, 동의 설정은 비활성화, Google Ads 연결은 0개입니다. 현재 대상은 한국이며 해외 제공 전에 동의 요건을 재검토합니다. `/privacy`에 수집 범위·보관·거부 방법을 안내합니다.

[자동 수집 설정](https://support.google.com/analytics/answer/9216061?hl=ko), [URL 쿼리 수정](https://support.google.com/analytics/answer/13544947?hl=ko), [내부 트래픽 필터](https://support.google.com/analytics/answer/10104470?hl=ko)

#### 확인 방법

- 개발·운영의 `보고서 → 실시간`과 `DebugView`에서 이벤트·파라미터 확인
- 검색 직접 접속·SPA 이동의 GA 요청에 검색어 원문과 중복 `page_view`가 없는지 확인
- `/login`·`/admin`의 GA 요청 부재, 팀 IP 필터 적용 여부 확인

### Looker Studio

초기에는 Sentry와 GA4를 분리해서 확인합니다. 운영 데이터가 충분히 쌓인 뒤
방문자 수, 뉴스 조회, 검색 사용, Sentry 오류 현황을 통합해서 볼 필요가
있을 때 Looker Studio 대시보드를 추가합니다.

### MVP 적용 범위

현재 MVP에서는 다음을 적용합니다.

- 운영·스테이징 예외 자동 수집
- React Error Boundary 적용
- 로컬 환경 수집 비활성화
- Sentry 소스맵 업로드
- 일반 배포 SHA·버전 배포 Git tag 기준 release 기록
- Sentry 개인정보 제거
- GA4 페이지 조회 및 사용자 행동 이벤트 수집
- 배포 후 Sentry Release·소스맵 확인
- GA4 Realtime·DebugView 이벤트 확인

오류 테스트 시에는 테스트 메시지에 토큰이나 개인정보를 포함하지 않습니다.
