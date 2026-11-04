# DAYNOMY Frontend

사용자가 보유 자산과 거래를 등록하고, **포트폴리오 AI 분석 결과와 근거를 확인**할 수 있는 React 애플리케이션입니다.

현재 자산 구성, 종목별 영향, 이전 분석 이후의 변화와 분석 출처를 하나의 흐름으로 제공합니다. 경제 콘텐츠 탐색, 투자 기록과 복기, 투자 리그 화면도 함께 제공합니다.

> 전체 프로젝트 소개는 [루트 README](../README.md)를 확인해 주세요.

## 주요 사용자 경험

### 포트폴리오 AI 분석

- 저장된 최신 포트폴리오 분석 조회
- 최초 분석과 자산 변경 후 새로운 분석 요청
- 전체 포트폴리오 요약과 종목별 영향 확인
- 분석 근거와 출처 확인
- 동일한 포트폴리오의 최신 근거 재확인
- 분석 준비·처리·실패·재확인 필요 상태 구분
- 이전 분석 이후 달라진 내용 확인
- 비회원 1회 분석 체험

### 자산과 투자 기록

- 국내·미국 주식 및 ETF 검색
- 매수·매도 거래 기록과 일괄 편집
- CSV 보유 현황 가져오기
- 평가금액, 손익과 자산 구성 확인
- 기간별 자산 추이 확인
- 투자 당시의 보유 이유와 결과 복기

### 경제 콘텐츠와 투자 리그

- 뉴스, 인물 게시물과 외부 자료 탐색
- 보유 종목과 관련된 소식 확인
- 출처·정렬·페이지 상태를 지원하는 통합 검색
- 주간 투자 리그와 공개 투자자 프로필
- 일별·주간 성과와 집계 상태 확인
- 관심 투자자 팔로우

## 주요 화면

| 경로                | 기능                                   | 접근   |
| ------------------- | -------------------------------------- | ------ |
| `/`                 | 보유 자산·거래 관리 및 포트폴리오 분석 | 공개   |
| `/welcome`          | 서비스 소개                            | 공개   |
| `/news`             | 경제 소식과 보유 종목 관련 콘텐츠      | 공개   |
| `/news/:newsId`     | 뉴스 상세와 관련 분석                  | 공개   |
| `/search`           | 기사·인물·외부 콘텐츠 통합 검색        | 공개   |
| `/stocks/:assetId`  | 종목 상세와 관련 콘텐츠                | 공개   |
| `/people`           | 주요 경제 인물                         | 공개   |
| `/league`           | 투자 리그 순위                         | 공개   |
| `/league/:publicId` | 공개 투자자 프로필                     | 공개   |
| `/league/profile`   | 내 리그 프로필                         | 로그인 |
| `/league/portfolio` | 리그 종목과 투자 기록 관리             | 로그인 |
| `/league/reviews`   | 투자 복기                              | 로그인 |
| `/mypage`           | 계정과 공개 설정                       | 로그인 |
| `/admin/*`          | 콘텐츠·자산·피드백 관리                | 관리자 |

## 기술 스택

| 분류             | 기술                           |
| ---------------- | ------------------------------ |
| UI               | React 19                       |
| Language         | TypeScript 6                   |
| Build            | Vite 8                         |
| Routing          | React Router 7                 |
| HTTP             | Axios                          |
| Unit Test        | Node Test Runner               |
| Component Test   | Vitest, Testing Library, jsdom |
| E2E Test         | Playwright                     |
| Quality          | Oxlint, Prettier               |
| Analytics        | Google Analytics 4, PostHog    |
| Error Monitoring | Sentry                         |

## 디렉터리 구조

```text
frontend/
├── src/
│   ├── api/                    # API 클라이언트와 공통 계약
│   ├── features/
│   │   ├── portfolio/          # 자산·거래·포트폴리오 분석
│   │   ├── league/             # 투자 리그와 공개 프로필
│   │   ├── news/               # 소식 목록과 상세
│   │   ├── search/             # 통합 검색
│   │   ├── people/             # 주요 인물
│   │   ├── stock-content/      # 종목별 콘텐츠
│   │   ├── account/            # 계정과 인증 상태
│   │   ├── admin/              # 관리자 화면
│   │   └── landing/            # 서비스 소개
│   ├── analytics.ts            # 사용자 행동 분석
│   └── App.tsx                 # 라우팅과 앱 진입점
├── server/                     # 공개 HTML 생성 서버
├── tests/                      # 단위·계약·컴포넌트 테스트
├── e2e/                        # Playwright E2E
├── public/                     # 정적 파일
└── scripts/                    # 빌드·릴리스 검증 스크립트
```

페이지, 기능 모듈과 공통 컴포넌트의 책임을 구분합니다. 복잡한 화면 로직은 기능 모듈 또는 Hook으로 분리하며, 서버 상태와 브라우저 상태를 구분해 관리합니다.

## 포트폴리오 분석 화면

### 분석 상태 처리

| 상태                | 화면 동작                               |
| ------------------- | --------------------------------------- |
| `EMPTY`             | 최초 분석 요청 안내                     |
| `READY`             | 저장된 최신 분석과 근거 표시            |
| `PORTFOLIO_CHANGED` | 자산 변경 안내와 새로운 분석 요청 제공  |
| `PROCESSING`        | 진행 상태를 표시하고 저장 상태 재조회   |
| `RECHECK_REQUIRED`  | 기존 결과를 유지하며 근거 재확인 안내   |
| `FAILED`            | 기존 결과가 있으면 유지하고 재시도 제공 |

회원 분석 결과는 서버에 저장합니다. 단순 조회에는 AI 호출이 발생하지 않으며, 새로운 분석과 최신 근거 재확인은 별도 사용자 동작으로 구분합니다.

비회원의 보유 자산과 거래 기록은 현재 탭의 `sessionStorage`에 임시 저장합니다. 분석은 한 번 체험할 수 있으며 로그인 계정의 포트폴리오로 자동 이전하지 않습니다.

### 주요 사용자 흐름

```text
종목 검색
   ↓
매수·매도 기록 입력
   ↓
자산 구성과 손익 확인
   ↓
포트폴리오 분석 요청
   ↓
전체 요약·종목별 영향·근거 확인
   ↓
자산 변경 또는 최신 근거 재확인
```

## 로컬 실행

### 요구사항

- Node.js 20.19 이상 또는 22.12 이상
- npm
- Git

### 설치

```bash
npm install
```

### 개발 서버

```bash
npm run dev
```

개발 서버는 `http://localhost:5173`에서 실행됩니다.

## 백엔드 연결

로컬 백엔드는 기본적으로 `http://localhost:8080`을 사용합니다.

`VITE_API_BASE_URL`이 비어 있으면 Vite 개발 서버가 다음 요청을 로컬 백엔드로 전달합니다.

- `/api`
- `/oauth2`
- `/login/oauth2`

별도 API 주소에 직접 연결하려면 환경변수를 설정합니다.

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

Google OAuth의 로컬 리디렉션 URI는 다음과 같습니다.

```text
http://localhost:8080/login/oauth2/code/google
```

프론트엔드와 백엔드 주소에서 `localhost`와 `127.0.0.1`을 혼용하면 쿠키 인증이 정상적으로 동작하지 않을 수 있습니다.

## 주요 명령

| 명령                        | 설명                       |
| --------------------------- | -------------------------- |
| `npm run dev`               | 개발 서버 실행             |
| `npm run build`             | 프로덕션 빌드              |
| `npm run build:development` | 개발 환경 빌드             |
| `npm run build:production`  | 운영 환경 빌드             |
| `npm run format`            | Prettier 포맷 적용         |
| `npm run format:check`      | 포맷 검사                  |
| `npm run lint`              | Oxlint 검사                |
| `npm run typecheck`         | TypeScript 타입 검사       |
| `npm run test:unit`         | 단위·API 계약 테스트       |
| `npm run test:server`       | 공개 HTML 서버 테스트      |
| `npm run test:ui`           | 컴포넌트 통합 테스트       |
| `npm test`                  | E2E를 제외한 전체 테스트   |
| `npm run test:e2e`          | Playwright 브라우저 테스트 |

## 테스트 전략

테스트는 구현 계층보다 사용자에게 발생할 수 있는 실패 위험을 기준으로 작성합니다.

| 종류          | 대상                                    | 도구                    |
| ------------- | --------------------------------------- | ----------------------- |
| 단위·API 계약 | 순수 로직, 요청·응답 변환과 데이터 검증 | Node Test Runner        |
| 서버 테스트   | 공개 HTML, 메타데이터, robots와 sitemap | Node Test Runner        |
| 컴포넌트 통합 | 입력, 화면 상태, URL과 주요 상호작용    | Vitest, Testing Library |
| 브라우저 E2E  | 로그인·권한, 검색과 연결된 사용자 흐름  | Playwright              |

다음 상태를 주요 검증 대상으로 삼습니다.

- 로딩, 빈 화면, 오류와 재시도
- 분석 준비, 처리 중, 자산 변경과 실패
- 사용자 입력과 거래 변경
- API 응답 계약 불일치
- 검색어·필터·페이지와 브라우저 URL 동기화
- 로그인과 관리자 권한 경계
- 반응형 화면과 주요 접근성 흐름

### 전체 검증

```bash
npm run format:check
npm run lint
npm run typecheck
npm test
npm run build
```

### E2E

최초 한 번 Chromium을 설치합니다.

```bash
npx playwright install chromium
```

프로덕션 빌드 후 E2E를 실행합니다.

```bash
npm run build
npm run test:e2e
```

## 공개 HTML과 검색 노출

프론트 컨테이너의 Node 서버는 공개 URL의 첫 HTML을 생성하고, 이후 화면 조작은 React 애플리케이션이 담당합니다.

- `/welcome`, `/news`, 발행된 뉴스와 종목 상세는 검색 노출 대상입니다.
- 공개 투자자 프로필은 공유 미리보기를 제공합니다.
- 로그인 후 화면과 관리자 화면은 검색 대상에서 제외합니다.
- 공개 페이지의 제목, 설명, canonical과 공유 메타데이터를 서버에서 생성합니다.
- robots와 sitemap은 공개 정책에 맞춰 제공합니다.

상세한 결정 배경은 [공개 HTML ADR](./docs/adr/261027-페이지별-렌더링과-공개-HTML.md)을 확인해 주세요.

## 사용자 행동 분석과 개인정보

Google Analytics 4와 PostHog가 설정된 환경에서는 기능 개선을 위해 주요 사용자 흐름을 측정합니다.

- 페이지와 콘텐츠 탐색
- 포트폴리오 등록과 분석 요청 결과
- 검색 필터와 페이지 이동
- 가이드 이용과 피드백 제출

검색어 원문, 포트폴리오 금액, 인증 정보와 개인정보를 분석 이벤트에 포함하지 않습니다. Sentry 전송 데이터에서도 쿠키, 요청 본문과 URL 쿼리 등 민감할 수 있는 정보를 제거합니다.

## 배포

| 브랜치 | 환경        | URL                       |
| ------ | ----------- | ------------------------- |
| `dev`  | Development | `https://dev.daynomy.com` |
| `main` | Production  | `https://daynomy.com`     |

GitHub Actions가 브랜치에 맞는 Vite mode로 빌드하고 배포합니다. 프론트 컨테이너를 교체한 뒤 상태를 확인하고 Nginx 연결을 전환합니다.

버전, Git tag와 롤백을 포함한 상세 절차는 [RELEASING.md](./RELEASING.md)를 확인해 주세요. 버전별 변경 사항은 [CHANGELOG.md](./CHANGELOG.md)에서 관리합니다.

## 관련 문서

- [릴리스 절차](./RELEASING.md)
- [변경 이력](./CHANGELOG.md)
- [공개 HTML ADR](./docs/adr/261027-페이지별-렌더링과-공개-HTML.md)
- [백엔드 개발 문서](../backend/README.md)
- [팀 개발 규칙](../AGENTS.md)

## 개발 원칙

- 페이지, 기능 모듈과 공통 컴포넌트의 책임을 구분합니다.
- UI와 비즈니스 로직을 분리합니다.
- 서버 상태와 클라이언트 상태를 구분합니다.
- 기존 컴포넌트와 유틸리티를 먼저 확인합니다.
- 한 번만 사용하는 코드를 과도하게 추상화하지 않습니다.
- API 타입은 실제 명세와 백엔드 DTO를 기준으로 정의합니다.
- 서버 응답을 런타임에서 검증하고 화면에 필요한 형태로 변환합니다.
- 로딩, 빈 화면, 오류, 반응형과 접근성을 함께 처리합니다.
- 주요 사용자 흐름과 복잡한 상태 변화를 테스트합니다.
