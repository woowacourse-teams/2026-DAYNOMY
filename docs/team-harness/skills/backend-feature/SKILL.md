---
name: backend-feature
description: DAYNOMY 백엔드 기능을 구현할 때 사용하는 절차
---

# 백엔드 기능 구현

1. 서비스 기획서, API 명세, 관련 ADR과 기존 패키지 구조를 확인한다.
2. 구현 범위와 제외 범위, 영향받는 API·도메인·테스트를 정리해 공유한다.
3. 구현 방향을 먼저 제안하고 승인받는다. 승인 전에는 코드 수정을 시작하지 않는다.
4. Controller, Service, Domain, Repository의 책임을 나눠 최소한으로 구현한다.
5. 요청 DTO, 응답 DTO, Domain, Entity를 분리한다. 단순 변환은 정적 팩토리 메서드를 우선한다.
6. 필요한 공통 예외와 도메인 `ErrorCode`를 기존 규칙에 맞춰 추가한다.
7. 비즈니스 로직, 예외 상황, 주요 흐름의 테스트를 작성한다. 직접 쿼리나 중요한 DB 동작이 있을 때만 Repository 테스트를 추가한다.
8. `./gradlew spotlessCheck test`를 실행하고, PR 전 `./gradlew clean build`를 실행한다.

승인받지 않은 범위의 기능·리팩토링, Controller의 비즈니스 로직, Domain·Entity의 직접 응답 노출, 실패 테스트 삭제·비활성화와 검증 우회는 금지한다.
