# DAYNOMY 팀 하네스

| 구성 | 저장 위치 | 역할 |
| --- | --- | --- |
| Rule | `AGENTS.md`, `.codex/rules/` | 항상 지켜야 하는 개발 원칙 |
| Agent | `.codex/agents/` | 영역별 전문 에이전트 |
| Hook | `.githooks/` | 로컬에서 자동으로 차단·검증할 수 있는 항목 |
| Skill | `.agents/skills/` | 반복 작업의 진행 절차 |
| Template | `.github/`, `.codex/` | 이슈·PR·커밋 형식 통일 |

## 강제 수준

- 구현 방향 승인은 Rule과 Skill, Issue·PR 템플릿으로 관리한다. Git Hook으로 승인 자체를 판별하지 않는다.
- 커밋 메시지, 보호 브랜치, 민감정보·충돌 마커·대용량 파일은 Hook으로 빠르게 차단한다.
- 포맷·테스트·타입 검사·빌드는 로컬 Hook과 PR CI에서 중복 검증한다.
- `main`·`dev` 직접 푸시와 강제 푸시는 GitHub Branch protection에서 별도로 차단한다. 저장소 파일만으로는 원격 권한을 보장할 수 없다.

## 시작 방법

```bash
./scripts/install-hooks.sh
```

기능 작업은 `backend-feature` 또는 `frontend-feature`, PR 전 자체 점검은 `code-review`, CI 장애는 `ci-failure-analysis` 절차를 따른다.
