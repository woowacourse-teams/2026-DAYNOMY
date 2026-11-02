# 포트폴리오 대시보드 이벤트 (#770)

`trackPortfolioEvent` → 공통 `trackEvent` → GA4·PostHog 경로를 사용한다.
포트폴리오와 공통 전송의 속성 허용 목록을 모두 통과해야 한다. `sort_direction`도 두 도구에 전달한다.
AI CTA 노출은 기존 `useContentImpression`의 공통 전송 경로를 사용한다.
회원 식별자를 추가하거나 자동 수집을 켜지 않는다. 기존 수집 거부·관리자 도메인 제외·URL 정리 정책을 따른다.

## 이벤트 계약

| 이벤트                          | 시점·횟수                                                                                                                        | 속성                                                                                                                                                  |
| ------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| `portfolio_bulk_opened`         | 일괄편집 창 마운트당 한 번. StrictMode 효과 재실행 제외                                                                          | `mode`: holdings/add/csv, `storage`: server/temporary, `entry_point`: ledger/header                                                                   |
| `portfolio_bulk_save_submitted` | 입력 검증 통과 후 저장을 시작할 때. 진행 중 추가 클릭 제외                                                                       | mode, storage, CSV일 때 csv_kind, row_count                                                                                                           |
| `portfolio_bulk_save_result`    | 각 제출의 저장 응답·임시 저장 완료 또는 실패                                                                                     | 제출 속성 + status: success/duplicate/error, imported_count, duration_ms, 실패 시 error_type                                                          |
| `portfolio_bulk_closed`         | 사용자가 명시적으로 닫기. 변경 취소 확인이 있으면 확인 후 한 번                                                                  | mode, storage, csv_kind, stage, has_changes, duration_ms                                                                                              |
| `portfolio_csv_read_result`     | 선택한 파일의 읽기·구문 분석 결과. 취소된 작업 제외                                                                              | mode, storage, csv_kind: balances/transactions, status: success/error, stage: file/parse, row_count, reason, duration_ms                              |
| `portfolio_csv_mapping_changed` | 사용자 열 연결·헤더·구분자 변경                                                                                                  | mode, storage, csv_kind, mapping_mode: manual, status: success/error (필수 연결 유효성)                                                               |
| `portfolio_csv_preview_ready`   | 종목 매칭과 미리보기 준비 완료. 자동 진행도 포함                                                                                 | mode, storage, csv_kind, mapping_mode: automatic/manual, row_count, matched_count, unmatched_count, skipped_count, duration_ms                        |
| `portfolio_csv_preview_failed`  | 읽기 성공 후 미리보기 준비 실패. 취소된 작업 제외                                                                                | mode, storage, csv_kind, stage, reason, error_type, duration_ms                                                                                       |
| `portfolio_search_result`       | 250ms 지연 후 실행한 국내·미국 통합 검색이 끝날 때 한 번. 자동 CSV 매칭 요청은 제외                                              | surface: transaction_editor/bulk_editor, search_length, status, duration_ms, 성공 시 result_count/kr_result_count/us_result_count, 실패 시 error_type |
| `portfolio_stock_selected`      | 기존 개별 선택 + 일괄편집에서 직접 선택                                                                                          | category, market, surface: transaction_editor/bulk_editor                                                                                             |
| `portfolio_validation_failed`   | 잘못된 필드에서 초점을 옮기거나 검증으로 제출이 거절될 때                                                                        | mode, field, reason: invalid_value, trigger: blur/submit                                                                                              |
| `portfolio_editor_closed`       | 개별 거래 입력·수정 창의 닫기·취소·바깥 클릭·Esc. 같은 닫기는 한 번                                                              | mode, stage: search/input/validation_error/save_error, has_changes, duration_ms                                                                       |
| `portfolio_analysis_cta_viewed` | 분석·상태 확인·로그인·재시도 버튼이 화면에서 50% 이상 보이고 문서가 활성일 때. 마운트 내 동일 action/reason/enabled 조합당 한 번 | action: analyze/refresh/check_status/login/retry, enabled, reason, user_type: member/guest, has_holdings, pricing_status                              |
| `portfolio_analysis_completed`  | 이 화면에서 시작한 분석·갱신의 최종 상태를 응답 또는 폴링으로 확인할 때 한 번                                                    | action: analyze/refresh, user_type, asset_count, status, summary_status (제공 시), duration_ms                                                        |
| `portfolio_dashboard_loaded`    | 기존 마운트당 한 번. 보유 조회와 현재 평가 완료, 빈 포트폴리오도 포함                                                            | 기존 속성 + duration_ms, holding_count_bucket: 0/1/2_5/6_20/21_plus                                                                                   |

### 결과와 시간의 의미

- `mode=holdings`는 기존 보유 정정, `add`는 신규 보유잔고 일괄 추가, `csv`는 파일 가져오기다. `csv_kind`로 보유현황과 거래내역을 구분한다.
- 저장 `row_count`는 보유 정정에서 실제 변경 종목 수, 추가·CSV에서는 제출 행 수다. `imported_count`는 신규 반영 행 수이며 중복·실패는 0이다. 실제 주식 수량이 아니다.
- 거래 CSV의 `alreadyImported=true`는 `status=duplicate`이며 신규 저장 성공에서 제외한다. 보유현황 CSV의 기존 종목 중복은 입력 검증으로 저장이 차단되는 별도 동작이다.
- 보유현황 미리보기 `row_count=matched_count+unmatched_count+skipped_count`다. 제외 행은 합계·소계 등이며, 미매칭 행이 있어도 미리보기는 준비될 수 있다. 저장 가능함을 의미하지 않는다. 거래 CSV는 모든 종목이 매칭되어야 준비 이벤트를 보낸다.
- 검색 `result_count`는 종목 ID 중복을 제거한 통합 결과 수다. 국내·미국 결과 수는 각 요청의 결과 수로 합이 통합 결과 수와 다를 수 있다. 이후 화면의 주식/ETF 필터는 적용하기 전이다.
- duration_ms는 저장에서 저장 시작→결과, CSV 읽기에서 파일 선택→구문 분석, 미리보기에서 종목 매칭 시작→준비, 검색에서 요청 실행→결과, 닫기에서 창 열기→닫기, 대시보드에서 마운트→초기 로딩 완료다. 브라우저의 단조 증가 시계를 사용하고 음수는 0으로 제한한다.
- 개별 검증은 같은 필드·값의 blur→submit을 중복 집계하지 않으며 사용자가 입력을 바꾸면 다시 측정한다. 원본 값은 브라우저 메모리에서 중복 비교에만 사용하고 전송하지 않는다. `has_changes`는 개별 창에서 사용자의 입력·선택 여부, 일괄편집에서는 미저장 변경 여부다.
- 저장 성공으로 닫힘·삭제·컴포넌트 제거·브라우저 종료는 명시적 닫기로 집계하지 않는다. 분석 중 화면 이탈도 완료로 만들지 않는다.
- `portfolio_analysis_result`는 기존 요청 응답·복구 결과다. `PROCESSING`은 최종 완료가 아니다. 새 완료 이벤트는 READY의 요약 PENDING도 기다리며 최종 summary_status를 남긴다. FAILED·ERROR·CONFLICT·TRIAL_EXHAUSTED·REFRESH_LIMIT_EXCEEDED·EMPTY·PORTFOLIO_CHANGED·RECHECK_REQUIRED는 성공과 구분한다. 단순 저장 결과 복원은 완료 이벤트를 만들지 않는다. 다른 탭·새로고침 이후 작업 완료는 이 화면의 시작 측정과 이어 붙이지 않는다.
- CTA `enabled`는 해당 버튼을 누를 수 있는지다. check_status는 분석 시작이 아니며 login은 로그인 이동이다. 요약 대기는 reason=summary_pending, 당일 갱신 횟수 소진은 reason=refresh_limit_exceeded로 구분하며 해당 버튼은 비활성이다. 복원 중 버튼이 눌려도 새 분석을 시작하지 않는 경우는 reason=restoring으로 구분한다. 가격 불완전·보유 불일치로 버튼 자체가 렌더링되지 않으면 CTA 노출로 집계하지 않는다. 비활성·화면 밖·가이드 예시는 실행 가능한 분석 노출의 분모에서 제외한다.

## 수집하지 않는 값

`/privacy`의 서비스 이용 분석 안내에도 CSV 처리 건수·개수 구간·검증 오류·명시적 중단·처리 및 편집 시간과 브라우저·세션 단위 분석을 반영한다.

검색어 원문, CSV·파일명·열 이름·미리보기 원문, 종목 ID·이름·코드, 거래일, 투자 금액·수량·단가·환율, 원문 예외·서버 메시지를 추가 이벤트에 보내지 않는다.
건수·구간·분류·정규화된 오류 코드만 전달한다. reason은 invalid_file/invalid_csv/stock_not_found/request_failed 등의 고정 값이다.

## 분석 설정

### GA4

개발 속성의 DebugView 또는 실제 collect 요청에서 이벤트와 속성을 먼저 확인한다.
보고서에서 사용할 mode, storage, csv_kind, status, stage, reason, mapping_mode, surface, field, trigger, user_type, action, enabled, pricing_status, holding_count_bucket, sort_direction, summary_status를 필요한 범위에서 이벤트 범위 맞춤 측정기준으로 등록한다.
기간·건수 비교에 필요한 duration_ms, row_count, imported_count, matched_count, unmatched_count, skipped_count, result_count 등을 맞춤 측정항목으로 등록한다. 등록 한도를 확인해 실제 보고서에 쓸 속성부터 선택한다.

### PostHog

- 일괄편집: bulk_opened → bulk_save_submitted → bulk_save_result(status=success). mode·storage별로 분류한다.
- CSV: bulk_opened(mode=csv) → csv_read_result(status=success) → csv_preview_ready → bulk_save_submitted → bulk_save_result(status=success). csv_kind를 일치시키고 duplicate는 별도 집계한다. 창 열기에서 파일 선택 없이 나간 사용자는 이후 단계 미도달로 계산한다.
- 검색: search_result(status=success) → stock_selected. surface를 일치시켜 개별 입력과 일괄편집을 비교한다.
- 입력: editor_opened → save_submitted → save_succeeded와 validation_failed·editor_closed를 함께 본다. 검증 실패율의 분모는 입력 시도자 기준으로 정의한다.
- AI: analysis_cta_viewed(enabled=true, action=analyze/refresh/retry, reason!=restoring) → start_portfolio_analysis → analysis_completed(status=READY). summary_status=FAILED는 요약 실패로 분리한다. 신규 분석과 갱신의 action을 구분한다.
- 전환율은 같은 기간의 고유 브라우저 사용자 기준으로 시작하며 전환 창·세션 범위를 명시한다. 반복 파일 선택·재시도 횟수는 이벤트 횟수로 따로 본다. 현재 익명 수집을 계정별·기기 간 사용자 추적으로 해석하지 않는다.

## 검증 상태

자동 검증은 공통 전송 경계의 GA4·PostHog 속성 보존·민감정보 제거와 화면의 성공/실패/재시도/중복/취소/가시성/분석 폴링 시나리오를 대상으로 한다.
실제 개발 GA4·PostHog 프로젝트 수신, 맞춤 정의 등록, 저장된 퍼널 설정은 로컬 테스트와 별도 확인 사항이다. 이 문서는 외부 프로젝트 설정 완료를 의미하지 않는다.
