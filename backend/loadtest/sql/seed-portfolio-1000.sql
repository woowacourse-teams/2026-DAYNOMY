-- dev PostgreSQL / DBeaver: dev 연결을 선택하고 파일 전체를 SQL 스크립트로 실행한다.
-- 측정 1,000명(400/400/200) + 워밍업 3명. 기존 lt496 계정과 겹치지 않는다.
-- 기존 실제 종목/종가/환율을 재사용한다. 부족하면 회원 생성 전에 중단한다.
-- 중간 오류가 나면 ROLLBACK; 후 원인을 확인한다. 같은 namespace 재실행은 거절한다.
-- access token은 이 SQL로 발급하지 않는다. 마지막 SELECT는 토큰 발급용 회원 목록이다.
BEGIN;
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SET LOCAL TIME ZONE 'Asia/Seoul';
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '20min';

-- 기준일을 변경할 때는 이 한 줄만 수정한다. 종가/환율은 기준일 직전까지 필요하다.
CREATE TEMP TABLE lt1000_config ON COMMIT DROP AS
SELECT as_of, 'lt1000-' || to_char(as_of, 'YYYYMMDD') AS namespace
FROM (VALUES (DATE '2026-10-29')) AS settings(as_of);
SELECT setseed(0.1000);

DO $$
BEGIN
    IF NOT pg_try_advisory_xact_lock(1000, 1) THEN
        RAISE EXCEPTION '다른 1,000명 데이터 적재가 진행 중입니다.';
    END IF;
    IF (SELECT as_of > (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date
        FROM lt1000_config) THEN
        RAISE EXCEPTION '기준일은 서울 기준 오늘 이후일 수 없습니다.';
    END IF;
    IF EXISTS (SELECT FROM members m CROSS JOIN lt1000_config c
               WHERE m.provider_id LIKE c.namespace || '-%') THEN
        RAISE EXCEPTION '같은 기준일의 테스트 회원이 이미 있습니다. 기존 데이터를 삭제하지 않습니다.';
    END IF;
END $$;

-- 국가별 실제 가격 날짜. 서로 다른 국내/미국 휴장일을 하나의 달력으로 강제하지 않는다.
CREATE TEMP TABLE lt1000_days ON COMMIT DROP AS
SELECT DISTINCT CASE WHEN a.market IN ('KOSPI', 'KOSDAQ') THEN 'KR' ELSE 'US' END AS country,
       p.base_date
FROM stock_daily_prices p JOIN assets a ON a.id = p.asset_id CROSS JOIN lt1000_config c
WHERE a.listed AND a.category IN ('STOCK', 'ETF')
  AND a.market IN ('KOSPI', 'KOSDAQ', 'NASDAQ', 'NYSE', 'NYSE_ARCA', 'NYSE_AMERICAN', 'BATS')
  AND p.base_date >= (c.as_of - INTERVAL '1 year' - INTERVAL '14 days')::date
  AND p.base_date < c.as_of;
CREATE UNIQUE INDEX ON lt1000_days(country, base_date);

-- 국가별 가격 날짜를 모두 보유한 실제 상장 종목만 선택한다(국가별 최대 60개).
-- 종목 종류별 순번을 번갈아 선택해, 적격 ETF가 있으면 주식과 함께 사용한다.
CREATE TEMP TABLE lt1000_assets ON COMMIT DROP AS
WITH eligible AS (
    SELECT a.id AS asset_id, a.asset_code, a.category,
           CASE WHEN a.market IN ('KOSPI', 'KOSDAQ') THEN 'KR' ELSE 'US' END AS country
    FROM assets a
    WHERE a.listed AND a.category IN ('STOCK', 'ETF')
      AND a.market IN ('KOSPI', 'KOSDAQ', 'NASDAQ', 'NYSE', 'NYSE_ARCA', 'NYSE_AMERICAN', 'BATS')
      AND NOT EXISTS (
          SELECT FROM lt1000_days d
          WHERE d.country = CASE WHEN a.market IN ('KOSPI', 'KOSDAQ') THEN 'KR' ELSE 'US' END
            AND NOT EXISTS (SELECT FROM stock_daily_prices p
                            WHERE p.asset_id = a.id AND p.base_date = d.base_date)
      )
), ranked AS (
    SELECT *, row_number() OVER (PARTITION BY country, category ORDER BY asset_code, asset_id) AS kind_rank
    FROM eligible
), selected AS (
    SELECT *, row_number() OVER (PARTITION BY country ORDER BY kind_rank, category, asset_id) AS selection
    FROM ranked
)
SELECT asset_id, country, category FROM selected WHERE selection <= 60;
CREATE UNIQUE INDEX ON lt1000_assets(asset_id);

-- 누락 진단 결과. 이 SELECT에서 날짜가 표시되면 실제 환율 수집이 먼저 필요하다.
SELECT p.base_date AS missing_usd_krw_date
FROM stock_daily_prices p JOIN lt1000_assets a ON a.asset_id = p.asset_id
CROSS JOIN lt1000_config c
LEFT JOIN usd_krw_rates r ON r.requested_date = p.base_date
WHERE a.country = 'US' AND p.base_date >= (c.as_of - INTERVAL '1 year' - INTERVAL '14 days')::date
  AND p.base_date < c.as_of AND r.requested_date IS NULL
GROUP BY p.base_date ORDER BY p.base_date;

DO $$
DECLARE
    nation text;
    cutoff date := (SELECT as_of FROM lt1000_config);
BEGIN
    FOREACH nation IN ARRAY ARRAY['KR', 'US'] LOOP
        IF (SELECT count(*) FROM lt1000_days WHERE country = nation
            AND base_date >= (cutoff - INTERVAL '1 year')::date) < 200
           OR (SELECT min(base_date) FROM lt1000_days WHERE country = nation)
              > (cutoff - INTERVAL '1 year')::date
           OR (SELECT max(base_date) FROM lt1000_days WHERE country = nation) < cutoff - 7 THEN
            RAISE EXCEPTION '%: 최근 1년/직전 거래일/최근 종가가 부족합니다. 실제 가격 수집 후 실행하세요.', nation;
        END IF;
        IF (SELECT count(*) FROM lt1000_assets WHERE country = nation) < 30 THEN
            RAISE EXCEPTION '%: 해당 기간의 가격이 완전한 상장 종목이 30개 미만입니다.', nation;
        END IF;
    END LOOP;
    IF EXISTS (
        SELECT FROM lt1000_days d LEFT JOIN usd_krw_rates r ON r.requested_date = d.base_date
        WHERE d.country = 'US' AND r.requested_date IS NULL
    ) THEN
        RAISE EXCEPTION '미국 가격 날짜의 환율이 부족합니다. 앞의 누락 날짜 결과를 확인하세요.';
    END IF;
END $$;

-- 이후 계산은 같은 트랜잭션에서 읽은 가격/환율만 사용한다.
CREATE TEMP TABLE lt1000_quotes ON COMMIT DROP AS
SELECT a.asset_id, a.country, p.base_date, p.close_price,
       CASE WHEN a.country = 'US' THEN r.rate ELSE 1::numeric END AS exchange_rate,
       p.close_price * CASE WHEN a.country = 'US' THEN r.rate ELSE 1::numeric END AS close_krw
FROM lt1000_assets a JOIN stock_daily_prices p ON p.asset_id = a.asset_id
CROSS JOIN lt1000_config c LEFT JOIN usd_krw_rates r ON r.requested_date = p.base_date
WHERE p.base_date >= (c.as_of - INTERVAL '1 year' - INTERVAL '14 days')::date
  AND p.base_date < c.as_of;
CREATE UNIQUE INDEX ON lt1000_quotes(asset_id, base_date);
ANALYZE lt1000_quotes;

CREATE TEMP TABLE lt1000_members (
    number integer PRIMARY KEY, profile text, country text, member_id bigint,
    portfolio_id bigint, holding_count integer, trade_count integer
) ON COMMIT DROP;
CREATE TEMP TABLE lt1000_positions (
    portfolio_id bigint, asset_id bigint, quantity bigint, average_price numeric(19,2),
    PRIMARY KEY(portfolio_id, asset_id)
) ON COMMIT DROP;
CREATE TEMP TABLE lt1000_versions (
    portfolio_id bigint, asset_id bigint, effective_date date, sort_order integer,
    quantity bigint, average_price numeric(19,2)
) ON COMMIT DROP;

DO $$
<<seed>>
DECLARE
    config record := (SELECT c FROM lt1000_config c);
    n integer;
    group_name text;
    nation text;
    holdings integer;
    trades integer;
    member_key bigint;
    portfolio_key bigint;
    selected_ids bigint[];
    asset_key bigint;
    opening date;
    trade_days date[];
    quantity_before bigint;
    average_before numeric;
    trade_quantity bigint;
    quantity_after bigint;
    average_after numeric;
    native_price numeric;
    applied_rate numeric;
    commission numeric;
    levy numeric;
    kind text;
    stamp timestamptz;
    quote record;
    trade record;
BEGIN
    FOR n IN 1..1003 LOOP
        group_name := CASE WHEN n = 1001 THEN 'light' WHEN n = 1002 THEN 'standard'
                           WHEN n = 1003 THEN 'heavy' WHEN n % 5 IN (1,3) THEN 'light'
                           WHEN n % 5 IN (2,4) THEN 'standard' ELSE 'heavy' END;
        nation := CASE n % 3 WHEN 1 THEN 'KR' WHEN 2 THEN 'US' ELSE 'MIXED' END;
        holdings := CASE group_name WHEN 'light' THEN 5 WHEN 'standard' THEN 15 ELSE 30 END;
        trades := CASE group_name WHEN 'light' THEN 50 WHEN 'standard' THEN 200 ELSE 800 END;
        opening := (config.as_of - INTERVAL '1 year' - INTERVAL '21 days')::date;

        INSERT INTO members(provider, provider_id, email, nickname, role, status, created_at, updated_at)
        VALUES ('GOOGLE', config.namespace || '-' || n, config.namespace || '-' || n || '@example.invalid',
                config.namespace || '-' || n, 'USER', 'ACTIVE', opening::timestamptz, CURRENT_TIMESTAMP)
        RETURNING id INTO member_key;
        INSERT INTO portfolios(member_id, created_at, updated_at)
        VALUES (member_key, opening::timestamptz, CURRENT_TIMESTAMP) RETURNING id INTO portfolio_key;

        SELECT array_agg(chosen.asset_id ORDER BY chosen.asset_id) INTO selected_ids FROM (
            (SELECT a.asset_id FROM lt1000_assets a WHERE a.country = 'KR'
             ORDER BY md5(a.asset_id::text || ':' || n)
             LIMIT CASE nation WHEN 'KR' THEN holdings WHEN 'US' THEN 0 ELSE (holdings + 1) / 2 END)
            UNION ALL
            (SELECT a.asset_id FROM lt1000_assets a WHERE a.country = 'US'
             ORDER BY md5(a.asset_id::text || ':' || n)
             LIMIT CASE nation WHEN 'US' THEN holdings WHEN 'KR' THEN 0 ELSE holdings / 2 END)
        ) chosen;

        FOREACH asset_key IN ARRAY selected_ids LOOP
            SELECT q.* INTO STRICT quote FROM lt1000_quotes q WHERE q.asset_id = asset_key
            ORDER BY q.base_date LIMIT 1;
            quantity_before := 100 + floor(random() * 101)::bigint;
            average_before := round(quote.close_krw * (0.9 + random()::numeric * 0.2), 2);
            INSERT INTO portfolio_holding_histories
                (portfolio_id, asset_id, change_type, quantity, average_purchase_price,
                 effective_date, fee, tax, exchange_rate, created_at, updated_at)
            VALUES (portfolio_key, asset_key, 'OPENING_BALANCE', quantity_before, average_before,
                    opening, 0, 0, 1, opening::timestamptz, opening::timestamptz);
            INSERT INTO lt1000_positions VALUES (portfolio_key, asset_key, quantity_before, average_before);
            INSERT INTO lt1000_versions VALUES (portfolio_key, asset_key, opening, 0, quantity_before, average_before);
        END LOOP;

        -- 회원별 거래를 날짜·순번 순서로 생성하고 종목 자체의 실제 가격 날짜를 사용한다.
        SELECT array_agg(d.base_date ORDER BY d.base_date) INTO trade_days
        FROM (SELECT DISTINCT q.base_date FROM lt1000_quotes q
              WHERE q.asset_id = ANY(selected_ids)
                AND q.base_date >= (config.as_of - INTERVAL '1 year')::date) d;
        FOR trade IN
            SELECT row_number() OVER (ORDER BY day, tie)::integer AS sequence, day
            FROM (SELECT trade_days[least(array_length(trade_days,1),
                         1 + floor(sqrt(random()) * array_length(trade_days,1))::integer)] AS day,
                         g AS tie FROM generate_series(1, trades) g) generated
            ORDER BY day, tie
        LOOP
            -- 휴장일이 다른 국가의 날짜이면 해당 날짜에 가격이 있는 보유 종목을 선택한다.
            SELECT q.* INTO STRICT quote FROM lt1000_quotes q
            WHERE q.asset_id = ANY(selected_ids) AND q.base_date = trade.day
            ORDER BY md5(q.asset_id::text || ':' || n || ':' || trade.sequence) LIMIT 1;
            asset_key := quote.asset_id;
            SELECT p.quantity, p.average_price INTO STRICT quantity_before, average_before
            FROM lt1000_positions p WHERE p.portfolio_id = portfolio_key AND p.asset_id = asset_key;
            kind := CASE WHEN quantity_before <= 1 OR random() < 0.65 THEN 'BUY' ELSE 'SELL' END;
            trade_quantity := CASE WHEN kind = 'BUY' THEN 1 + floor(random() * 20)::bigint
                                  ELSE 1 + floor(random() * least(quantity_before - 1, 20))::bigint END;
            native_price := round(quote.close_price * (0.97 + random()::numeric * 0.06),
                                  CASE WHEN quote.country = 'US' THEN 4 ELSE 2 END);
            applied_rate := quote.exchange_rate;
            commission := round(native_price * trade_quantity * 0.00015, 2);
            levy := CASE WHEN kind = 'SELL' THEN round(native_price * trade_quantity * 0.0015,2) ELSE 0 END;
            quantity_after := quantity_before + CASE kind WHEN 'BUY' THEN trade_quantity ELSE -trade_quantity END;
            average_after := CASE kind WHEN 'BUY' THEN round(
                (average_before * quantity_before +
                 (native_price * trade_quantity + commission + levy) * applied_rate) / quantity_after, 2)
                ELSE average_before END;
            stamp := (trade.day::timestamp AT TIME ZONE 'Asia/Seoul') + INTERVAL '9 hours'
                     + trade.sequence * INTERVAL '1 millisecond';
            INSERT INTO portfolio_holding_histories
                (portfolio_id, asset_id, change_type, quantity, average_purchase_price, effective_date,
                 transaction_quantity, unit_price, fee, tax, exchange_rate, created_at, updated_at)
            VALUES (portfolio_key, asset_key, kind, quantity_before, average_before, trade.day,
                    trade_quantity, native_price, commission, levy, applied_rate, stamp, stamp);
            UPDATE lt1000_positions p SET quantity = quantity_after, average_price = average_after
            WHERE p.portfolio_id = portfolio_key AND p.asset_id = asset_key;
            INSERT INTO lt1000_versions VALUES
                (portfolio_key, asset_key, trade.day, trade.sequence, quantity_after, average_after);
        END LOOP;
        INSERT INTO lt1000_members VALUES (n, group_name, nation, member_key, portfolio_key, holdings, trades);
    END LOOP;
END $$;

INSERT INTO portfolio_holdings
    (portfolio_id, asset_id, quantity, average_purchase_price, created_at, updated_at)
SELECT portfolio_id, asset_id, quantity, average_price, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM lt1000_positions;

-- 각 종목의 일말 보유 상태를 유효 기간으로 바꿔 날짜마다 전체 원장을 다시 스캔하지 않는다.
CREATE TEMP TABLE lt1000_intervals ON COMMIT DROP AS
WITH end_of_day AS (
    SELECT DISTINCT ON (portfolio_id, asset_id, effective_date) * FROM lt1000_versions
    ORDER BY portfolio_id, asset_id, effective_date, sort_order DESC
)
SELECT *, lead(effective_date, 1, (SELECT as_of FROM lt1000_config))
          OVER (PARTITION BY portfolio_id, asset_id ORDER BY effective_date) AS until_date
FROM end_of_day;
CREATE INDEX ON lt1000_intervals(portfolio_id, asset_id, effective_date);

CREATE TEMP TABLE lt1000_snapshot_days ON COMMIT DROP AS
SELECT portfolio_id, base_date,
       lag(base_date) OVER (PARTITION BY portfolio_id ORDER BY base_date) AS previous_date
FROM (SELECT DISTINCT p.portfolio_id, q.base_date
      FROM lt1000_positions p JOIN lt1000_quotes q ON q.asset_id = p.asset_id) dates;
CREATE UNIQUE INDEX ON lt1000_snapshot_days(portfolio_id, base_date);
ANALYZE lt1000_intervals;
ANALYZE lt1000_snapshot_days;

-- 앱과 동일하게 오늘/직전 기준일의 가격이 모든 보유 종목에 있는 날짜만 평가한다.
-- 혼합 포트폴리오에서 국가별 휴장일의 가격을 임의로 이월하지 않는다.
WITH amounts AS (
    SELECT d.portfolio_id, d.base_date, d.previous_date,
           round(sum(v.quantity * v.average_price), 2) AS purchase,
           round(sum(v.quantity * today.close_krw), 2) AS evaluation,
           round(sum(v.quantity * previous.close_krw), 2) AS previous_evaluation
    FROM lt1000_intervals v
    JOIN lt1000_snapshot_days d ON d.portfolio_id = v.portfolio_id
         AND d.base_date >= v.effective_date AND d.base_date < v.until_date
    CROSS JOIN lt1000_config c
    LEFT JOIN lt1000_quotes today ON today.asset_id = v.asset_id AND today.base_date = d.base_date
    LEFT JOIN lt1000_quotes previous ON previous.asset_id = v.asset_id AND previous.base_date = d.previous_date
    WHERE v.quantity > 0 AND d.base_date >= (c.as_of - INTERVAL '1 year')::date
    GROUP BY d.portfolio_id, d.base_date, d.previous_date
    HAVING count(today.close_krw) = count(*)
       AND (d.previous_date IS NULL OR count(previous.close_krw) = count(*))
)
INSERT INTO portfolio_daily_snapshots
    (portfolio_id, base_date, total_purchase_amount, total_evaluation_amount,
     total_profit_loss, total_return_rate, daily_profit_loss, daily_return_rate, created_at, updated_at)
SELECT portfolio_id, base_date, purchase, evaluation, evaluation - purchase,
       CASE WHEN purchase = 0 THEN 0 ELSE round((evaluation - purchase) * 100 / purchase, 2) END,
       CASE WHEN previous_date IS NOT NULL THEN evaluation - previous_evaluation END,
       CASE WHEN previous_date IS NOT NULL THEN
           CASE WHEN previous_evaluation = 0 THEN 0
                ELSE round((evaluation - previous_evaluation) * 100 / previous_evaluation, 2) END END,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM amounts;

DO $$
BEGIN
    IF (SELECT count(*) FROM lt1000_members) <> 1003 THEN
        RAISE EXCEPTION '회원 수 검증 실패';
    END IF;
    IF EXISTS (
        SELECT FROM lt1000_members m
        WHERE (SELECT count(*) FROM portfolio_holdings h WHERE h.portfolio_id = m.portfolio_id) <> m.holding_count
           OR (SELECT count(*) FROM portfolio_holding_histories h WHERE h.portfolio_id = m.portfolio_id
               AND h.change_type IN ('BUY','SELL')) <> m.trade_count
           OR (SELECT count(*) FROM portfolio_daily_snapshots s WHERE s.portfolio_id = m.portfolio_id) < 150
    ) THEN
        RAISE EXCEPTION '보유/거래 건수 또는 1년 스냅샷 범위 검증 실패';
    END IF;
    IF EXISTS (
        SELECT FROM portfolio_holding_histories h JOIN lt1000_members m ON m.portfolio_id = h.portfolio_id
        GROUP BY h.portfolio_id, h.asset_id
        HAVING sum(CASE h.change_type WHEN 'OPENING_BALANCE' THEN h.quantity
                         WHEN 'BUY' THEN h.transaction_quantity WHEN 'SELL' THEN -h.transaction_quantity ELSE 0 END)
            <> (SELECT p.quantity FROM lt1000_positions p
                WHERE p.portfolio_id = h.portfolio_id AND p.asset_id = h.asset_id)
    ) THEN
        RAISE EXCEPTION '원장과 현재 보유 수량 불일치';
    END IF;
END $$;

-- 측정 회원과 워밍업을 구분한 최종 집계.
SELECT CASE WHEN m.number <= 1000 THEN 'measure' ELSE 'warmup' END AS phase,
       m.profile, m.country, count(*) AS members,
       sum(m.holding_count) AS holdings, sum(m.trade_count) AS buy_sell_histories,
       sum(m.holding_count + m.trade_count) AS all_histories
FROM lt1000_members m GROUP BY phase, m.profile, m.country ORDER BY phase, m.profile, m.country;

-- 비밀값 없는 계정 매핑. 토큰 발급 시 이 member_id와 서버의 JWT 설정을 사용한다.
SELECT m.number AS test_number, m.profile, m.country, m.member_id, m.portfolio_id,
       CASE WHEN m.number <= 1000 THEN 'measure' ELSE 'warmup' END AS phase,
       (SELECT count(*) FROM portfolio_daily_snapshots s WHERE s.portfolio_id = m.portfolio_id) AS snapshots
FROM lt1000_members m ORDER BY m.number;

COMMIT;
