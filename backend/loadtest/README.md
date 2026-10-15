# dev 포트폴리오 거래 등록 k6

| 실행 파일            | 등록 API                                       |
| -------------------- | ---------------------------------------------- |
| `transactions-v1.js` | `POST /api/users/me/portfolio/transactions`    |
| `transactions-v2.js` | `POST /api/v2/users/me/portfolio/transactions` |

공통 요청·검증은 `transaction-scenario.js`에서 사용한다. 한 번에 한 버전만 실행한다.
로컬 서버·DB를 생성하거나 dev 데이터를 초기화하는 기능은 없다.

## 준비

1. dev에 테스트 회원과 포트폴리오 데이터를 준비하고 각 회원의 access token을 확보한다.
2. 예시를 복사해 실제 테스트 회원 정보로 바꾼다. VU마다 서로 다른 회원을 사용한다.

```bash
cd backend/loadtest
mkdir -p .local
cp users.example.json .local/users.json
```

- `memberId`: 테스트 회원 ID. 파일의 첫 회원부터 VU 수만큼 사용한다.
- `accessToken`: 해당 회원의 dev access token.
- `assetId`, `unitPrice`: 등록할 종목과 거래 단가. 요청당 1주, 수수료·세금 0으로 등록한다.
- `initialQuantity`: 실행 직전 해당 종목의 현재 보유 수량. 제공하면 매 요청 후 수량도 검증한다. 생략 가능하다.

`.local/`은 Git에서 제외한다. 실제 토큰은 예시 파일에 넣지 않는다.
과거 거래일은 마지막 잔고 등록·조정일 이후여야 하며, 매도는 해당 거래일 이후에도 수량이 부족하지 않아야 한다.

## 실행

dev API 주소와 테스트할 거래일을 지정한다. 아래 날짜는 예시다.

```bash
k6 run \
  -e BASE_URL='https://<dev-api-host>' \
  -e TRADE_DATE=2026-04-15 \
  -e SIDE=BUY -e VUS=1 -e ITERATIONS=3 \
  --summary-export .local/v1-six-months-buy.json \
  transactions-v1.js
```

V2는 동일 조건에서 `transactions-v2.js`로 실행한다.

| 입력         | 의미                                   | 기본값                |
| ------------ | -------------------------------------- | --------------------- |
| `BASE_URL`   | dev API origin. `/api`를 붙이지 않는다 | 필수                  |
| `TRADE_DATE` | 등록할 거래일, `YYYY-MM-DD`            | 필수                  |
| `USERS_FILE` | 테스트 회원 JSON 배열                  | `./.local/users.json` |
| `SIDE`       | `BUY` 또는 `SELL`                      | `BUY`                 |
| `VUS`        | 동시 사용자 수                         | `1`                   |
| `ITERATIONS` | 회원당 등록 건수                       | `1`                   |

오늘·1개월 전·6개월 전은 `TRADE_DATE`를 각각 지정해 따로 실행한다.
동시 사용자는 1·5·10·20·50명으로 늘리되, 회원 파일에도 그 수만큼 계정을 준비한다.
매수·매도도 별도로 실행한다. VU는 응답을 받은 뒤 다음 거래를 즉시 등록한다.

## 비교 기준

- V1/V2 실행 전 테스트 회원의 거래 이력·보유 종목·스냅샷을 같은 초기 상태로 복원한다. 스크립트가 대신 초기화하지 않는다.
- 거래일·종목·단가·회원당 요청 건수·서버 설정을 맞추고 두 버전을 순차 실행한다.
- `transaction_duration`은 거래 POST만 측정한다. `http_req_duration`에는 CSRF 획득 요청도 포함된다.
- `transaction_success`가 `VUS × ITERATIONS`인지 확인한다. 상태·응답 검증이 실패하면 중단하며 재시도하지 않는다.
- 상태 201·거래 ID·응답 종목과 선택적인 보유 수량을 검사한다. 과거 스냅샷 전체 정합성은 이 스크립트에서 검사하지 않는다.
- 한 회원당 고정 건수 시험이다. 최대 처리량·고정 RPS 시험이나 안정적인 p95 측정과 구분한다.
