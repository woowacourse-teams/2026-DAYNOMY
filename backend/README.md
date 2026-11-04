# DAYNOMY Backend

DAYNOMY의 **포트폴리오 기록과 AI 분석**을 처리하는 Spring Boot API 서버입니다.

사용자의 보유 자산과 거래 기록을 계산하고, 포트폴리오 분석의 생성·저장·재확인·변경 이력을 관리합니다. 인증, 경제 콘텐츠, 투자 리그와 관리자 기능도 함께 제공합니다.

> 전체 프로젝트 소개는 [루트 README](../README.md)를 확인해 주세요.

## 담당 영역

| 영역            | 주요 책임                                              |
| --------------- | ------------------------------------------------------ |
| 포트폴리오 분석 | 분석 생성·저장·재확인, 근거와 변경 이력 관리           |
| 자산과 거래     | 국내·미국 주식·ETF, 거래 원장, 종가·환율과 수익률 계산 |
| 콘텐츠          | 뉴스·인물·외부 자료 수집, 통합 검색과 관리자 검수      |
| 투자 리그       | 주간 순위, 공개 기록, 복기 알림과 기사 기반 AI 질문    |
| 이메일 알림     | 중요 종목 자동 분석, 구독·해지와 예약 발송             |
| 인증과 운영     | Google OIDC, JWT, 권한 관리와 관측 가능성              |

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
├── emailsubscription/    # 포트폴리오 이메일 알림
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

분석 조회는 저장된 결과를 반환하며 AI를 새로 호출하지 않습니다. 자산 변경, 처리 중, 근거 재확인과 실패 상태를 구분하고 새로운 분석과 최신 근거 확인을 별도 요청으로 처리합니다.

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
- Git — Gradle은 저장소의 Wrapper를 사용합니다.

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

AI, S3와 외부 콘텐츠 기능의 추가 설정은 `src/main/resources/application.yaml`을 기준으로 합니다.

### 실행

```bash
./gradlew bootRun
```

API는 `http://localhost:8080`, Swagger UI는 `http://localhost:8080/swagger-ui.html`, 관리 엔드포인트는 `http://localhost:8081`에서 제공합니다.

Google OAuth의 로컬 리디렉션 URI는 다음과 같습니다.

```text
http://localhost:8080/login/oauth2/code/google
```

로컬에서는 프론트엔드와 백엔드 모두 `localhost`를 사용하고 `127.0.0.1`과 혼용하지 않습니다.

## 데이터베이스 마이그레이션

스키마는 Flyway로 관리하며 JPA는 `ddl-auto: validate`로 검증합니다.

- 이미 배포된 마이그레이션 파일을 수정하지 않습니다.
- 변경은 `src/main/resources/db/migration/`에 다음 버전으로 추가합니다.
- 이력 불일치를 초기화나 `repair`로 우회하지 않습니다.

## 테스트와 검증

```bash
# 코드 포맷과 전체 테스트
./gradlew spotlessCheck test

# PR 수준 전체 검증
./gradlew clean build
```

핵심 비즈니스 규칙, API 계약, 인증 경계와 데이터 정합성을 우선 검증합니다. DB 테스트에는 PostgreSQL Testcontainers를 사용합니다.

## 관측 가능성

Actuator와 Prometheus 메트릭, OpenTelemetry 트레이싱, 요청 식별자를 포함한 구조화 로그를 제공합니다. 자세한 구성은 [모니터링 문서](../monitoring/docs)를 확인해 주세요.

## 관련 문서

- [백엔드 ADR](./docs/adr)
- [포트폴리오 일괄 편집](./docs/portfolio-bulk-edit.md)
- [투자 리그 AI 복기 기술 명세](../docs/league-ai-review-technical.md)
- [부하 테스트](./loadtest/README.md)
- [공통 부하 테스트 문서](../docs/loadtest)
- [모니터링 운영 문서](../monitoring/docs)
- [팀 개발 규칙](../AGENTS.md)
