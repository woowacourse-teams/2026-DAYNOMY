# DAYNOMY 팀 개발 규칙

이 문서는 저장소에서 작업하는 사람과 AI 에이전트가 공통으로 따르는 규칙의 진입점이다.
세부 규칙은 `.codex/rules/`에, 전문 에이전트는 `.codex/agents/`에, 반복 작업 절차는 `.agents/skills/`에 둔다.

## 작업 원칙

- 모든 기능과 구조 변경은 구현 전에 구현 방향, 변경 범위, 제외 범위를 공유하고 승인을 받은 뒤 시작한다.
- API 명세, 관련 ADR과 기존 코드를 먼저 확인한다.
- 요청받지 않은 리팩토링이나 선제적인 추상화를 추가하지 않는다.
- 변경 범위는 최소화하고, 변경에 필요한 테스트와 문서만 함께 수정한다.
- 민감정보·API 키·개인정보를 코드, 로그, 응답, 커밋에 노출하지 않는다.
- 실패한 테스트를 삭제하거나 비활성화해 통과시키지 않는다.

## 영역별 규칙

- 공통: `.codex/rules/common.md`
- 백엔드: `.codex/rules/backend.md`
- 프론트엔드: `.codex/rules/frontend.md`

## 에이전트 협업

- 모든 에이전트는 작업 전에 이 문서와 `.codex/rules/common.md`를 읽고, 담당 영역의 Rule과 Skill을 함께 확인한다.
- 백엔드 구현은 `backend_worker`, 프론트엔드 구현은 `frontend_worker`, 코드 리뷰는 `code_reviewer`, CI 실패 분석은 `ci_failure_analyst`를 사용한다.
- 각 전문 에이전트는 `.codex/agents/`에 정의된 다른 전문 에이전트를 확인하고, 작업이 다른 영역으로 나뉠 때 해당 에이전트와 협업한다.
- 여러 에이전트가 작업하면 주 에이전트는 담당 범위를 나누고 결과를 모두 받은 뒤 하나로 정리한다.

## 검증 명령

- 백엔드: `cd backend && ./gradlew spotlessCheck test`
- 백엔드 PR: `cd backend && ./gradlew clean build`
- 프론트엔드: `cd frontend && npm run format:check && npm run lint && npm run typecheck && npm test`
- 프론트엔드 PR: `cd frontend && npm run build`

로컬 Hook은 `.githooks/`에 있다. 최초 1회 `./scripts/install-hooks.sh`로 활성화한다.
