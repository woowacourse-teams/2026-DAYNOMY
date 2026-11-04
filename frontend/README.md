# DAYNOMY Frontend

사용자가 보유 자산과 거래를 등록하고, **포트폴리오 AI 분석 결과와 근거를 확인**할 수 있는 React 애플리케이션입니다.

현재 자산 구성, 종목별 영향, 이전 분석 이후의 변화와 분석 출처를 하나의 흐름으로 제공합니다. 경제 콘텐츠 탐색, 투자 기록과 복기, 투자 리그 화면도 함께 제공합니다.

> 전체 프로젝트 소개는 [루트 README](../README.md)를 확인해 주세요.

## 주요 사용자 경험

| 영역            | 주요 경험                                                |
| --------------- | -------------------------------------------------------- |
| 포트폴리오 분석 | 전체·종목별 영향, 근거와 변경 내용 확인, 비회원 체험     |
| 자산과 거래     | 국내·미국 주식·ETF, 거래 기록, CSV 일괄 편집과 자산 추이 |
| 경제 콘텐츠     | 보유 종목 소식, 인물·외부 자료와 통합 검색               |
| 투자 리그       | 주간 성과, 공개 투자자, 복기 알림과 기사 기반 AI 질문    |
| 이메일 알림     | 중요 종목 자동 분석과 구독 설정                          |

## 주요 화면

| 경로                | 기능                                   | 접근   |
| ------------------- | -------------------------------------- | ------ |
| `/`                 | 보유 자산·거래 관리 및 포트폴리오 분석 | 공개   |
| `/news`, `/search`  | 경제 콘텐츠와 통합 검색                | 공개   |
| `/stocks/:assetId`  | 종목 상세와 관련 콘텐츠                | 공개   |
| `/league`           | 투자 리그 순위                         | 공개   |
| `/league/:publicId` | 공개 투자자 프로필                     | 공개   |
| `/league/*`         | 내 리그·종목·투자 복기 관리            | 로그인 |
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

## 포트폴리오 분석 화면

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

## 로컬 실행

Node.js 20.19 이상 또는 22.12 이상이 필요합니다.

```bash
npm install
npm run dev
```

개발 서버는 `http://localhost:5173`에서 실행됩니다.

## 백엔드 연결

로컬 백엔드는 기본적으로 `http://localhost:8080`을 사용합니다.

`VITE_API_BASE_URL`이 비어 있으면 Vite가 `/api`, `/oauth2`, `/login/oauth2`를 로컬 백엔드로 전달합니다. 직접 연결하려면 다음 값을 설정합니다.

```dotenv
VITE_API_BASE_URL=http://localhost:8080
```

Google OAuth의 로컬 리디렉션 URI는 `http://localhost:8080/login/oauth2/code/google`입니다. 쿠키 인증을 위해 `localhost`와 `127.0.0.1`을 혼용하지 않습니다.

## 주요 명령

| 명령                   | 설명                       |
| ---------------------- | -------------------------- |
| `npm run dev`          | 개발 서버 실행             |
| `npm run build`        | 프로덕션 빌드              |
| `npm run format:check` | 포맷 검사                  |
| `npm run lint`         | Oxlint 검사                |
| `npm run typecheck`    | TypeScript 타입 검사       |
| `npm test`             | E2E를 제외한 전체 테스트   |
| `npm run test:e2e`     | Playwright 브라우저 테스트 |

## 테스트 전략

단위·API 계약, 컴포넌트 통합과 브라우저 E2E 테스트로 주요 사용자 흐름과 실패 상태를 검증합니다.

```bash
npm run format:check
npm run lint
npm run typecheck
npm test
npm run build
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

Google Analytics 4와 PostHog로 주요 사용자 흐름을 측정합니다. 검색어 원문, 포트폴리오 금액과 인증 정보는 이벤트에 포함하지 않으며 Sentry에서도 민감정보를 제거합니다.

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
- [CI/CD 성능 기록](./docs/ci-cd-performance.md)
- [포트폴리오 분석 이벤트 계약](./docs/portfolio-analytics.md)
- [투자 리그 AI 복기 기술 명세](../docs/league-ai-review-technical.md)
- [백엔드 개발 문서](../backend/README.md)
- [팀 개발 규칙](../AGENTS.md)
