# 거래 등록 구간 계측

`daynomy.portfolio.transaction.phase` Timer는 API(`v1`,`v2`)와 고정된 `phase`만
태그로 사용한다. 회원 ID·키·거래 내용은 기록하지 않는다. 분포 히스토그램은
추가하지 않고 count/sum/max를 노출한다. 전후 비교는 같은 계측을 유지한다.

| phase | 측정 범위 |
| --- | --- |
| transaction_total | TransactionTemplate 시작 전부터 반환/예외까지. 풀 획득·커밋 후 동기 처리도 포함 |
| member_lock_query | 회원 FOR UPDATE Repository 호출. 순수 잠금 대기가 아니라 SQL·통신 포함 |
| member_lock_held | 관측한 첫 회원 잠금 조회 반환부터 커밋/롤백 확인 콜백까지의 앱 측 추정 |
| portfolio_access | 포트폴리오 조회·잠금·생성 전체 |
| portfolio_lock_query | V2의 기존 포트폴리오 잠금 조회 |
| history_read | 거래 처리의 원장 이력 조회 |
| ledger_replay | 조회된 이력 재생. 지연 로딩이 있다면 그 비용도 포함 |
| snapshot_refresh | 거래 등록의 스냅샷 갱신 |
| idempotency_lookup | 쓰기 트랜잭션 전 완료 키 조회 |
| idempotency_recheck | 회원 잠금 후 키 재조회 |
| result_write | 응답 직렬화와 멱등성 결과 INSERT |

잠금 유지 표본은 같은 API·트랜잭션에서 한 번만 등록한다. 성공 시 우선순위가
높은 afterCommit에서 끝내 커밋 후 리그 동기화 시간을 제외하고, 롤백도 종료한다.
이는 DB 서버의 실제 잠금 획득/해제 시각을 직접 관측한 값이 아니다. V1 가져오기 등
공유 내부 경로는 이미 획득한 잠금 전체를 포착하지 못할 수 있어 단건 등록과 분리한다.

부모·자식 구간이 겹치므로 모든 구간 합계를 응답시간으로 더하지 않는다.
중복 조회 전후는 member_lock_query 호출 횟수가 달라진다. `sum/count` 호출당 평균과
`구간 sum / 해당 구간의 거래 요청 수` 거래당 총시간을 함께 보고한다.
최종 응답 p95/p99는 발생기 요청 원본으로 별도 계산한다.

계측만 추가한 기준 버전에서 기존 중복 조회를 먼저 측정한다. 다음 버전은
잠금 순서·키 재확인·원자적 저장을 유지하고 잠근 Member를 재사용한다.
리그 제외 검증, 동일 초기 이력·JVM·RPS·측정시간·횟수로 비교하고 정확성도 대조한다.
