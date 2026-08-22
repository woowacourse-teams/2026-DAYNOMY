---
name: backend-feature
description: DAYNOMY 백엔드 기능을 구현할 때 사용하는 절차
---

# 백엔드 기능 구현

1. API 명세, 관련 ADR과 기존 패키지 구조를 확인한다.
2. 구현 범위와 제외 범위, 영향받는 API·도메인·테스트를 정리해 공유한다.
3. 구현 방향을 먼저 제안하고 승인받는다. 승인 전에는 코드 수정을 시작하지 않는다.
4. Controller, Service, Domain, Repository의 책임을 나눠 최소한으로 구현한다.
5. 요청 DTO, 응답 DTO, Domain을 분리한다. 단순 변환은 정적 팩토리 메서드를 우선한다.
6. 필요한 공통 예외와 도메인 `ErrorCode`를 기존 규칙에 맞춰 추가한다.
7. 계층별 테스트는 다음 기준으로 선택한다.
   - Domain·Service: Spring Context 없이 단위 테스트를 작성하고 Repository·외부 API Client는 Mock으로 대체한다.
   - Controller: `@WebMvcTest`와 `MockMvc`를 사용하는 슬라이스 테스트로 HTTP 경계를 검증한다.
   - Repository: 직접 작성한 쿼리나 중요한 DB 동작이 있을 때만 `@DataJpaTest`와 PostgreSQL Testcontainers를 사용한다.
   - 핵심 API 흐름: `@SpringBootTest`, RestAssured, PostgreSQL Testcontainers를 사용하는 API 인수 테스트로 검증한다.
   - 외부 API 연동: 실제 외부 서버를 호출하지 않고 MockWebServer로 검증한다.
8. 단순 getter, 단순 DTO, Repository 호출 후 그대로 반환하는 코드, Spring Data JPA 기본 CRUD 자체는 별도 테스트하지 않는다.
9. `./gradlew spotlessCheck test`를 실행하고, PR 전 `./gradlew clean build`를 실행한다.

승인받지 않은 범위의 기능·리팩토링, Controller의 비즈니스 로직, Domain의 직접 응답 노출, 실패 테스트 삭제·비활성화와 검증 우회는 금지한다.
