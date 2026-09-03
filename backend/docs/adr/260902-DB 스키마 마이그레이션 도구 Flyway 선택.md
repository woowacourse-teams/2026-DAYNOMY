# DB 스키마 마이그레이션 도구로 Flyway를 선택한다

- **날짜**: 2026-09-02
- **관련 이슈**: [Discussion #143](https://github.com/woowacourse-teams/2026-DAYNOMY/discussions/143)

---

# 배경 및 해결할 문제

DAYNOMY는 Hibernate의 `ddl-auto`를 이용해 Entity와 데이터베이스 스키마를 관리해 왔다. 그러나 운영 데이터가 쌓인 이후에는 자동 변경이나 수동 SQL 실행만으로 스키마 변경을 안전하게 관리하기 어려워졌다.

프로젝트에서는 다음과 같은 요구사항을 만족할 필요가 있었다.

- 컬럼 추가·삭제와 타입 변경 이력을 관리할 것
- 기존 데이터 보정과 인덱스 변경을 정해진 순서로 적용할 것
- `GIN`, `pg_trgm`, Extension 등 PostgreSQL 전용 SQL을 관리할 것
- 개발·테스트·운영 환경에 동일한 스키마 변경을 적용할 것
- 운영 DB에 적용된 변경 버전을 추적하고 신규 환경에서 같은 스키마를 재현할 것

이를 해결하기 위해 Hibernate `ddl-auto`, Flyway, Liquibase를 검토했다.

---

# 검토한 대안과 장단점

## 1. Hibernate `ddl-auto`로 스키마 자동 변경

### 장점

- 별도의 마이그레이션 도구가 필요하지 않았다.
- Entity 변경을 빠르게 스키마에 반영할 수 있었다.
- 초기 설정과 학습 비용이 적었다.

### 단점

- 스키마 변경 이유와 적용 순서를 추적하기 어려웠다.
- 기존 데이터가 있는 운영 DB에서 의도한 변경을 보장하기 어려웠다.
- PostgreSQL Extension과 전용 인덱스를 관리하기 어려웠다.
- 환경마다 스키마 상태가 달라질 수 있었다.

---

## 2. Flyway (선택)

### 장점

- PostgreSQL SQL을 그대로 마이그레이션 파일로 관리할 수 있었다.
- 버전과 체크섬으로 적용 이력을 추적할 수 있었다.
- 모든 환경에 같은 변경을 정해진 순서로 적용할 수 있었다.
- Git에서 SQL과 변경 이유를 함께 관리할 수 있었다.
- Spring Boot와 연동하기 단순했다.

### 단점

- 스키마 변경 SQL을 직접 작성하고 검증해야 했다.
- 기존 운영 DB에 도입하기 위한 Baseline 작업이 필요했다.
- 이미 적용한 마이그레이션 파일을 수정하거나 삭제할 수 없었다.
- 실패한 변경은 복구 SQL이나 후속 마이그레이션으로 보정해야 했다.

---

## 3. Liquibase

### 장점

- SQL 외에도 YAML, XML, JSON 형식을 지원했다.
- Precondition, Context, Label과 같은 실행 제어 기능을 제공했다.
- ChangeSet과 태그를 기준으로 Rollback을 관리할 수 있었다.
- 여러 종류의 데이터베이스와 복잡한 변경 정책에 대응할 수 있었다.

### 단점

- ChangeSet, Changelog, Context 등 추가 개념과 설정이 필요했다.
- PostgreSQL 전용 기능은 Liquibase에서도 SQL을 직접 작성해야 했다.
- PostgreSQL 단일 DB에 동일한 변경을 적용하는 현재 요구사항에는 관리 비용이 컸다.

---

# 최종 결정 내용과 선택 이유

프로젝트에서는 DB 스키마 마이그레이션 도구로 **Flyway**를 선택하기로 결정했다.

DAYNOMY는 하나의 PostgreSQL을 사용하며 모든 환경에 동일한 SQL을 순서대로 적용하는 것이 핵심이다. 현재 필요한 변경 이력 관리, 중복 실행 방지, 적용 버전 확인, PostgreSQL 전용 SQL 실행은 Flyway의 버전 기반 SQL 마이그레이션으로 충족할 수 있다.

Liquibase의 조건부 실행과 다중 DB 지원 기능은 현재 필요하지 않으며, PostgreSQL 전용 기능에는 Liquibase를 사용하더라도 직접 SQL을 작성해야 한다. 따라서 추가 개념과 설정이 적은 Flyway가 현재 프로젝트 규모와 요구사항에 적합하다고 판단했다.

역할은 다음과 같이 분리한다.

```text
Flyway    : DB 스키마 변경 실행 및 이력 관리
Hibernate : Entity와 실제 DB 스키마의 일치 여부 검증
Git       : 마이그레이션 SQL과 변경 이유 관리
```

Hibernate가 스키마를 자동 변경하지 않도록 `ddl-auto: validate`를 유지한다.

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

마이그레이션 파일은 다음 위치와 형식으로 관리한다.

```text
backend/src/main/resources/db/migration/
├── V1__baseline_schema.sql
├── V2__add_news_indexes.sql
└── V3__change_column_type.sql
```

또한 다음과 같은 원칙을 적용하기로 했다.

- 파일명은 `V{version}__{description}.sql` 형식의 영문 `snake_case`로 작성한다.
- 기존 운영 DB는 현재 스키마를 확인한 후 일회성 Baseline 처리를 수행한다.
- 신규 환경을 재현할 수 있도록 현재 전체 스키마를 Baseline 파일에 기록한다.
- Entity 변경과 Flyway 마이그레이션을 함께 작성한다.
- 개발·테스트 환경에서 검증한 마이그레이션을 운영에 동일한 순서로 적용한다.
- 이미 적용된 마이그레이션 파일은 수정하거나 삭제하지 않고 새로운 버전을 추가한다.
- 운영 DB 스키마를 수동으로 변경하지 않는다.
- 실패한 변경은 원인을 확인한 후 복구하거나 후속 마이그레이션으로 롤포워드한다.

향후 여러 종류의 데이터베이스를 지원하거나 환경별 조건부 실행, 엄격한 ChangeSet 단위 Rollback이 필요해질 경우 Liquibase를 다시 검토하기로 했다.

---

# 결정에 따른 영향 및 트레이드오프

## 긍정적인 영향

- 스키마 변경 SQL과 적용 이력을 Git과 DB에서 함께 추적할 수 있게 되었다.
- 개발·테스트·운영 환경의 스키마를 같은 순서로 재현할 수 있게 되었다.
- PostgreSQL Extension과 전용 인덱스를 명시적인 SQL로 관리할 수 있게 되었다.
- Hibernate의 역할을 스키마 검증으로 제한해 자동 변경에 따른 불확실성을 줄일 수 있게 되었다.

## 트레이드오프

- 모든 스키마 변경에 대해 SQL 작성과 검토가 필요하게 되었다.
- 기존 운영 DB에 대한 초기 Baseline 검증과 일회성 적용 작업이 필요하다.
- 적용된 파일을 수정할 수 없어 잘못된 변경은 새 마이그레이션으로 보정해야 한다.
- 운영 배포 과정에서 마이그레이션 실패와 롤포워드 절차를 관리해야 한다.
