-- DBeaver에서 개발 PostgreSQL 연결을 선택하고 이 파일 전체를 SQL 스크립트로 실행한다.
-- CLI: psql -v ON_ERROR_STOP=1 -f seed-league-rankings.sql
-- 순위 테스트 회원을 총 1,000명으로 맞춘다. 기존 100명이 있으면 900명만 추가한다.
-- 기존 회원·종목·종가·거래일·확정 순위는 변경하지 않는다.
-- 현재 주차의 수집 완료된 종가와 국내 상장 종목 3개 이상이 필요하다.
-- 이미 있는 lt601-rank- 회원은 유지하고 없는 번호만 추가한다. 모두 있으면 중단한다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE TEMP TABLE lt601_config ON COMMIT DROP AS
SELECT date_trunc('week', CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date AS week_start,
    1000 AS target_members;
-- 다른 주차 검증이 필요한 경우에만 아래 날짜를 바꿔 주석을 해제한다.
-- UPDATE lt601_config SET week_start = DATE '2026-10-19';

CREATE TEMP TABLE lt601_days ON COMMIT DROP AS
SELECT d.base_date
FROM market_trading_days d CROSS JOIN lt601_config c
WHERE d.base_date BETWEEN c.week_start AND c.week_start + 6
  AND d.trading AND d.collected_at IS NOT NULL
  AND (d.base_date + TIME '15:30') AT TIME ZONE 'Asia/Seoul' <= CURRENT_TIMESTAMP;

DO $$
BEGIN
    IF NOT pg_try_advisory_xact_lock(601, 1) THEN
        RAISE EXCEPTION '다른 #601 데이터 적재가 진행 중입니다.';
    END IF;
    IF NOT EXISTS (
        SELECT FROM generate_series(1, (SELECT target_members FROM lt601_config)) AS n(number)
        WHERE NOT EXISTS (
            SELECT FROM members m
            WHERE m.provider_id = 'lt601-rank-' ||
                lpad(n.number::text, greatest(3, length(n.number::text)), '0')
        )
    ) THEN
        RAISE EXCEPTION '대상 순위 테스트 회원이 이미 있습니다. 중복 적재하지 않습니다.';
    END IF;
    IF NOT EXISTS (SELECT FROM lt601_days) THEN
        RAISE EXCEPTION '이 주차의 수집 완료된 거래일이 없습니다. 종가 수집 후 실행하세요.';
    END IF;
    IF (SELECT count(*) FROM market_trading_days d CROSS JOIN lt601_config c
        WHERE d.base_date BETWEEN c.week_start AND c.week_start + 6) <> 7
       OR EXISTS (
           SELECT FROM market_trading_days d CROSS JOIN lt601_config c
           WHERE d.base_date BETWEEN c.week_start AND (SELECT max(base_date) FROM lt601_days)
             AND d.trading AND d.collected_at IS NULL
       ) THEN
        RAISE EXCEPTION '주간 거래일 달력 또는 종가 수집 상태가 불완전합니다.';
    END IF;
    IF EXISTS (
        SELECT FROM weekly_leaderboards b CROSS JOIN lt601_config c
        WHERE b.week_start = c.week_start AND b.league_type = 'WEEKLY_RETURN'
    ) THEN
        RAISE EXCEPTION '이미 확정된 주차입니다. 미확정 주차에서 실행하세요.';
    END IF;
END $$;

CREATE TEMP TABLE lt601_assets ON COMMIT DROP AS
SELECT a.id, first_price.close_price AS opening_price
FROM assets a
JOIN stock_daily_prices first_price ON first_price.asset_id = a.id
  AND first_price.base_date = (SELECT min(base_date) FROM lt601_days)
WHERE a.category IN ('STOCK', 'ETF') AND a.listed AND a.market IN ('KOSPI', 'KOSDAQ')
  AND NOT EXISTS (
      SELECT FROM lt601_days d
      WHERE NOT EXISTS (
          SELECT FROM stock_daily_prices p
          WHERE p.asset_id = a.id AND p.base_date = d.base_date AND p.close_price > 0
      )
  )
ORDER BY a.id
LIMIT 30;

DO $$
BEGIN
    IF (SELECT count(*) FROM lt601_assets) < 3 THEN
        RAISE EXCEPTION '해당 주차의 종가가 모두 있는 국내 상장 종목이 3개 미만입니다.';
    END IF;
END $$;

SELECT setseed(0.601);
CREATE TEMP TABLE lt601_cohort (
    number integer PRIMARY KEY,
    member_id bigint NOT NULL,
    portfolio_id bigint NOT NULL
) ON COMMIT DROP;

DO $$
<<seed>>
DECLARE
    week_start date := (SELECT c.week_start FROM lt601_config c);
    first_day date := (SELECT min(base_date) FROM lt601_days);
    -- 온전한 주간 순위에 들어가도록 전주 금요일 국내 장 마감 전에 참여시킨다.
    enrolled_at timestamptz := ((week_start - 3) + TIME '15:00') AT TIME ZONE 'Asia/Seoul';
    imported_at timestamptz := enrolled_at - INTERVAL '1 day';
    member_number integer;
    member_id bigint;
    profile_id bigint;
    portfolio_id bigint;
    decision_id bigint;
    decision_number integer;
    name text;
    asset record;
    holding record;
BEGIN
    FOR member_number IN 1..(SELECT target_members FROM lt601_config) LOOP
        name := 'lt601-rank-' ||
            lpad(member_number::text, greatest(3, length(member_number::text)), '0');
        IF EXISTS (SELECT FROM members m WHERE m.provider_id = seed.name) THEN
            CONTINUE;
        END IF;
        INSERT INTO members(provider, provider_id, email, nickname, role, status, created_at, updated_at)
        VALUES ('GOOGLE', name, name || '@example.invalid', name, 'USER', 'ACTIVE', now(), now())
        RETURNING id INTO member_id;

        INSERT INTO public_investor_profiles(member_id, public_id, display_name, bio,
            experience_level, risk_profile, profile_public, league_enabled, allocation_public,
            premium_detail_enabled, league_enabled_at, tracking_started_on, created_at, updated_at)
        VALUES (member_id, gen_random_uuid()::text, name, '#601 순위 부하테스트 데이터',
            (ARRAY['BEGINNER','ONE_TO_THREE_YEARS','OVER_THREE_YEARS'])[1 + member_number % 3],
            (ARRAY['CONSERVATIVE','BALANCED','AGGRESSIVE'])[1 + member_number % 3],
            true, true, true, true, enrolled_at, first_day, now(), now())
        RETURNING id INTO profile_id;

        INSERT INTO league_participations(profile_id, started_at) VALUES (profile_id, enrolled_at);
        INSERT INTO shared_portfolios(member_id, created_at, updated_at)
        VALUES (member_id, imported_at, now()) RETURNING id INTO portfolio_id;
        INSERT INTO lt601_cohort VALUES (member_number, member_id, portfolio_id);

        FOR asset IN SELECT * FROM lt601_assets ORDER BY random() LIMIT 3 LOOP
            INSERT INTO shared_holdings(portfolio_id, asset_id, quantity, average_purchase_price,
                hidden, reason, created_at, updated_at)
            VALUES (portfolio_id, asset.id, 10 + floor(random() * 91)::integer, asset.opening_price,
                false, '#601 테스트 보유 종목', imported_at, now());
        END LOOP;

        INSERT INTO shared_holding_histories(portfolio_id, asset_id, change_type, quantity,
            average_purchase_price, hidden, reason, created_at, updated_at)
        SELECT h.portfolio_id, h.asset_id, 'ADDED', h.quantity, h.average_purchase_price,
            h.hidden, h.reason, imported_at, imported_at
        FROM shared_holdings h WHERE h.portfolio_id = seed.portfolio_id;

        FOR decision_number IN 1..10 LOOP
            SELECT h.* INTO holding FROM shared_holdings h
            WHERE h.portfolio_id = seed.portfolio_id ORDER BY random() LIMIT 1;
            INSERT INTO portfolio_transactions(portfolio_id, asset_id, request_key, transaction_type,
                quantity, unit_price, fee, traded_on, decision_reason, expected_holding_period,
                expected_change, invalidation_condition, maximum_acceptable_loss_rate,
                written_after_trade, status, created_at, updated_at)
            VALUES (portfolio_id, holding.asset_id, 'lt601-rank-' || decision_number, 'HOLD',
                holding.quantity, holding.average_purchase_price, 0, first_day,
                '#601 테스트 투자 판단 ' || decision_number, 'ONE_TO_THREE_MONTHS',
                '실적 성장 기대', '실적 악화 시 재검토', 10, false, 'ACTIVE',
                (first_day + TIME '09:00') AT TIME ZONE 'Asia/Seoul', now())
            RETURNING id INTO decision_id;

            IF random() < 0.6 THEN
                INSERT INTO investment_reviews(transaction_id, actual_result,
                    difference_from_expectation, next_action, created_at, updated_at)
                VALUES (decision_id, '#601 테스트 복기', '기대와 실제 비교', '다음 실적 확인', now(), now());
            END IF;
        END LOOP;
    END LOOP;
END $$;

-- 보유 수량과 실제 종가로 일별 수익률을 계산해 재집계 시에도 같은 투자 구성을 유지한다.
WITH evaluations AS (
    SELECT h.portfolio_id, d.base_date,
        sum(h.quantity * p.close_price) AS evaluation,
        max(h.quantity * p.close_price) AS largest_position
    FROM lt601_cohort c
    JOIN shared_holdings h ON h.portfolio_id = c.portfolio_id
    CROSS JOIN lt601_days d
    JOIN stock_daily_prices p ON p.asset_id = h.asset_id AND p.base_date = d.base_date
    GROUP BY h.portfolio_id, d.base_date
), changes AS (
    SELECT *,
        lag(evaluation, 1, evaluation) OVER w AS previous_evaluation,
        lag(largest_position, 1, largest_position) OVER w AS previous_largest_position
    FROM evaluations
    WINDOW w AS (PARTITION BY portfolio_id ORDER BY base_date)
)
INSERT INTO portfolio_daily_returns(portfolio_id, base_date, daily_return_rate,
    starting_evaluation_amount, ending_evaluation_amount, max_holding_weight, current_holding_weight,
    eligible, tracking_baseline, calculation_version, calculated_at, created_at, updated_at)
SELECT portfolio_id, base_date, round((evaluation - previous_evaluation) * 100 / previous_evaluation, 4),
    round(previous_evaluation, 2), round(evaluation, 2),
    round(previous_largest_position * 100 / previous_evaluation, 2),
    round(largest_position * 100 / evaluation, 2), true,
    base_date = (SELECT min(base_date) FROM lt601_days), 1, now(), now(), now()
FROM changes;

-- 추가된 데이터 규모를 다음 조회의 실행계획에 반영한다.
ANALYZE members, public_investor_profiles, league_participations, shared_portfolios,
    shared_holdings, shared_holding_histories, portfolio_transactions, investment_reviews,
    portfolio_daily_returns;

SELECT (SELECT week_start FROM lt601_config) AS week_start,
    (SELECT max(base_date) FROM lt601_days) AS as_of_date,
    (SELECT count(*) FROM lt601_cohort) AS added_members,
    (SELECT count(*) FROM members m
        WHERE m.provider_id LIKE 'lt601-rank-%') AS total_test_members,
    (SELECT count(*) FROM shared_holdings h JOIN lt601_cohort c ON c.portfolio_id = h.portfolio_id) AS holdings,
    (SELECT count(*) FROM portfolio_transactions t JOIN lt601_cohort c ON c.portfolio_id = t.portfolio_id) AS decisions,
    (SELECT count(*) FROM investment_reviews r JOIN portfolio_transactions t ON t.id = r.transaction_id
        JOIN lt601_cohort c ON c.portfolio_id = t.portfolio_id) AS reviews,
    (SELECT count(*) FROM portfolio_daily_returns r JOIN lt601_cohort c ON c.portfolio_id = r.portfolio_id) AS daily_returns;

COMMIT;
