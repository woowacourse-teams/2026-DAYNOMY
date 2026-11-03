# 투자리그 사용자 API 부하 테스트

## 실행 기준

| 항목 | 기준 |
| --- | --- |
| 대상 | 사용자 API 20개, API별 개별 실행 |
| 데이터 | 참가자 최소 10,000명 + 연관 데이터 |
| 요청률 / VU | **100 RPS / 50 VU** (`VU = ceil(RPS × 0.5초)`) |
| 지속 시간 | **5분**, 예정 요청 30,000건 |
| 응답 시간 | **p95 ≤ 300ms, p99 ≤ 500ms** |
| 오류 / 검사 / 누락 | HTTP 오류 0건, 응답 검사 성공률 100%, 누락 요청 0건 |
| 추후 측정 | 300 RPS / 150 VU → 500 RPS / 250 VU, 각각 별도 실행 |

- `constant-arrival-rate`로 대상 API를 iteration당 한 번 호출한다.
- 사전 조회·인증·CSRF 준비와 종료 대기는 본 측정 5분에서 제외한다.
- VU 산식은 초기 할당 기준이다. 요청이 누락되면 목표 RPS를 달성한 것으로 판단하지 않는다.
- 결과에 배포 SHA, 주차·데이터 규모·대상 분포, 목표/실제 RPS·요청 수, VU, p95·p99, 오류·누락, 서버 CPU·메모리·DB 연결풀 상태를 기록한다.

## 대상 API

| 메서드 | 경로 |
| --- | --- |
| GET | `/api/league/rankings` |
| GET | `/api/league/investors/{publicId}/details` |
| GET | `/api/league/weeks` |
| GET | `/api/league/investors/{publicId}` |
| GET | `/api/league/investors/{publicId}/daily-history` |
| GET | `/api/users/me/investor-profile` |
| PUT | `/api/users/me/investor-profile` |
| PUT | `/api/users/me/portfolio/publication` |
| GET | `/api/users/me/portfolio/transactions` |
| POST | `/api/users/me/portfolio/decisions` |
| PUT | `/api/users/me/portfolio/decisions/{transactionId}/reason` |
| POST | `/api/users/me/portfolio/decisions/{transactionId}/reviews` |
| PUT | `/api/users/me/portfolio/decisions/{transactionId}/reviews/{reviewId}` |
| POST | `/api/users/me/league/follows/{publicId}` |
| DELETE | `/api/users/me/league/follows/{publicId}` |
| GET | `/api/users/me/league/follows/summary` |
| GET | `/api/users/me/shared-portfolio` |
| POST | `/api/users/me/shared-portfolio/import` |
| PATCH | `/api/users/me/shared-portfolio/holdings/{assetId}/visibility` |
| DELETE | `/api/users/me/shared-portfolio/holdings/{assetId}` |

내부 API는 제외한다. 종목 불러오기 화면을 함께 측정할 때는 `GET /api/users/me/portfolio`를 보조 대상으로 추가한다.
호출 코드: [리그 API](../../frontend/src/features/league/api.ts), [공유 종목 API](../../frontend/src/features/league/sharedPortfolioApi.ts).

## 데이터

모든 테이블을 각각 1만 건으로 맞추지 않고, **참가자 1만 명에 필요한 연관 데이터**를 넣는다.

| 구분 | 참가자 | 회원당 공유 종목 | 회원당 판단 | 회원당 복기 |
| --- | ---: | ---: | ---: | ---: |
| 일반 | 9,500명 | 5개 | 20개 | 10개 |
| 대형 | 500명 | 20개 | 200개 | 100개 |
| 합계 | 10,000명 | 57,500개 | 290,000개 | 145,000개 |

- 테스트용 분포다. 기존 참가자는 유지하므로 실제 전체 참가자는 1만 명을 넘을 수 있다.
- 쓰기 준비 계정: `lt-lg10k-00001`~`lt-lg10k-01000`, 원본 보유 7,000건, 팔로우 20,000건.
- 공유 보유 이력: 57,500건. 일별 수익률: 현재 주차의 수집 완료된 거래일 수 × 10,000건.
- 합성 참가자는 전주 금요일 국내 장 마감 전에 리그를 시작한 것으로 생성해 현재 주차의 온전한 순위에 포함한다.
- 과거 확정 순위·성과 데이터와 인증 토큰은 생성하지 않는다.

## DBeaver 적재

[seed-league-user-apis.sql](../../backend/loadtest/sql/seed-league-user-apis.sql)을 사용한다. 이미 적재를 확인했다면 재실행하지 않는다.

1. 활성 DB가 `daynomy_dev`인지 확인한다.
2. 현재 주차의 7일 달력과 수집된 거래일마다 양수 종가가 있는 국내 상장 종목 20개 이상을 준비한다.
3. **SQL 스크립트 실행** 메뉴로 `BEGIN`부터 `COMMIT`까지 순서대로 실행한다.
4. 첫 결과의 건수를 위 데이터 표와 비교하고, 두 번째 결과의 계정별 요청 ID를 저장한다. `source_holdings=7000`, `follows=20000`도 확인한다.

- 실패하면 같은 연결에서 `ROLLBACK;`을 실행하고 첫 오류를 확인한다. `25P02`는 앞선 실패의 후속 오류다.
- `COMMIT` 후 임시 테이블 `ltlg_cohort`는 삭제된다. 결과 재조회 오류가 나면 실제 회원·연관 데이터 건수를 확인한다.
- 시드는 중복 적재를 차단하며 쓰기 테스트 후 데이터 복원용으로 사용할 수 없다. Git 머지·배포로 자동 적재되지 않는다.

## 실행 준비

현재 스크립트는 **순위·공개 투자자 상세·팔로잉 요약 3개**다. [실행 방법](league-rankings.md)을 따른다. 나머지 17개는 실행기·응답 검사·인증 준비가 필요하다.

상세 조회는 지정한 공개 ID를 반복 호출한다. 일반·대형 참가자를 별도로 측정한다. 전체 참가자에 요청을 분산하는 기능은 추가 구현이 필요하다.

쓰기 테스트는 시작 상태를 보관하고 실행마다 복원한다. 새 판단에는 고유 `requestKey`를 사용하고, 수정 요청은 실제 값이 바뀌게 준비한다.

| 쓰기 대상 | 준비 계정의 초기 수량 | 100 RPS·5분에 필요한 조건 |
| --- | ---: | --- |
| 삭제할 공유 종목 | 0개: 5,000개 모두 원본 보유 | 성공 삭제 측정은 원본에 없는 공유 보유 30,000개 이상을 별도 준비. 원본 보유 삭제의 409는 거절 계약 검증으로 분리 |
| 해제할 팔로우 | 20,000개 | 기존 팔로우 관계 30,000개 이상 |
| 새로 가져올 종목 | 최초 동기화 전 최대 2,000개, 공유 목록 조회 후 0개 | 원본에는 있고 공유에는 없는 보유 30,000개 이상을 별도 준비. 측정 전 공유 조회·공개 설정·자동 동기화가 대상을 먼저 생성하지 않았는지 확인 |

위 시험은 추가 데이터 준비가 필요하다. 가져오기에는 원본 보유가 필요하며 수량·매입가는 서버의 원본 값으로 결정한다. 요청값만 바꾸는 재가져오기는 실제 보유 갱신 부하가 아니다. 원본 보유 종목 삭제는 `409 SHARED_HOLDING_SOURCE_MANAGED`, 원본에 없는 종목 가져오기는 `404 PORTFOLIO_HOLDING_NOT_FOUND`를 검증하고 성공 쓰기 처리량과 구분한다. 준비 요청을 본 측정에 섞거나 중복·삭제된 대상으로 빠른 응답을 받아 요청 수를 채우지 않는다.


## 같은 계정의 공유 목록 동시 조회

`GET /api/users/me/shared-portfolio`는 원본 변경 이벤트 누락과 기존 데이터의 최초 동기화를 복구하기 위해 조회 시 재대조한다. 원본·공유의 종목·수량·원화 매입가가 같은지는 잠금 없는 DB 조회로 먼저 비교한다. 같으면 재대조를 생략하며, 차이가 있으면 기존 회원 잠금 후 다시 읽어 복구한다. 비교 쿼리는 JPA 엔티티를 미리 읽지 않아 잠금 대기 후 예전 엔티티를 재사용하는 문제를 피한다.

- 서로 다른 계정 분산 측정과 같은 계정 반복 측정을 분리한다.
- 같은 계정에서 1·10·50 VU로 조회하고 p95·p99, DB 잠금 대기 시간, 원본 저장 지연을 함께 기록한다.
- 운영 규모의 잠금 경합과 100 RPS 충족 여부는 아직 검증하지 않았다. 추가 최적화는 같은 계정 반복 측정과 변경 복구 부하를 구분한 결과를 근거로 결정한다.
- 회원/포트폴리오의 `updated_at`만으로 변경 없음을 추정하지 않는다. 현재 비교는 실제 보유 행의 추가·삭제·수량·매입가 차이와 지원 자산 조건을 확인한다. 비공개 여부·보유 이유는 동기화 변경 조건이 아니다.
- 인수 테스트에서는 회원 행을 다른 연결에서 잠근 채 변경 없는 공유 목록을 동시에 조회하고, 잠금을 풀기 전에 응답하는지 검증한다. 이후 원본 수량을 바꾸면 조회가 재대조해 새 값을 반환하는지도 확인한다. 이는 운영 100 RPS 검증을 대체하지 않는다.
