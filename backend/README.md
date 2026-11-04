# DAYNOMY Backend

DAYNOMY의 **포트폴리오 기록과 AI 분석**을 처리하는 Spring Boot API 서버입니다.

사용자의 보유 자산과 거래 기록을 계산하고, 포트폴리오 분석의 생성·저장·재확인·변경 이력을 관리합니다. 인증, 경제 콘텐츠, 투자 리그와 관리자 기능도 함께 제공합니다.

> 전체 프로젝트 소개는 [루트 README](../README.md)를 확인해 주세요.

## 담당 영역

### 포트폴리오 AI 분석

- 회원 포트폴리오 분석 생성 및 저장
- 전체 요약과 종목별 영향 분석
- 분석 근거와 출처 관리
- 보유 자산 변경 여부 감지
- 동일 포트폴리오 분석 결과 재사용
- 최신 시장 근거 재확인
- 종목별 분석 재사용과 동시 중복 분석 방지
- 최근 분석 변경 이력 제공
- 비회원 1회 분석 체험

### 포트폴리오와 거래 기록

- 국내·미국 주식 및 ETF 관리
- 매수·매도 거래 원장
- 수량과 평균 매입가 계산
- 국내·미국 종가와 환율 연동
- 평가금액, 손익과 일별 자산 추이 계산
- 투자 판단과 복기 관리
- CSV 보유 현황 가져오기와 일괄 편집

### 콘텐츠와 투자 리그

- 경제 뉴스, 인물 게시물과 외부 콘텐츠 관리
- 콘텐츠 통합 검색과 종목 연결
- 자동 수집 콘텐츠의 관리자 검수
- 주간 투자 리그 집계와 순위 확정
- 공유 포트폴리오와 공개 투자 기록
- 거래일 달력, 종가 수집과 집계 상태 관리

### 인증과 운영

- Google OIDC 로그인
- JWT 쿠키 인증과 권한 관리
- 관리자 API 접근 제어
- Actuator, Prometheus와 OpenTelemetry 연동
- 구조화 로그와 요청 추적

## 기술 스택

| 분류              | 기술                                              |
| ----------------- | ------------------------------------------------- |
| Language          | Java 21                                           |
| Framework         | Spring Boot 4.1                                   |
| Database          | PostgreSQL 17                                     |
| ORM               | Spring Data JPA                                   |
| Migration         | Flyway                                            |
| Security          | Spring Security, Google OIDC, JWT                 |
| API Documentation | Springdoc OpenAPI                                 |
| Test              | JUnit, RestAssured, Testcontainers, MockWebServer |
| Storage           | AWS S3                                            |
| Observability     | Actuator, Prometheus, OpenTelemetry               |
| Build             | Gradle 9.5.1                                      |

## 애플리케이션 구조

기본 흐름은 `Controller → Service → Domain → Repository`입니다.

```text
src/main/java/org/grit/daynomy/
├── auth/                 # 로그인, 토큰과 인증·인가
├── member/               # 회원과 프로필
├── portfolio/            # 포트폴리오, 거래와 AI 분석
├── asset/                # 자산 정보
├── market/               # 시장 정보와 종목 분석
├── exchangerate/         # 환율
├── league/               # 투자 리그와 순위 집계
├── news/                 # 경제 뉴스
├── content/              # 외부 콘텐츠와 사용자 반응
├── person/               # 주요 인물과 게시물
├── search/               # 통합 검색
├── feedback/             # 사용자 피드백
├── external/             # 외부 API 연동
├── config/               # 애플리케이션 설정
└── common/               # 공통 응답, 예외와 로깅
```

계층별 책임은 다음과 같습니다.

- `Controller`: 요청 검증, HTTP 처리와 응답 변환
- `Service`: 트랜잭션과 객체 간 흐름 조율
- `Domain`: 핵심 비즈니스 규칙과 상태 변경
- `Repository`: 데이터 접근

요청 DTO, 응답 DTO와 Domain을 분리하며 Domain 객체를 API 응답으로 직접 노출하지 않습니다.

## 포트폴리오 분석

### 분석 상태

| 상태                | 의미                                 |
| ------------------- | ------------------------------------ |
| `EMPTY`             | 저장된 분석이 없음                   |
| `READY`             | 현재 포트폴리오에 유효한 분석이 있음 |
| `PORTFOLIO_CHANGED` | 분석 이후 보유 자산이 변경됨         |
| `PROCESSING`        | 분석 생성 또는 근거 확인이 진행 중   |
| `RECHECK_REQUIRED`  | 기존 분석의 근거를 다시 확인해야 함  |
| `FAILED`            | 분석 생성 또는 확인에 실패함         |

분석 조회는 저장된 결과를 반환하며 AI를 새로 호출하지 않습니다. 새로운 분석 생성과 기존 분석의 최신 근거 재확인은 별도 요청으로 구분합니다.

### 주요 API

| Method | Endpoint                                   | 설명                        |
| ------ | ------------------------------------------ | --------------------------- |
| `GET`  | `/api/users/me/portfolio/analysis`         | 저장된 최신 분석 조회       |
| `POST` | `/api/users/me/portfolio/analysis`         | 현재 포트폴리오 분석 생성   |
| `POST` | `/api/users/me/portfolio/analysis/refresh` | 기존 분석의 근거 재확인     |
| `GET`  | `/api/users/me/portfolio/analysis/history` | 최근 분석 변경 이력 조회    |
| `POST` | `/api/portfolio/analysis`                  | 비회원 포트폴리오 분석 체험 |

전체 API 계약은 애플리케이션 실행 후 Swagger UI에서 확인할 수 있습니다.

## 로컬 실행

### 요구사항

- JDK 21
- PostgreSQL 17
- Git

Gradle은 저장소의 Wrapper를 사용하므로 별도 설치가 필요하지 않습니다.

### 데이터베이스 생성

```sql
CREATE DATABASE daynomy;
```

### 환경 설정

`backend/.env` 파일을 생성합니다. 이 파일은 저장소에 커밋하지 않습니다.

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/daynomy
DB_USERNAME=<database-username>
DB_PASSWORD=<database-password>

GOOGLE_CLIENT_ID=<google-client-id>
GOOGLE_CLIENT_SECRET=<google-client-secret>
JWT_SECRET=<jwt-secret>

AWS_S3_PUBLIC_BASE_URL=<public-image-base-url>
PUBLIC_DATA_SERVICE_KEY=<public-data-service-key>

FRONTEND_BASE_URL=http://localhost:5173
COOKIE_SECURE=false
```

외부 기능을 사용할 때는 다음 환경변수를 추가합니다.

| 기능                   | 주요 환경변수                                |
| ---------------------- | -------------------------------------------- |
| AI 분석 및 콘텐츠 생성 | `OPENAI_API_KEY`                             |
| 이미지 업로드          | `AWS_REGION`, `AWS_S3_BUCKET`                |
| YouTube 콘텐츠         | `YOUTUBE_API_KEY`                            |
| 미국 주식 종가         | `EODHD_API_TOKEN`                            |
| 콘텐츠 반응 익명화     | `CONTENT_INTERACTION_ANONYMOUS_TOKEN_SECRET` |

설정 이름과 기본값은 `src/main/resources/application.yaml`을 기준으로 합니다.

### 실행

```bash
./gradlew bootRun
```

| 용도               | 주소                                        |
| ------------------ | ------------------------------------------- |
| API 서버           | `http://localhost:8080`                     |
| Swagger UI         | `http://localhost:8080/swagger-ui.html`     |
| OpenAPI JSON       | `http://localhost:8080/v3/api-docs`         |
| Health Check       | `http://localhost:8081/actuator/health`     |
| Prometheus Metrics | `http://localhost:8081/actuator/prometheus` |

Google OAuth의 로컬 리디렉션 URI는 다음과 같습니다.

```text
http://localhost:8080/login/oauth2/code/google
```

로컬에서는 프론트엔드와 백엔드 모두 `localhost`를 사용하고 `127.0.0.1`과 혼용하지 않습니다.

## 데이터베이스 마이그레이션

스키마는 Flyway로 관리하며 JPA는 `ddl-auto: validate`로 검증합니다.

- 이미 배포된 마이그레이션 파일을 수정하지 않습니다.
- 새로운 스키마 변경은 다음 버전의 마이그레이션으로 추가합니다.
- 실행 전 DB의 Flyway 이력과 저장소의 마이그레이션을 대조합니다.
- 기존 데이터와 이력을 초기화하거나 `repair`로 불일치를 우회하지 않습니다.
- 데이터 보존이 필요한 이력 변경은 별도 이전 절차를 마련합니다.

마이그레이션 파일은 다음 경로에 있습니다.

```text
src/main/resources/db/migration/
```

## 테스트와 검증

```bash
# 전체 테스트
./gradlew test

# 코드 포맷과 전체 테스트
./gradlew spotlessCheck test

# PR 수준 전체 검증
./gradlew clean build
```

테스트는 다음 위험을 우선 검증합니다.

- 핵심 비즈니스 규칙과 상태 변화
- API 요청·응답 계약
- 인증과 권한 경계
- 거래 원장과 수익률 계산의 데이터 정합성
- 분석 중복 실행과 실패 복구
- 외부 API 오류와 잘못된 응답
- 반복 가능성이 있는 회귀 버그

DB 동작이 중요한 Repository 테스트에는 PostgreSQL Testcontainers를 사용합니다. 외부 서비스 연동은 테스트에서 통제 가능한 응답으로 대체합니다.

## 관측 가능성

- Actuator 헬스 체크와 프로브
- Prometheus 애플리케이션 메트릭
- OpenTelemetry 트레이싱
- 요청 식별자를 포함한 구조화 로그
- 외부 API 호출과 주요 배치 작업 관찰
- 민감정보를 제외한 실패 원인 기록

개발·운영 모니터링 구성은 [모니터링 문서](../monitoring/docs)를 확인해 주세요.

## 관련 문서

- [백엔드 ADR](./docs/adr)
- [부하 테스트](./loadtest/README.md)
- [공통 부하 테스트 문서](../docs/loadtest)
- [모니터링 운영 문서](../monitoring/docs)
- [팀 개발 규칙](../AGENTS.md)

## 개발 원칙

- API 명세와 관련 ADR, 기존 코드 구조를 먼저 확인합니다.
- 핵심 비즈니스 규칙은 Domain에 둡니다.
- Setter 대신 의미 있는 상태 변경 메서드를 사용합니다.
- Controller에 비즈니스 로직을 두지 않습니다.
- 요청받지 않은 추상화와 구조 변경을 추가하지 않습니다.
- 로그에 토큰, 개인정보와 외부 서비스 인증 정보를 남기지 않습니다.
- 실패한 테스트를 삭제하거나 비활성화하지 않습니다.
