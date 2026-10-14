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
cd 2026-DAYNOMY/frontend
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

### 실제 계정과 API 연결

- `/league`와 공개 투자자 상세는 로그인 없이 조회합니다. 내 리그 프로필·종목 관리·공개 설정·팔로우·복기 화면은 로그인 후 이용합니다.
- 로컬 백엔드는 `http://localhost:8080`, 프론트는 `http://localhost:5173`을 사용합니다. `localhost`와 `127.0.0.1`을 섞지 않습니다.
- Google OAuth의 로컬 승인된 리디렉션 URI는 `http://localhost:8080/login/oauth2/code/google`입니다. Google 클라이언트 정보는 백엔드의 비공개 환경 설정에만 둡니다.
- 백엔드는 `FRONTEND_BASE_URL=http://localhost:5173`, HTTP 로컬 실행에서는 `COOKIE_SECURE=false`를 사용합니다. Dev·Prod의 HTTPS 쿠키 정책은 변경하지 않습니다.
- 프론트의 `VITE_API_BASE_URL`이 비어 있으면 Vite가 `/api`, `/oauth2`, `/login/oauth2`를 동일한 백엔드로 전달합니다. `http://localhost:8080`을 설정한 경우 직접 연결합니다.
- 로그인 화면의 `Google로 시작하기`에서 기존 Google OAuth를 실행합니다. 로그인 후에는 검증된 서비스 내부 경로로 돌아갑니다.
- 공유 자산·판단·복기·공개 설정·팔로우의 저장은 실제 로그인 계정과 서버 API를 사용합니다. 공개 투자 상세는 `/api/league/investors/{publicId}/details`에서 비회원도 조회할 수 있습니다.
- 리그 종류는 주간 수익률·안정형·초보리그 세 가지입니다. 순위·일별 조회·주간 그래프는 리그 시작 주(2026-10-12)부터 최대 최근 3주만 표시합니다. 첫 주에는 주차 선택 없이 이번 주만 보여주고, 다음 주부터는 이번 주·지난주를 드롭다운으로 선택하며 최대 3주까지 표시합니다. 서버의 기존 8주 상세 성과 지표와 과거 조회 API는 유지합니다.
- 리그 진입 버튼은 참여 상태(`leagueEnabled`)에 따라 미참여자는 `투자리그 참여하기`로 종목 선택·보유 이유·공개 설정 흐름에 연결하고, 참여자는 `종목 관리하기`로 연결합니다. 빈 순위에는 같은 버튼을 반복하지 않습니다.
- 리그는 순위 상세 지표를 옆 패널에서, 투자자 성과·공개 종목·투자 기록을 탭에서 표시합니다. 공유 종목마다 보유 이유만 필수이고 추가 판단 정보와 결과 복기는 선택입니다. 마이페이지에서 리그 프로필·팔로우한 투자자·리그 설정으로 이동할 수 있습니다.
- 공유 포트폴리오는 원본에서 선택해 불러오기·숨기기·해제와 리그 종목 제거를 제공합니다. 직접 추가·수량/매수가 편집 및 거래 입력 API는 제거했습니다. 숨김은 `PATCH /api/users/me/shared-portfolio/holdings/{assetId}/visibility`에 `{ "hidden": true }`만 전송하며, 원본·수량·매수가·기존 근거·수익률은 유지합니다. 리그 종목 제거는 `DELETE /api/users/me/shared-portfolio/holdings/{assetId}`를 사용하고 마지막 종목을 제거하면 참여를 종료하며 원본 포트폴리오와 기존 판단·복기는 보존합니다. 원본을 수정한 뒤 다시 가져올 때만 확인을 거쳐 값을 갱신합니다.
- 멤버십·결제·회원 등급·7일 지연 공개는 제거했습니다. 공개 동의가 있는 종목·판단·복기는 즉시 제공하며, 비공개 프로필·상세 비공개·숨긴 종목·실제 금액과 수량은 공개하지 않습니다. 종가가 없는 종목도 이름과 근거는 표시하고 비중·기여도는 집계 대기로 구분합니다. 이전 `/membership` 주소는 리그로 이동합니다.
- 신청 이후 첫 집계 종가를 0% 기준으로 이번 주 순위에 포함합니다. 장 마감 후 신청하면 다음 거래일이 첫 기준입니다. 일별 수익률은 해당 주 안에서 복리로 누적하고 다음 주에 새로 시작합니다. 금액 하한은 없으며 전체 누적·참여 후 별도 누적 수익률은 계산하지 않습니다. MDD는 최근 8주, 변동성은 최근 20거래일이며 부족한 값은 0%로 대체하지 않습니다.
- 거래일 달력·종가 수집·계산이 완료된 주간 확정값의 버전과 기준일을 표시합니다. 종목별 평가 수익률은 평균 매입가 대비 백분율이며, 주간 기여도는 복리 누적을 반영합니다. 과거 확정 결과도 현재 공개 동의를 확인합니다.
- 종가 제공처는 기준일 다음 영업일 오후 1시 이후 데이터를 갱신합니다. 자동 수집은 거래일 달력의 미수집 날짜를 다시 확인합니다. 리그의 최신 기준일은 실제 수집된 종가 기준으로 표시하며, 당일 데이터는 수집 대기로 안내합니다.
- 일별 빈 화면은 참여 전 날짜를 대기 사유에 섞지 않고 실제 수집 대기 날짜와 제공 시점을 안내합니다. 일별 대기는 다시 확인할 수 있고, 순위·일별 화면은 브라우저 탭으로 돌아오면 다시 조회합니다.
- 관심 투자자는 이번 주 순위를 바로 지난주의 확정 순위와 비교합니다. `previousRank`, `rankChange`, `comparisonWeekStart`를 사용하며 양수는 상승, 음수는 하락, 0은 동일입니다. 비교할 순위가 없으면 `비교 기록 없음`을 표시합니다. 추가 필드가 없는 이전 서버 응답도 기존 요약을 표시합니다.
- 로그인 변경과 리그 백엔드를 함께 반영해야 합니다. V31이 추적 기준일·참여 기간·거래일 수집 상태·확정 순위 이력을 추가하고, V32가 선택 판단 정보의 미입력을 허용합니다. 거래일 달력은 백엔드가 KRX에 발표된 전년도·올해·다음 해 일정을 자동 갱신합니다. 미수집 종가는 백엔드가 자동으로 순차 수집하며, 기간별 백필은 가격 정정이나 긴급 복구에 사용합니다.
- 상세 공개 필드는 `detailPublic`입니다. 기존 DB의 `premium_detail_enabled`와 이전 요청 필드의 동의 값은 보존하며, 기존 마이그레이션과 구독 테이블을 삭제하거나 공개 설정을 자동 변경하지 않습니다.
- 마이페이지의 `닉네임 변경`은 기존 `PATCH /api/users/me`와 CSRF 방어를 사용합니다. 앞뒤 공백을 제거한 1~20자를 저장하고, 중복·저장 실패 시 초안을 유지합니다. 성공한 응답은 런타임 검증 후 헤더에도 반영합니다.
- 이메일·Google 계정 이름은 본인에게만 제공됩니다. Google 이름은 별도 `google_name` 필드에 저장하며 기존 계정은 다음 Google 로그인 때 채웁니다. 이름은 닉네임에서 추측하지 않으며 Google 계정 이름을 실명 인증으로 간주하지 않습니다.
- 닉네임은 마이페이지와 리그에서 동일하게 사용하며, 두 설정 화면의 저장은 같은 서버 로직을 사용합니다. 공개 프로필 동의 없이 계정이 자동 공개되지는 않습니다. 중복은 기존 DB 정책대로 대소문자를 구분하고 탈퇴 계정의 닉네임도 예약 상태로 둡니다.
- 이미 공개 프로필이 있는 계정은 사용자가 정한 공개 닉네임을 계정 조회에도 사용합니다. 기존 별칭을 임의로 바꾸지 않으며 다음 닉네임 저장 시 회원 정보와 공개 프로필을 함께 갱신합니다.
- 신규 가입은 Google 이름·이메일 대신 임시 서비스 닉네임을 부여합니다.
- 초보 돈 관리의 메뉴와 화면 연결은 제거했습니다. `/finance/*`의 기존 주소는 홈으로 이동합니다. 저장 데이터·테이블과 해당 내부 모듈은 보존하되 배포 화면에 포함하지 않습니다.
- DB 마이그레이션은 기존 공식 이력을 보존합니다. 주요 인물은 V24, 피드백은 V25, 금융 학습·투자 리그·공유 포트폴리오·월 계획·비공개 이름은 `V26~V30`, 리그 성과·선택 판단은 `V31~V32`, 거래 원장은 V33, 구조화 시장 분석은 V34입니다. 이번 릴리스의 `V35~V42`는 분석 저장·근거, 콘텐츠 게시일, 인물 프로필과 게시일 정렬을 추가하며 세부 내용은 [백엔드 README](../backend/README.md#v111-프론트엔드와-함께-반영할-변경)를 확인합니다.
- 공식 V20·V21의 동시 검색 인덱스 생성에는 `.sql.conf`의 `executeInTransaction=false`와 `spring.flyway.postgresql.transactional-lock: false`를 함께 사용합니다.
- 새 백엔드 실행 전 Flyway 이력을 현재 `backend/src/main/resources/db/migration`과 대조합니다. 이전 로컬 DB에 같은 버전 번호의 다른 미공개 기능이 적용됐다면 SQL 내용이 같아도 버전·파일명 이력은 다를 수 있습니다. 기존 DB의 초기화나 `repair`로 우회하지 말고 데이터 보존 이전을 승인받습니다.

---

## 비회원 포트폴리오

- 보유 종목 정보는 `sessionStorage`의 `daynomy:guest-portfolio-holdings:v1`에 임시 보관합니다. 종목 정보와 매수·매도 기록을 저장하며, 평가금액·손익 계산 결과와 인증 정보는 저장하지 않습니다. 기존 임시 보유정보는 거래를 처음 기록할 때 시작 잔고로 보존합니다.
- 비회원도 종목 추가·정정·제거, 매수·매도 기록·수정·삭제와 거래 내역 조회를 사용할 수 있습니다. 거래일 순서로 보유 수량과 평균 매입가를 반영하며 초과 매도는 저장하지 않습니다. 현재 평가금액·손익·자산 구성은 백엔드 계산 응답으로 표시합니다. 자산 추이와 누적 실현손익은 로그인 안내를 유지합니다.
- 회원 자산 추이는 거래 변경 성공 후 다시 조회합니다. 서버에서 스냅샷을 재계산한 경우 새로고침 버튼이나 탭 복귀로 최신 결과를 조회하며, 평가금액·손익은 서버 응답을 사용합니다.
- 기존 비회원 AI 분석 1회 체험 정책은 유지합니다. 로그인하면 서버의 계정 포트폴리오를 조회하며, 임시 보유 종목의 자동 이전은 별도 작업입니다.

---

## 포트폴리오 AI 분석

- 회원은 `GET /api/users/me/portfolio/analysis`로 서버에 저장된 결과를 조회합니다. 단순 조회에는 AI 호출이 없으며, 이전 회원 공용 브라우저 캐시를 읽거나 새 계정으로 이전하지 않습니다.
- 최초 분석과 보유 자산 변경 후 분석은 `POST /api/users/me/portfolio/analysis`, 같은 포트폴리오의 새 근거 확인은 `POST /api/users/me/portfolio/analysis/refresh`를 사용합니다. 자산·비중·분석 기준이 같은 결과의 재사용과 실제 웹 검색을 구분합니다.
- 저장 상태는 `EMPTY`, `READY`, `PORTFOLIO_CHANGED`, `PROCESSING`, `RECHECK_REQUIRED`, `FAILED`입니다. 보유 자산이 바뀌면 이전 결과를 현재 자산의 분석으로 표시하지 않으며, 확인 실패 시 기존 결과와 실패 안내를 함께 유지합니다. 진행 중에는 저장 상태를 다시 조회하고 동시 요청의 409 응답도 재조회로 처리합니다.
- 전체 요약·종목별 영향 점수·출처·변경 이유와 최근 확인 결과를 표시합니다. 동일한 유효 근거이면 기존 분석 생성 시각을 유지하며, 근거 재확인이 어려운 상태와 검색 실패를 구분합니다.
- 서버는 최근 변경 이력 최대 10개를 `GET /api/users/me/portfolio/analysis/history`로 제공합니다. 현재 프론트는 저장된 최신 결과의 조회·갱신에 연결되어 있으며 분석 이력 탐색 화면은 제공하지 않습니다.
- 비회원은 `POST /api/portfolio/analysis`로 1회 체험하고 결과·체험 상태를 현재 탭의 `sessionStorage`에 보관합니다. 로그인·계정 전환 시 포트폴리오 화면을 다시 구성하여 다른 사용자의 보유 자산과 분석 결과가 섞이지 않도록 합니다.

## 소식·통합 검색·인물

- 소식 홈은 `/api/news`, `/api/people`, `/api/contents`를 읽어 기사·인물 글·저장된 외부 자료를 합칩니다. 오늘 날짜는 한국 시간으로 판단하며 전체 콘텐츠는 출처별 과거 기록을 클라이언트에서 합쳐 12개씩 표시합니다.
- 로컬 개발 서버에서는 화면 확인용 샘플 기사를 함께 표시합니다. 운영·개발 배포 빌드와 테스트에서는 샘플을 사용하지 않습니다.
- 홈·전체 콘텐츠의 종목 외부 자료는 `publishedDate`가 있으면 원문 게시일, 없으면 등록일을 기준으로 정렬·필터링합니다. 같은 날짜에는 등록 시각·ID로 정렬합니다. 기사에는 발행일, 인물 글에는 API 등록일을 사용합니다.
- `publishedDate`에는 관리자가 입력한 원문 게시일을 사용하며 입력을 생략한 신규 자료와 V42가 보완한 기존 자료는 한국 시간 등록일이 들어갑니다. 날짜 필드가 있다는 이유만으로 원문의 실제 게시일이 확인되었다고 판단하지 않습니다.
- 통합 검색은 `GET /api/search/contents?q=...&source=ALL&page=1&size=12`를 사용합니다. 출처는 `ALL`, `DAYNOMY`, `X`, `THREADS`, `YOUTUBE`이며 기사 제목·본문과 인물 글·외부 자료의 제목을 검색합니다. 검색어·출처·페이지를 URL과 동기화하고 로딩·빈 결과·오류·재시도를 구분합니다.
- 검색 결과는 서버가 페이지를 나누며 기사 발행 시각·외부 자료 등록 시각 기준으로 정렬합니다. 원문 게시일 정렬을 적용한 소식 피드와 검색의 정렬 기준은 다릅니다. 종목 상세와 보유 자산 소식도 기존 등록일 정렬을 유지합니다.
- 동일 URL의 종목 외부 자료를 합치고 인물 글과 겹치면 인물 글을 우선합니다. 기사 링크는 상세로 이동하고 X·Threads·YouTube 자료는 목록에서 펼쳐 원문과 임베드를 확인합니다.
- 인물은 등록된 프로필 이미지를 표시하고 이미지가 없으면 기본 아이콘을 사용합니다. 관리자는 Wikimedia Commons 이미지 선택과 X·Threads·YouTube 프로필 URL 등록, 세 플랫폼의 게시물 등록을 사용할 수 있습니다.

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

| 기능             | 검증 내용                                                                            |
| ---------------- | ------------------------------------------------------------------------------------ |
| 뉴스 목록 API    | 페이지 변환, 응답 매핑, 잘못된 응답 계약                                             |
| 뉴스 탐색 화면   | 목록·상세 표시, 카테고리 변경, 오류·비로그인 상태                                    |
| 통합 검색 API    | 기사·인물 글·외부 자료 계약, 검색어·출처·페이지 URL, 오류 응답                       |
| 통합 검색 화면   | 입력 검증, 결과·빈 화면·오류·재시도, URL·출처·페이지·브라우저 탐색 동기화            |
| 종목 목록 API    | 응답 매핑, 잘못된 응답 계약                                                          |
| 로그인 화면      | Google 로그인 시작, OAuth 실패 안내                                                  |
| 포트폴리오 화면  | 회원·비회원 거래 기록, 거래 수정·삭제, 보유 자산·추이·손익 갱신, API 오류 복구       |
| 포트폴리오 분석  | 저장 결과 조회·갱신·재사용, 자산·계정 변경, 진행 중·실패·근거 재확인 상태, 체험 제한 |
| 소식·인물 화면   | 출처 통합·중복 제거·게시일 정렬·오늘 필터·페이지 이동, 프로필 이미지와 임베드        |
| 리그 진입 화면   | 공개 순위·투자자 상세 조회와 본인 관리 화면의 로그인 보호                            |
| 사용자 행동 분석 | 콘텐츠·대시보드·분석 이벤트, 수집 제외 경로, GA4·PostHog URL 정리와 파라미터 제한    |
| 오류 모니터링    | Sentry 이벤트 개인정보 및 URL 쿼리 제거                                              |

통합 검색 화면은 사용자 입력, API 요청, URL 상태, 필터, 페이지네이션, 브라우저
탐색과 오류 복구가 함께 동작하는 대표 사용자 흐름이므로 컴포넌트 통합 테스트
대상으로 선정했습니다. 뉴스 탐색, 로그인, 소식과 포트폴리오는 각 기능의
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
npm test
npm run build
bash scripts/test-release-version.sh
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
| Variable | `VITE_POSTHOG_KEY`   | PostHog 공개 프로젝트 토큰                   |
| Variable | `VITE_POSTHOG_HOST`  | PostHog 이벤트 수집 호스트                   |
| Variable | `SENTRY_DSN`         | 환경별 별도 Sentry 프로젝트 DSN              |
| Variable | `SENTRY_ENVIRONMENT` | development는 `staging`, 운영은 `production` |
| Variable | `SENTRY_ORG`         | Sentry 조직 slug                             |
| Variable | `SENTRY_PROJECT`     | Sentry 프로젝트 slug                         |
| Secret   | `SENTRY_AUTH_TOKEN`  | 소스맵 업로드 토큰                           |

개발·운영은 각각 다른 GA4 Measurement ID와 Sentry 프로젝트 DSN을 사용합니다.
개발 Sentry 이벤트는 `staging`, 운영 이벤트는 `production`으로 구분합니다.
각 Environment의 `SENTRY_AUTH_TOKEN` Secret은 빌드 중 소스맵 업로드에 사용하며,
설정 변경 후에는 프론트를 다시 빌드·배포해야 합니다.
PostHog는 `VITE_POSTHOG_KEY`와 `VITE_POSTHOG_HOST`가 모두 있어야 활성화됩니다.
두 값은 빌드에 포함되는 공개 클라이언트 설정이며 개인 API 키를 사용하지 않습니다.

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

| 이벤트                                                                            | 발생 시점                                                | 주요 파라미터                                                                       |
| --------------------------------------------------------------------------------- | -------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `page_view`                                                                       | 공개 화면의 경로 변경                                    | `page_path` (쿼리 없는 경로)                                                        |
| `view_news_list`                                                                  | 뉴스 목록 진입·카테고리 변경                             | `category` (뉴스 카테고리 코드)                                                     |
| `view_news_detail`                                                                | 뉴스 상세 진입                                           | `news_id` (기사 식별 번호)                                                          |
| `search_news`                                                                     | 검색어 변경                                              | `search_length` (검색어 글자 수)                                                    |
| `click_search_open`                                                               | 검색 열기 버튼 클릭                                      | 없음                                                                                |
| `start_portfolio_analysis`                                                        | 포트폴리오 분석·갱신 요청 시작                           | `action`, `user_type`, `asset_count`                                                |
| `portfolio_analysis_result`                                                       | 사용자가 시작한 분석·갱신 요청 결과                      | `action`, `user_type`, `asset_count`, `status`, `last_check_outcome`, `duration_ms` |
| `view_portfolio_analysis`                                                         | 결과 상단 점수 영역이 50% 이상 노출                      | `surface`, `user_type`, `analyzed_asset_count`                                      |
| `select_portfolio_analysis_asset`                                                 | 차트·목록에서 전체 또는 자산별 결과로 선택 변경          | `surface`, `scope`, `position`                                                      |
| `expand_portfolio_analysis_sources`                                               | 분석 출처 펼치기                                         | `surface`, `scope`, `source_count`                                                  |
| `content_load_result`                                                             | 소식·인물·종목 콘텐츠·보유 자산 소식·뉴스 분석 조회 결과 | `surface`, `area`, `status`, `result_count`, `attempt`, `duration_ms`               |
| `view_content_list`, `view_content_empty`                                         | 콘텐츠 목록 또는 빈 상태 확인                            | `surface`, `source_type`, `result_count`, `page_number`, `status`, `reason`         |
| `click_content`, `click_content_original`, `expand_content`, `open_content_video` | 콘텐츠 선택·원문 이동·글 펼치기·종목 영상 열기           | `surface`, `source_type`, `content_id`, `position`                                  |
| `filter_content_source`, `change_content_page`                                    | 출처 필터·페이지 변경                                    | `surface`, `source_type`, `previous_source`, `previous_page`, `page_number`         |
| `click_content_more`, `retry_content_load`                                        | 더 보기 이동·조회 재시도                                 | `surface`, `destination`, `area`, `attempt`                                         |
| `click_person_preview`, `select_featured_person`                                  | 인물 미리보기·인물 선택                                  | `surface`, `person_id`, `position`                                                  |
| `view_portfolio_news`                                                             | 보유 자산 소식 제목이 50% 이상 노출                      | `surface=portfolio_news`, `status`, `result_count`                                  |
| `open_keyword_analysis`                                                           | 키워드 설명이 닫힘에서 열림으로 전환                     | `news_id`, `keyword_category`, `trigger`                                            |
| `view_market_analysis`                                                            | 정상 시장 분석의 제목이 50% 이상 노출                    | `surface=news_detail`, `news_id`                                                    |

포트폴리오 대시보드는 `/`에서 다음 이벤트를 수집합니다. 로그인 여부·시세 상태·입력
방식·정렬·조회 기간·실패 유형만 허용하며 종목 이름·금액·수량은 전송하지 않습니다.
정확한 파라미터 허용 목록은 `src/features/portfolio/analytics.ts`에서 관리합니다.

| 이벤트                                                                                                            | 발생 시점                               |
| ----------------------------------------------------------------------------------------------------------------- | --------------------------------------- |
| `portfolio_dashboard_viewed`, `portfolio_dashboard_loaded`, `portfolio_dashboard_load_failed`                     | 대시보드 진입·조회 완료·조회 실패       |
| `portfolio_retry_clicked`, `portfolio_amount_visibility_changed`, `portfolio_asset_section_toggled`               | 재시도·금액 표시·자산 영역 펼치기       |
| `portfolio_period_changed`, `portfolio_chart_expanded`, `portfolio_filter_changed`, `portfolio_sort_changed`      | 추이 기간·확대·보유 종목 필터·정렬 변경 |
| `portfolio_holding_detail_opened`, `portfolio_tab_changed`, `portfolio_editor_opened`, `portfolio_stock_selected` | 종목 상세·탭·입력창·시장 선택           |
| `portfolio_save_submitted`, `portfolio_save_succeeded`, `portfolio_save_failed`                                   | 보유 정정·거래 등록·수정 제출과 결과    |
| `portfolio_delete_succeeded`, `portfolio_delete_failed`                                                           | 보유 종목·거래 삭제 결과                |
| `portfolio_history_filter_changed`, `portfolio_history_range_changed`                                             | 거래 유형·조회 기간 필터 변경           |

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

### PostHog 사용자 행동 분석

- `VITE_POSTHOG_KEY`·`VITE_POSTHOG_HOST`가 모두 설정되면 GA4와 같은 명시적 사용자 행동 이벤트를 전송하며 페이지 조회는 `$pageview` 이름을 사용합니다. GA4 설정 없이 PostHog만 사용할 수도 있습니다.
- 자동 클릭·페이지 조회·페이지 이탈·성능·오류 수집, 세션 녹화·설문·기능 플래그 자동 조회와 개인 프로필 생성을 끕니다. 이벤트 속성은 코드의 허용 목록으로 제한하며 검색어 원문·보유 종목·금액·수량은 전송하지 않습니다.
- 익명 식별은 브라우저 `localStorage`를 사용합니다. URL·referrer에서 쿼리·fragment를 제거하고 투자자 공개 ID 경로를 일반 경로로 치환합니다.
- `/login`·`/admin`에서는 수집하지 않으며 해당 경로에 진입한 뒤에는 같은 앱 세션의 수집을 중지합니다. 분석 SDK 로딩·전송 실패는 사용자 작업을 중단하지 않습니다.
- 로컬 `.env`와 배포 Environment에서 두 값을 비워 두면 PostHog를 초기화하지 않습니다. 변경 시 다시 빌드하고 해당 환경의 이벤트 수신 여부를 확인합니다.

### Looker Studio

현재는 Sentry와 GA4, 설정된 경우 PostHog를 각각 확인합니다. 운영 데이터가 충분히 쌓인 뒤
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
- PostHog 설정 시 동일한 공개 화면의 명시적 페이지 조회·사용자 행동 이벤트 수집
- 배포 후 Sentry Release·소스맵 확인
- GA4 Realtime·DebugView 이벤트 확인
- PostHog 설정 환경에서 이벤트 이름·파라미터·수집 제외 경로 확인

오류 테스트 시에는 테스트 메시지에 토큰이나 개인정보를 포함하지 않습니다.

### 포트폴리오 거래 기록

- `feature/BE/#335`의 거래 등록·수정·삭제 API와 거래 식별자, 시세 상태 응답을 사용합니다.
- `자산 추가`는 회원·비회원 모두 매수·매도 거래 기록으로 연결합니다. 거래일·수량·단가를 입력하며 별도의 초기 보유 등록 경로는 제공하지 않습니다. `보유정보 정정`과 `보유자산 제거`는 거래 내역에 표시하지 않습니다.
- `평가손익`은 현재 보유자산 기준, `누적 실현손익`은 매도로 확정된 손익입니다. 전량 매도 후에도 거래 내역과 과거 추이를 조회합니다.
- 거래 목록의 매수·매도 행을 누르면 편집 창이 바로 열립니다. 편집 창에서 수량·단가·거래일을 수정하고, 거래 삭제도 선택할 수 있습니다. 삭제 확인을 취소하면 편집 중이던 값이 유지됩니다.
- 거래 수정은 거래일·수량·단가·수수료·세금만 지원합니다. 과거 거래의 수정·삭제는 이후 매도 수량 검증에 실패할 수 있으며, 서버 안내를 표시하고 화면을 유지합니다.
- 거래 등록·수정·삭제 성공 시 보유자산·실현손익·계산 결과·내역·추이를 갱신합니다. 수량이 같고 거래일만 바뀌어도 추이를 다시 조회합니다.
- 대시보드의 거래 내역은 모든 종목을 섞어 거래일 최신순으로 표시합니다. 같은 거래일에는 기록 시각, 기록 ID 최신순을 적용하며 개별 종목 상세에서는 해당 종목만 표시합니다.
- 대시보드와 종목 상세의 거래 목록 및 필터는 실제 매수·매도 기록(`BUY`, `SELL`)만 표시합니다. 거래 기록이 없는 보유분의 거래일·수량·단가를 임의로 만들지 않으며, 기존 보유 및 정정 기록은 서버 계산용으로 유지합니다.
- 포트폴리오와 리그는 소식의 이슈 화면과 같은 본문 너비·좌우 여백·상단 간격·제목 크기를 사용합니다. 포트폴리오 제목은 축소하지 않으며, 1024px 이상에서 카드·표 본문은 1324.8px 기준 배치를 좁은 노트북에 맞춰 같은 비율로 축소합니다. 기준보다 넓으면 공통 본문 너비만큼 펼칩니다. 1024px 미만에서는 기존 반응형 배치를 사용하며, 팝업은 본문 축소와 별도로 화면 크기에 맞춥니다.
- 기본 자산 추이 그래프는 기간·로딩·오류·데이터 개수에 관계없이 같은 높이를 유지하며 평가 요약은 상세 팝업에서 표시합니다.
- 대시보드 거래 목록은 날짜·유형·종목·수량/단가·총액을 넓은 행 안에 표시하며 별도의 세부 박스를 펼치지 않습니다.
- 자산 추이 제목 또는 확대 버튼으로 상세를 열고 평가금액·평가손익·평가수익률을 전환합니다. 차트의 가로 간격은 실제 날짜 간격을 반영하며 손익·수익률은 0 기준선을 표시합니다.
- 기본·상세 차트 모두 마우스나 좌우 방향키로 평가 데이터가 있는 날짜별 툴팁을 확인합니다. 상세에서 조회 날짜를 고른 뒤에도 툴팁은 마우스 위치를 따라가며, 하단 조회 날짜와 값은 선택한 날짜를 유지합니다.
- 자산 추이 상세 팝업의 높이와 스크롤바 공간은 날짜 선택에 관계없이 유지하며 그래프 위치나 폭이 바뀌지 않습니다.
- 자산 추이는 날짜별 자산 평가값만 표시하며 매수·매도 내역·거래 표시점·거래 범례를 제공하지 않습니다. 추이 조회에서 거래 내역 API를 요청하지 않습니다. 가격 데이터가 없는 날짜의 평가값은 임의로 만들지 않으며, 하루 데이터만 있을 때는 평가점과 안내를 표시합니다.
- 추이의 손익·수익률은 해당 날짜에 보유한 자산의 평가 기준입니다. 배당·현금·매도 실현손익을 합산한 기간 투자수익률과 구분합니다.
- 시세 누락(`PARTIAL`)은 부분 평가금액과 계산 불가 손익을 구분합니다. 기준일 혼재(`MIXED_DATES`)는 일간 손익을 표시하지 않습니다. 추이 응답의 `currentPoint: null`을 허용합니다.
- 거래 화면 브라우저 검증: `npm run build && npx playwright test e2e/portfolio-ledger.e2e.ts`. 계약에 맞춘 API 모의 응답으로 데스크톱·모바일 흐름을 검사하며 실제 백엔드 연결 검증과는 구분합니다.
