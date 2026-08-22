# DAYNOMY 팀 개발 규칙

이 문서는 저장소에서 작업하는 사람과 AI 에이전트가 공통으로 따르는 규칙의 진입점이다.
세부 규칙은 `docs/team-harness/rules/`에, 반복 작업 절차는 `docs/team-harness/skills/`에 둔다.

## 작업 원칙

- 모든 기능과 구조 변경은 구현 전에 구현 방향, 변경 범위, 제외 범위를 공유하고 승인을 받은 뒤 시작한다.
- API 명세, 관련 ADR과 기존 코드를 먼저 확인한다.
- 요청받지 않은 리팩토링이나 선제적인 추상화를 추가하지 않는다.
- 변경 범위는 최소화하고, 변경에 필요한 테스트와 문서만 함께 수정한다.
- 민감정보·API 키·개인정보를 코드, 로그, 응답, 커밋에 노출하지 않는다.
- 실패한 테스트를 삭제하거나 비활성화해 통과시키지 않는다.

## 영역별 규칙

- 공통: `docs/team-harness/rules/common.md`
- 백엔드: `docs/team-harness/rules/backend.md`
- 프론트엔드: `docs/team-harness/rules/frontend.md`

## 검증 명령

- 백엔드: `cd backend && ./gradlew spotlessCheck test`
- 백엔드 PR: `cd backend && ./gradlew clean build`
- 프론트엔드: `cd frontend && npm run format:check && npm run lint && npm run typecheck && npm test`
- 프론트엔드 PR: `cd frontend && npm run build`

로컬 Hook은 `.githooks/`에 있다. 최초 1회 `./scripts/install-hooks.sh`로 활성화한다.
