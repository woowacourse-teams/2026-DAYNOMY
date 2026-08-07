# DAYNOMY BACKEND

## 기술 스택

| 항목 | 버전 |
|------|------|
| Java | 17 |
| Spring Boot | 3.5.16 |
| Gradle | 9.5.1 |
| Database | PostgreSQL |
| ORM | Spring Data JPA |

---

## 필수 설치 항목

- **JDK 17** 이상 설치
- **PostgreSQL 17** 설치 및 실행
- Git

---

## 환경 설정

### 1. 저장소 클론

```bash
git clone https://github.com/woowacourse-teams/2026-DAYNOMY
cd daynomy
```

### 2. 데이터베이스 설정

PostgreSQL에서 데이터베이스를 생성합니다.

```sql
CREATE DATABASE daynomy;
```

### 3. 애플리케이션 설정

`src/main/resources/application.yaml`에 DB 접속 정보를 추가합니다.

```yaml
spring:
  application:
    name: daynomy
  datasource:
    url: jdbc:postgresql://localhost:5432/daynomy
    username: <db-username>
    password: <db-password>
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

---

## 빌드

**macOS / Linux**

```bash
# 의존성 다운로드 및 빌드
./gradlew build

# 테스트 제외하고 빌드
./gradlew build -x test
```

**Windows**

```bash
# 의존성 다운로드 및 빌드
gradlew.bat build

# 테스트 제외하고 빌드
gradlew.bat build -x test
```

---

## 실행

```bash
# 애플리케이션 실행
./gradlew bootRun
```

또는 빌드된 JAR 파일로 직접 실행합니다.

```bash
java -jar build/libs/daynomy-0.0.1-SNAPSHOT.jar
```

서버가 정상 기동되면 `http://localhost:8080`으로 접근할 수 있습니다.

---

## 테스트

```bash
# 전체 테스트 실행
./gradlew test
```

