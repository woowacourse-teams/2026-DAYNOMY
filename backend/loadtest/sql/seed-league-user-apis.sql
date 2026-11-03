-- DBeaver에서 daynomy_dev 연결을 선택하고 BEGIN부터 COMMIT까지 전체를 실행한다.
-- 테스트 참가자 10,000명을 새로 추가한다. 기존 회원·종목·종가·확정 순위는 변경하지 않는다.
-- 현재 주차의 수집 완료된 종가가 있는 국내 상장 종목이 최소 20개 필요하다.
-- lt-lg10k- 회원이 이미 있으면 중단한다. 기존 테스트 데이터를 삭제하거나 덮어쓰지 않는다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '5min';

CREATE TEMP TABLE ltlg_config ON COMMIT DROP AS
SELECT 'daynomy_dev'::text AS expected_database,
    date_trunc('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date AS week_start,
    'lt-lg10k-'::text AS member_prefix,
    10000 AS target_members,
    1000 AS write_members,
    500 AS heavy_members;
-- 격리 테스트에서만 아래 날짜를 해당 테스트 주차로 바꾼다.
-- UPDATE ltlg_config SET week_start = DATE '2026-10-19';

DO $$
BEGIN
    IF current_database() <> (SELECT expected_database FROM ltlg_config) THEN
        RAISE EXCEPTION '개발 DB(daynomy_dev) 연결에서만 실행할 수 있습니다.';
    END IF;
    IF NOT pg_try_advisory_xact_lock(601, 10000) THEN
        RAISE EXCEPTION '다른 리그 1만 명 적재가 진행 중입니다.';
    END IF;
    IF EXISTS (
        SELECT FROM members m CROSS JOIN ltlg_config c
        WHERE m.provider_id LIKE c.member_prefix || '%'
           OR m.nickname LIKE c.member_prefix || '%'
    ) THEN
        RAISE EXCEPTION 'lt-lg10k- 테스트 회원이 이미 있습니다. 중복 적재하지 않습니다.';
    END IF;
    IF (SELECT count(*) FROM market_trading_days d CROSS JOIN ltlg_config c
        WHERE d.base_date BETWEEN c.week_start AND c.week_start + 6) <> 7 THEN
        RAISE EXCEPTION '현재 주차의 거래일 달력이 불완전합니다.';
    END IF;
    IF EXISTS (
        SELECT FROM weekly_leaderboards b CROSS JOIN ltlg_config c
        WHERE b.week_start = c.week_start
    ) THEN
        RAISE EXCEPTION '이미 확정된 주차입니다. 미확정 주차에서 실행하세요.';
    END IF;
END $$;

CREATE TEMP TABLE ltlg_days ON COMMIT DROP AS
SELECT d.base_date
FROM market_trading_days d CROSS JOIN ltlg_config c
WHERE d.base_date BETWEEN c.week_start AND c.week_start + 6
  AND d.trading AND d.collected_at IS NOT NULL
  AND (d.base_date + TIME '15:30') AT TIME ZONE 'Asia/Seoul' <= CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM ltlg_days) THEN
        RAISE EXCEPTION '현재 주차에 수집 완료된 종가가 없습니다.';
    END IF;
    IF EXISTS (
        SELECT FROM market_trading_days d CROSS JOIN ltlg_config c
        WHERE d.base_date BETWEEN c.week_start AND (SELECT max(base_date) FROM ltlg_days)
          AND d.trading AND d.collected_at IS NULL
    ) THEN
        RAISE EXCEPTION '현재 주차의 종가 수집 상태가 불완전합니다.';
    END IF;
END $$;

CREATE TEMP TABLE ltlg_assets ON COMMIT DROP AS
SELECT (row_number() OVER (ORDER BY a.id))::integer AS position,
    a.id AS asset_id, first_price.close_price AS opening_price
FROM (
    SELECT a.id FROM assets a
    WHERE a.category IN ('STOCK', 'ETF') AND a.listed AND a.market IN ('KOSPI', 'KOSDAQ')
      AND NOT EXISTS (
          SELECT FROM ltlg_days d
          WHERE NOT EXISTS (
              SELECT FROM stock_daily_prices p
              WHERE p.asset_id = a.id AND p.base_date = d.base_date AND p.close_price > 0
          )
      )
    ORDER BY a.id LIMIT 30
) a
JOIN stock_daily_prices first_price ON first_price.asset_id = a.id
  AND first_price.base_date = (SELECT min(base_date) FROM ltlg_days);

DO $$
BEGIN
    IF (SELECT count(*) FROM ltlg_assets) < 20 THEN
        RAISE EXCEPTION '해당 주차의 종가가 모두 있는 국내 상장 종목이 20개 미만입니다.';
    END IF;
END $$;

INSERT INTO members(provider, provider_id, email, nickname, role, status, created_at, updated_at)
SELECT 'GOOGLE', c.member_prefix || lpad(n.number::text, 5, '0'),
    c.member_prefix || lpad(n.number::text, 5, '0') || '@example.invalid',
    c.member_prefix || lpad(n.number::text, 5, '0'), 'USER', 'ACTIVE', now(), now()
FROM ltlg_config c CROSS JOIN generate_series(1, c.target_members) AS n(number);

-- 처음 적재한 부모 테이블의 통계를 FK 검사와 다음 대량 삽입 전에 갱신한다.
ANALYZE members;

INSERT INTO shared_portfolios(member_id, created_at, updated_at)
SELECT m.id, ((c.week_start - 3) + TIME '14:00') AT TIME ZONE 'Asia/Seoul', now()
FROM members m CROSS JOIN ltlg_config c
WHERE m.provider_id LIKE c.member_prefix || '%';

ANALYZE shared_portfolios;

INSERT INTO public_investor_profiles(member_id, public_id, display_name, bio,
    experience_level, risk_profile, profile_public, league_enabled, allocation_public,
    premium_detail_enabled, league_enabled_at, tracking_started_on, created_at, updated_at)
SELECT m.id, gen_random_uuid()::text, m.nickname, '투자리그 사용자 API 부하테스트용 가상 회원',
    'BEGINNER', 'BALANCED', true, true, true, true,
    ((c.week_start - 3) + TIME '15:00') AT TIME ZONE 'Asia/Seoul',
    (SELECT min(base_date) FROM ltlg_days), now(), now()
FROM members m CROSS JOIN ltlg_config c
WHERE m.provider_id LIKE c.member_prefix || '%';

ANALYZE public_investor_profiles;

CREATE TEMP TABLE ltlg_cohort ON COMMIT DROP AS
SELECT n.number, m.id AS member_id, s.id AS portfolio_id, p.id AS profile_id,
    p.public_id,
    CASE WHEN n.number > c.target_members - c.heavy_members THEN 20 ELSE 5 END AS holding_count,
    CASE WHEN n.number > c.target_members - c.heavy_members THEN 200 ELSE 20 END AS decision_count
FROM ltlg_config c CROSS JOIN generate_series(1, c.target_members) AS n(number)
JOIN members m ON m.provider_id = c.member_prefix || lpad(n.number::text, 5, '0')
JOIN shared_portfolios s ON s.member_id = m.id
JOIN public_investor_profiles p ON p.member_id = m.id;
ALTER TABLE ltlg_cohort ADD PRIMARY KEY (number);
ANALYZE ltlg_cohort;

INSERT INTO league_participations(profile_id, started_at)
SELECT p.profile_id, ((c.week_start - 3) + TIME '15:00') AT TIME ZONE 'Asia/Seoul'
FROM ltlg_cohort p CROSS JOIN ltlg_config c;

CREATE TEMP TABLE ltlg_holdings ON COMMIT DROP AS
SELECT c.number, c.portfolio_id, slot.number AS slot, a.asset_id,
    (10 + (c.number * 17 + slot.number * 13) % 91)::bigint AS quantity,
    a.opening_price
FROM ltlg_cohort c CROSS JOIN LATERAL generate_series(1, c.holding_count) AS slot(number)
JOIN ltlg_assets a ON a.position = 1 + (c.number * 7 + slot.number - 1) %
    (SELECT count(*)::integer FROM ltlg_assets);
ALTER TABLE ltlg_holdings ADD PRIMARY KEY (number, slot);
ANALYZE ltlg_holdings;

INSERT INTO shared_holdings(portfolio_id, asset_id, quantity, average_purchase_price,
    hidden, reason, created_at, updated_at)
SELECT h.portfolio_id, h.asset_id, h.quantity, h.opening_price, false,
    '부하테스트 보유 이유: 실적과 분산 투자 기준을 확인하며 보유',
    ((c.week_start - 3) + TIME '14:00') AT TIME ZONE 'Asia/Seoul', now()
FROM ltlg_holdings h CROSS JOIN ltlg_config c;

INSERT INTO shared_holding_histories(portfolio_id, asset_id, change_type, quantity,
    average_purchase_price, hidden, reason, created_at, updated_at)
SELECT h.portfolio_id, h.asset_id, 'ADDED', h.quantity, h.average_purchase_price,
    h.hidden, h.reason, h.created_at, h.created_at
FROM shared_holdings h JOIN ltlg_cohort c ON c.portfolio_id = h.portfolio_id;

INSERT INTO portfolio_transactions(portfolio_id, asset_id, request_key, transaction_type,
    quantity, unit_price, fee, traded_on, decision_reason, original_decision_reason,
    expected_holding_period, expected_change, invalidation_condition, maximum_acceptable_loss_rate,
    written_after_trade, status, created_at, updated_at)
SELECT c.portfolio_id, h.asset_id, 'lg10k:' || decision.number, 'HOLD', h.quantity,
    h.opening_price, 0, d.base_date, '부하테스트 투자 판단 ' || decision.number,
    '부하테스트 투자 판단 ' || decision.number, 'ONE_TO_THREE_MONTHS',
    '실적 성장 확인', '사업 가정이 달라지면 재검토', 10, false, 'ACTIVE',
    (d.base_date + TIME '09:00') AT TIME ZONE 'Asia/Seoul', now()
FROM ltlg_cohort c
CROSS JOIN LATERAL generate_series(1, c.decision_count) AS decision(number)
JOIN ltlg_holdings h ON h.number = c.number AND h.slot = 1 + (decision.number - 1) % c.holding_count
JOIN (SELECT base_date, row_number() OVER (ORDER BY base_date) AS position FROM ltlg_days) d
    ON d.position = 1 + (decision.number - 1) % (SELECT count(*) FROM ltlg_days);

ANALYZE portfolio_transactions;

INSERT INTO investment_reviews(transaction_id, actual_result, difference_from_expectation,
    next_action, created_at, updated_at)
SELECT t.id, '부하테스트 복기: 관찰한 결과', '예상과 실제를 비교한 가상 기록',
    '다음 실적과 위험 요인을 확인',
    (t.traded_on + TIME '17:00') AT TIME ZONE 'Asia/Seoul', now()
FROM portfolio_transactions t JOIN ltlg_cohort c ON c.portfolio_id = t.portfolio_id
-- 기존 요청 키의 형식과 무관하게 평가되도록 숫자 형변환 없이 테스트 키를 판별한다.
WHERE t.request_key ~ '^lg10k:[0-9]*[02468]$';

-- 1~1,000번은 쓰기 준비 계정이다. 원본 7종목 중 5개는 공유 중이고 2개는 새 가져오기 대상이다.
INSERT INTO portfolios(member_id, created_at, updated_at)
SELECT c.member_id, (p.week_start::timestamp AT TIME ZONE 'Asia/Seoul') - INTERVAL '1 day', now()
FROM ltlg_cohort c CROSS JOIN ltlg_config p WHERE c.number <= p.write_members;

ANALYZE portfolios;

CREATE TEMP TABLE ltlg_sources ON COMMIT DROP AS
SELECT c.number, slot.number AS slot, p.id AS portfolio_id, a.asset_id,
    (11 + (c.number * 17 + slot.number * 13) % 91)::bigint AS quantity,
    a.opening_price
FROM ltlg_cohort c CROSS JOIN ltlg_config config
CROSS JOIN generate_series(1, 7) AS slot(number)
JOIN portfolios p ON p.member_id = c.member_id
JOIN ltlg_assets a ON a.position = 1 + (c.number * 7 + slot.number - 1) %
    (SELECT count(*)::integer FROM ltlg_assets)
WHERE c.number <= config.write_members;
ALTER TABLE ltlg_sources ADD PRIMARY KEY (number, slot);

INSERT INTO portfolio_holdings(portfolio_id, asset_id, quantity, average_purchase_price,
    created_at, updated_at)
SELECT s.portfolio_id, s.asset_id, s.quantity, s.opening_price,
    (c.week_start::timestamp AT TIME ZONE 'Asia/Seoul') - INTERVAL '1 day', now()
FROM ltlg_sources s CROSS JOIN ltlg_config c;

INSERT INTO portfolio_holding_histories(portfolio_id, asset_id, change_type, quantity,
    average_purchase_price, effective_date, created_at, updated_at)
SELECT s.portfolio_id, s.asset_id, 'ADDED', s.quantity, s.opening_price, c.week_start - 1,
    (c.week_start::timestamp AT TIME ZONE 'Asia/Seoul') - INTERVAL '1 day',
    (c.week_start::timestamp AT TIME ZONE 'Asia/Seoul') - INTERVAL '1 day'
FROM ltlg_sources s CROSS JOIN ltlg_config c;

-- 쓰기 계정끼리 관계를 공유하지 않고, 읽기 대상 9,000명에 팔로우를 분산한다.
INSERT INTO investor_follows(follower_member_id, profile_id, created_at, updated_at)
SELECT c.member_id, target.profile_id,
    (p.week_start::timestamp AT TIME ZONE 'Asia/Seoul') - INTERVAL '1 day', now()
FROM ltlg_cohort c CROSS JOIN ltlg_config p CROSS JOIN generate_series(1, 20) AS f(number)
JOIN ltlg_cohort target ON target.number = p.write_members + 1 +
    ((c.number - 1) * 37 + f.number - 1) % (p.target_members - p.write_members)
WHERE c.number <= p.write_members;

WITH evaluations AS (
    SELECT h.portfolio_id, d.base_date,
        sum(h.quantity * p.close_price) AS evaluation,
        max(h.quantity * p.close_price) AS largest_position
    FROM ltlg_holdings h CROSS JOIN ltlg_days d
    JOIN stock_daily_prices p ON p.asset_id = h.asset_id AND p.base_date = d.base_date
    GROUP BY h.portfolio_id, d.base_date
), changes AS (
    SELECT *, lag(evaluation, 1, evaluation) OVER w AS previous_evaluation,
        lag(largest_position, 1, largest_position) OVER w AS previous_largest_position
    FROM evaluations WINDOW w AS (PARTITION BY portfolio_id ORDER BY base_date)
)
INSERT INTO portfolio_daily_returns(portfolio_id, base_date, daily_return_rate,
    starting_evaluation_amount, ending_evaluation_amount, max_holding_weight, current_holding_weight,
    eligible, tracking_baseline, calculation_version, calculated_at, created_at, updated_at)
SELECT portfolio_id, base_date, round((evaluation - previous_evaluation) * 100 / previous_evaluation, 4),
    round(previous_evaluation, 2), round(evaluation, 2),
    round(previous_largest_position * 100 / previous_evaluation, 2),
    round(largest_position * 100 / evaluation, 2), true,
    base_date = (SELECT min(base_date) FROM ltlg_days), 1, now(), now(), now()
FROM changes;

ANALYZE league_participations, shared_holdings, shared_holding_histories, investment_reviews,
    portfolio_daily_returns, investor_follows, portfolio_holdings, portfolio_holding_histories;

SELECT (SELECT week_start FROM ltlg_config) AS week_start,
    (SELECT max(base_date) FROM ltlg_days) AS as_of_date,
    (SELECT count(*) FROM ltlg_cohort) AS added_members,
    (SELECT count(*) FROM ltlg_holdings) AS shared_holdings,
    (SELECT count(*) FROM portfolio_transactions t JOIN ltlg_cohort c ON c.portfolio_id = t.portfolio_id) AS decisions,
    (SELECT count(*) FROM investment_reviews r JOIN portfolio_transactions t ON t.id = r.transaction_id
        JOIN ltlg_cohort c ON c.portfolio_id = t.portfolio_id) AS reviews,
    (SELECT count(*) FROM investor_follows f JOIN ltlg_cohort c ON c.member_id = f.follower_member_id) AS follows,
    (SELECT count(*) FROM ltlg_sources) AS source_holdings,
    (SELECT count(*) FROM portfolio_daily_returns r JOIN ltlg_cohort c ON c.portfolio_id = r.portfolio_id) AS daily_returns;

-- 가상 테스트 회원의 ID만 조회한다. 토큰은 발급하지 않으며 필요하면 결과를 별도 파일로 저장한다.
SELECT c.number AS account_number, c.member_id, c.public_id,
    h.asset_id AS shared_asset_id, s.asset_id AS new_import_asset_id,
    t.id AS transaction_id, r.id AS review_id,
    followed.public_id AS unfollow_public_id, available.public_id AS follow_public_id
FROM ltlg_cohort c CROSS JOIN ltlg_config p
JOIN ltlg_holdings h ON h.number = c.number AND h.slot = 1
JOIN ltlg_sources s ON s.number = c.number AND s.slot = 6
JOIN portfolio_transactions t ON t.portfolio_id = c.portfolio_id AND t.request_key = 'lg10k:2'
JOIN investment_reviews r ON r.transaction_id = t.id
JOIN ltlg_cohort followed ON followed.number = p.write_members + 1 +
    ((c.number - 1) * 37) % (p.target_members - p.write_members)
JOIN ltlg_cohort available ON available.number = p.write_members + 1 +
    ((c.number - 1) * 37 + 24) % (p.target_members - p.write_members)
WHERE c.number <= p.write_members
ORDER BY c.number;

COMMIT;
