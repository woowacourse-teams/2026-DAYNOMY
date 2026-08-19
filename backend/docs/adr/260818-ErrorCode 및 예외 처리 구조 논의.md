# ErrorCode 및 예외 처리 구조를 논의한다

- **날짜**: 2026-08-18
- **관련 이슈**:

---

# 배경 및 해결할 문제

DAYNOMY는 API 실패 응답에 `code`를 포함하기로 결정했기 때문에, 백엔드에서 해당 에러 코드를 어떤 구조로 관리하고 예외를 어떻게 발생시킬지에 대한 기준이 필요했다.

프로젝트가 커질수록 뉴스, 회원, 인증 등 다양한 도메인에서 비즈니스 예외가 발생할 수 있었다. 이때 모든 에러 코드를 하나의 Enum으로 관리하면 구현은 단순하지만 파일이 지나치게 커지고 서로 다른 도메인의 에러가 섞일 가능성이 있었다.

반대로 모든 에러마다 별도의 예외 클래스를 생성하면 의미는 명확하지만, 예외 클래스 수가 증가하여 관리 비용도 함께 커질 수 있었다.

따라서 다음 사항을 중심으로 ErrorCode와 예외 처리 구조를 검토했다.

- ErrorCode를 하나의 Enum으로 관리할지 여부
- ErrorCode를 공통·도메인별 Enum으로 분리할지 여부
- 예외마다 별도의 Exception 클래스를 생성할지 여부
- 공통 예외를 사용할지 여부
- HTTP 상태, 에러 코드, 메시지를 어느 계층에서 관리할지

---

# 검토한 대안과 장단점

## 1. 모든 ErrorCode를 하나의 Enum으로 관리

### 예시

```java
public enum ErrorCode {
    INVALID_INPUT,
    UNAUTHORIZED,
    NEWS_NOT_FOUND,
    USER_NOT_FOUND,
    INTERNAL_SERVER_ERROR
}
```

### 장점

- 구조가 단순했다.
- 모든 에러 코드를 한 파일에서 확인할 수 있었다.
- 초기에는 구현과 관리가 쉬웠다.

### 단점

- 도메인이 증가할수록 하나의 Enum이 지나치게 커질 수 있었다.
- 서로 다른 도메인의 에러 코드가 하나의 파일에 섞였다.
- 여러 개발자가 동일한 파일을 수정하면서 충돌이 발생할 가능성이 높았다.

---

## 2. 예외마다 개별 Exception 클래스 생성

### 예시

```java
throw new NewsNotFoundException();
throw new InvalidTokenException();
```

### 장점

- 예외 이름만으로 발생 원인을 명확하게 확인할 수 있었다.
- 예외마다 별도의 처리나 추가 정보를 가지기 쉬웠다.

### 단점

- 에러가 증가할수록 예외 클래스 수도 함께 증가했다.
- 대부분의 예외가 ErrorCode만 달라지는 현재 구조에서는 반복 코드가 많아졌다.
- 현재 프로젝트 규모에서는 구조가 불필요하게 복잡해질 수 있었다.

---

## 3. 공통 ErrorCode 인터페이스 + 도메인별 ErrorCode Enum + 공통 BusinessException 사용 (선택)

### 예시

```java
throw new BusinessException(NewsErrorCode.NEWS_NOT_FOUND);
```

### 장점

- ErrorCode를 도메인별로 관리할 수 있었다.
- 모든 ErrorCode를 공통 인터페이스로 일관성 있게 처리할 수 있었다.
- 예외 클래스를 에러마다 생성하지 않아도 되었다.
- 공통 예외 처리기를 통해 모든 비즈니스 예외를 동일한 방식으로 처리할 수 있었다.
- 도메인이 증가하더라도 ErrorCode를 체계적으로 관리할 수 있었다.
- 향후 필요한 경우 개별 Exception 클래스로 확장하기 쉬웠다.

### 단점

- ErrorCode 관련 파일 수가 증가했다.
- 공통 에러와 도메인 에러를 구분하는 기준이 필요했다.
- `BusinessException` 타입만으로는 어떤 도메인의 예외인지 바로 확인하기 어려웠다.

---

# 최종 결정 내용과 선택 이유

프로젝트에서는 **공통 ErrorCode 인터페이스와 공통 BusinessException을 사용하고, 실제 에러 코드는 CommonErrorCode, NewsErrorCode, AuthErrorCode와 같이 공통 및 도메인별 Enum으로 분리하여 관리하기로 결정했다.**

예외 발생 시에는 개별 Exception 클래스를 생성하는 대신 공통 `BusinessException`에 ErrorCode를 전달하기로 했다.

```java
throw new BusinessException(NewsErrorCode.NEWS_NOT_FOUND);
```

현재 프로젝트에서는 예외마다 별도의 처리 로직이나 추가 정보가 필요하지 않았기 때문에, 모든 에러마다 개별 Exception 클래스를 생성하는 것은 관리 비용이 크다고 판단했다.

또한 프로젝트의 모든 ErrorCode를 하나의 Enum으로 관리하면 초기에는 단순하지만, 도메인이 증가할수록 파일이 커지고 서로 다른 영역의 에러가 섞여 관리가 어려워질 수 있다고 판단했다.

따라서 ErrorCode는 공통 인터페이스를 기반으로 공통 및 도메인별 Enum으로 분리하여 관리하기로 했다.

```text
ErrorCode
├── CommonErrorCode
├── NewsErrorCode
├── AuthErrorCode
└── ...
```

`ErrorCode` 인터페이스에서는 HTTP 상태, 에러 코드, 기본 메시지를 제공하고, 각 Enum이 이를 구현하도록 했다.

발생한 `BusinessException`은 `GlobalExceptionHandler`에서 일괄 처리하여 HTTP 상태와 `code`, `message`를 포함한 응답을 생성하기로 했다.

```text
Service
→ BusinessException(ErrorCode) 발생
→ GlobalExceptionHandler 처리
→ HTTP Status + code + message 반환
```

또한 의미가 같은 ErrorCode가 중복 생성되는 것을 방지하기 위해 새로운 ErrorCode를 추가하기 전에 기존 코드를 확인하고, 동일하거나 유사한 의미의 코드가 존재하면 기존 코드를 재사용하기로 했다.

ErrorCode의 이름은 `{DOMAIN}_{REASON}` 형식을 기본 규칙으로 사용하고, 추가 및 변경 사항은 PR에서 함께 리뷰하기로 했다.

---

# 결정에 따른 영향 및 트레이드오프

## 긍정적인 영향

- 모든 비즈니스 예외를 동일한 방식으로 처리할 수 있게 되었다.
- 예외마다 별도의 Exception 클래스를 생성하지 않아 반복 코드를 줄일 수 있게 되었다.
- ErrorCode를 도메인별로 분리하여 관리할 수 있게 되었다.
- 도메인이 증가하더라도 ErrorCode를 체계적으로 관리할 수 있게 되었다.
- `GlobalExceptionHandler`를 통해 일관된 에러 응답을 생성할 수 있게 되었다.
- ErrorCode 명명 규칙과 재사용 기준을 마련하여 의미상 중복되는 에러 코드 생성을 줄일 수 있게 되었다.

## 트레이드오프

- ErrorCode 인터페이스와 도메인별 Enum으로 인해 관련 파일 수가 증가했다.
- 공통 에러와 도메인별 에러를 구분하는 기준이 필요하게 되었다.
- `BusinessException` 타입만으로는 어떤 도메인에서 발생한 예외인지 바로 확인하기 어려웠다.
- ErrorCode가 추가되거나 변경될 경우 프론트엔드와 변경 사항을 공유해야 했다.
- 향후 예외마다 별도의 데이터나 처리 방식이 필요해질 경우 개별 Exception 클래스를 추가해야 할 수 있다.