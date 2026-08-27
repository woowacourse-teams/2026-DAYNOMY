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

| 종류                 | 대상                                        | 도구                                |
| -------------------- | ------------------------------------------- | ----------------------------------- |
| 단위·API 계약 테스트 | 순수 로직, 요청·응답 변환, 데이터 검증      | `node:test`                         |
| 컴포넌트 통합 테스트 | 사용자 입력, 화면 상태, URL과 주요 상호작용 | Vitest, Testing Library, jsdom      |
| E2E·시각적 테스트    | 현재 적용하지 않음                          | 브라우저·화면 회귀가 반복될 때 도입 |

API 테스트는 실제 서버 대신 Axios adapter로 응답을 대체합니다. 컴포넌트 테스트는
구현 세부사항보다 사용자가 확인하는 텍스트, 버튼, 링크, URL과 오류 상태를
검증합니다. 기능 변경이나 버그 수정 시 기존 테스트로 회귀를 확인하고, 기존
테스트가 실패를 재현하지 못할 때 해당 시나리오를 추가합니다.

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

# 전체 검증
npm run format:check
npm run lint
npm run typecheck
npm run build
```

`dev` 또는 `main` 대상 Pull Request에서 프론트엔드 파일이 변경되면 Frontend CI가
포맷, 린트, 테스트와 빌드를 순서대로 실행합니다.

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
- Git commit SHA를 Sentry release로 기록

### Sentry 환경 변수

| 변수                 | 용도                            |
| -------------------- | ------------------------------- |
| `SENTRY_DSN`         | Sentry 프로젝트 Client Key(DSN) |
| `SENTRY_ENVIRONMENT` | `staging` 또는 `production`     |
| `SENTRY_AUTH_TOKEN`  | 소스맵 업로드용 CI Secret       |
| `SENTRY_ORG`         | Sentry 조직 slug                |
| `SENTRY_PROJECT`     | Sentry 프로젝트 slug            |
| `SENTRY_RELEASE`     | 배포한 Git commit SHA           |

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

GA4는 방문자 수, 페이지 이동, 검색, 로그인 전환을 확인하기 위해 사용합니다.

#### 기본 설정

- GA4 계정 및 웹 데이터 스트림 생성
- Measurement ID 발급
- `GA_MEASUREMENT_ID` 환경 변수 설정
- `gtag.js`를 통한 GA4 초기화
- React Router 페이지 이동 시 `page_view` 수집

#### 현재 수집 이벤트

| 이벤트             | 발생 시점        | 주요 파라미터          |
| ------------------ | ---------------- | ---------------------- |
| `page_view`        | 페이지 이동      | `page_path`            |
| `view_news_list`   | 뉴스 목록 진입   | `category`             |
| `view_news_detail` | 뉴스 상세 진입   | `news_id`              |
| `search_news`      | 검색 실행        | `search_length`        |
| `search_no_result` | 검색 결과 없음   | `search_length`        |
| `click_login`      | 로그인 버튼 클릭 | 없음                   |
| `login_success`    | 로그인 성공 확인 | `method`               |
| `login_failure`    | 로그인 실패      | `method`, `error_code` |

검색어 원문은 전송하지 않고 검색어 길이만 전송합니다. 이메일, 전화번호, 이름,
인증 토큰 등 개인정보를 GA4 이벤트 파라미터에 포함하지 않습니다.

#### 분석 질문

- 뉴스 목록에서 뉴스 상세 화면으로 이동하는 비율은 얼마인가?
- 검색이 결과 없이 끝나는 비율은 얼마인가?
- 로그인 버튼 클릭 후 로그인에 성공하는 비율은 얼마인가?
- 로그인 실패가 발생하는 비율과 주요 실패 유형은 무엇인가?

#### 확인 방법

- GA4 `보고서 → 실시간`에서 이벤트 수집 확인
- GA4 `관리 → 데이터 표시 → DebugView`에서 디버그 이벤트 확인
- 로컬 및 운영 환경에서 `collect` 요청 확인

### Looker Studio

초기에는 Sentry와 GA4를 분리해서 확인합니다. 운영 데이터가 충분히 쌓인 뒤
방문자 수, 검색 전환율, 로그인 전환율, Sentry 오류 현황을 통합해서 볼 필요가
있을 때 Looker Studio 대시보드를 추가합니다.

### MVP 적용 범위

현재 MVP에서는 다음을 적용합니다.

- 운영·스테이징 예외 자동 수집
- React Error Boundary 적용
- 로컬 환경 수집 비활성화
- Sentry 소스맵 업로드
- Git SHA 기준 release 기록
- Sentry 개인정보 제거
- GA4 페이지 조회 및 사용자 행동 이벤트 수집
- 배포 후 Sentry Release·소스맵 확인
- GA4 Realtime·DebugView 이벤트 확인

오류 테스트 시에는 테스트 메시지에 토큰이나 개인정보를 포함하지 않습니다.
