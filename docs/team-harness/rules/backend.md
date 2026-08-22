# Backend Rule

- 객체지향 설계와 KISS, YAGNI, DRY 원칙을 따른다. 단, 원칙을 이유로 불필요한 추상화나 구조를 추가하지 않는다.
- 기본 흐름은 `Controller → Service → Domain → Repository`로 유지한다.
- Controller는 요청 검증, HTTP 처리, 응답 변환만 담당한다.
- Service는 트랜잭션과 객체 간 흐름을 조율한다.
- 핵심 비즈니스 규칙은 Domain에 두고, Repository는 데이터 접근만 담당한다.
- Setter 사용 금지하고 상태 변경을 의미 있는 메서드로 표현한다.
- 요청 DTO, 응답 DTO, Domain, Entity를 분리하며 Domain·Entity를 API 응답으로 직접 노출하지 않는다.
- 단순 DTO 변환은 정적 팩토리 메서드를 우선하고, 별도 Mapper는 변환 규칙이 복잡할 때만 만든다.
- 공통 예외 응답과 도메인별 `ErrorCode` 규칙을 따른다.
- 로그에는 필요한 추적 정보만 남기고 민감정보를 기록하지 않는다.
- Repository 테스트는 직접 쿼리를 작성했거나 DB 동작 검증이 중요한 경우에만 추가한다.
