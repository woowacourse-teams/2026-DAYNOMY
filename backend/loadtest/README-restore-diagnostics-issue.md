# [BE] 부하 테스트 데이터 복원 실패 시 불일치 원인을 확인할 수 없는 문제

## 🐛 버그 설명

부하 테스트용 데이터를 원본 파일에서 dev DB로 복원할 때 데이터 불일치로 실패하지만, 어떤 테이블과 컬럼이 다른지 출력되지 않아 원인을 파악하기 어렵다.

## 🔄 재현 방법

1. 기존 부하 테스트 원본 JSON 파일을 준비한다.
2. dev 환경에서 `PortfolioLoadtestTool`의 `restore` 명령을 실행한다.
3. 복원 후 데이터 검증이 실패하면 오류 메시지를 확인한다.

## ✅ 예상 동작

- 불일치한 **테이블·컬럼·차이 유형·건수**를 확인할 수 있다.
- 실제 데이터 값, 회원 ID, 토큰, 접속 정보는 출력되지 않는다.
- 검증 실패 시 커밋하지 않고 롤백하여 기존 데이터를 보존한다.
- 토큰 발급 전 데이터 검증에서도 불일치 원인을 확인할 수 있다.

## ❌ 실제 동작

기존 도구의 기본 실행 경로에서는 예외 유형만 출력된다. 별도 진단 코드를 실행해도 `Restored data differs`라는 메시지만 확인할 수 있어, 불일치한 테이블과 컬럼을 알 수 없다.

## 🌐 요청 정보

API 요청이 아닌 **부하 테스트 준비용 CLI 도구**에서 발생한다.

- HTTP 메서드: 해당 없음
- 요청 URL: 해당 없음
- 요청 데이터: 부하 테스트 원본 JSON 파일
- 응답 상태 코드: 해당 없음
- 응답 데이터: 표준 오류 메시지 출력 후 종료

## 📎 로그 및 참고 자료

기존 도구의 기본 오류 메시지:

```text
Preparation failed (IllegalArgumentException). Check private dev settings, cohort and baseline. No credentials or tokens printed.
```

별도 진단 과정에서 확인한 오류:

```text
Failure type: IllegalArgumentException
Restored data differs; transaction will be rolled back
org.grit.daynomy.loadtest.PortfolioLoadtestTool.restore(PortfolioLoadtestTool.java:388)
org.grit.daynomy.loadtest.PortfolioLoadtestTool.run(PortfolioLoadtestTool.java:151)
```

수정 대상: `backend/src/test/java/org/grit/daynomy/loadtest/PortfolioLoadtestTool.java`

진단 출력과 민감정보 비노출, 검증 실패 시 롤백을 회귀 테스트로 검증한다. 현재 로그만으로는 실제 불일치 컬럼이나 Flyway 마이그레이션 실패 여부를 확정할 수 없다.
