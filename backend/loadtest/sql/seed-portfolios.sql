-- dev DB에서 전체를 한 번에 실행한다. 기존 종목/가격은 조회만 한다.
-- 측정 회원 50명(소규모 20 / 보통 20 / 다수 거래 10) + 워밍업 51~53번(그룹별 1명).
-- 같은 날짜의 테스트 회원이 이미 있으면 중단하며, 기존 데이터를 삭제하지 않는다.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

CREATE TEMP TABLE lt_config ON COMMIT DROP AS
SELECT (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Seoul')::date AS as_of;

-- 오늘/1개월 전/6개월 전 테스트 모두 이 기준일을 사용한다.
-- 다른 기준일이 필요하면 아래 UPDATE의 주석을 해제한다.
-- UPDATE lt_config SET as_of = DATE '2026-10-15';
SELECT setseed(0.496);

CREATE TEMP TABLE lt_price_days ON COMMIT DROP AS
WITH dates AS (
    SELECT DISTINCT base_date
    FROM stock_daily_prices
    WHERE base_date < (SELECT as_of FROM lt_config)
), ordered AS (
    SELECT base_date, lag(base_date) OVER (ORDER BY base_date) AS previous_date
    FROM dates
)
SELECT * FROM ordered
WHERE base_date >= (SELECT (as_of - INTERVAL '1 year')::date FROM lt_config);

CREATE TEMP TABLE lt_days ON COMMIT DROP AS
SELECT * FROM lt_price_days
WHERE base_date >= (SELECT (as_of - INTERVAL '6 months')::date FROM lt_config);

-- 같은 날짜에 가격이 있는 실제 종목만 사용한다. 가격 데이터는 생성하지 않는다.
CREATE TEMP TABLE lt_assets ON COMMIT DROP AS
WITH required_dates AS (
    SELECT base_date FROM lt_price_days
    UNION
    SELECT previous_date FROM lt_price_days WHERE previous_date IS NOT NULL
)
SELECT a.id AS asset_id, row_number() OVER (ORDER BY a.asset_code, a.id) AS popularity
FROM assets a
WHERE a.category = 'STOCK'
  AND NOT EXISTS (
      SELECT FROM required_dates d
      WHERE NOT EXISTS (
          SELECT FROM stock_daily_prices p
          WHERE p.asset_id = a.id AND p.base_date = d.base_date
      )
  )
ORDER BY a.asset_code, a.id
LIMIT 100;

DO $$
DECLARE
    prefix text := 'lt496-' || (SELECT to_char(as_of, 'YYYYMMDD') FROM lt_config);
BEGIN
    IF NOT pg_try_advisory_xact_lock(496, 1) THEN
        RAISE EXCEPTION '다른 #496 데이터 적재가 진행 중입니다.';
    END IF;
    IF EXISTS (SELECT FROM members WHERE provider_id LIKE prefix || '-%') THEN
        RAISE EXCEPTION '같은 기준일의 #496 데이터가 이미 있습니다. 기존 데이터를 자동 삭제하지 않습니다.';
    END IF;
    IF (SELECT count(*) FROM lt_price_days) < 200
       OR (SELECT min(base_date) FROM lt_price_days) >
          (SELECT (as_of - INTERVAL '1 year')::date + 7 FROM lt_config)
       OR (SELECT count(*) FROM lt_days) < 100
       OR (SELECT min(base_date) FROM lt_days) >
          (SELECT (as_of - INTERVAL '6 months')::date + 7 FROM lt_config)
       OR (SELECT max(base_date) FROM lt_days) < (SELECT as_of - 7 FROM lt_config) THEN
        RAISE EXCEPTION '1년 거래 이력과 6개월 비교에 필요한 실제 가격 날짜가 부족합니다. 적재하지 않습니다.';
    END IF;
    IF (SELECT count(*) FROM lt_assets) < 30 THEN
        RAISE EXCEPTION '최근 1년과 직전 거래일 가격이 모두 있는 주식이 30개 미만입니다. 적재하지 않습니다.';
    END IF;
END $$;

CREATE TEMP TABLE lt_members (
    number integer,
    profile text,
    member_id bigint,
    portfolio_id bigint,
    holding_count integer,
    trade_count integer,
    realized_profit_loss numeric(19, 2)
) ON COMMIT DROP;

CREATE TEMP TABLE lt_positions (
    portfolio_id bigint,
    asset_id bigint,
    quantity bigint,
    average_price numeric(19, 2),
    PRIMARY KEY (portfolio_id, asset_id)
) ON COMMIT DROP;

CREATE TEMP TABLE lt_versions (
    portfolio_id bigint,
    asset_id bigint,
    effective_date date,
    sort_order integer,
    quantity bigint,
    average_price numeric(19, 2)
) ON COMMIT DROP;

DO $$
<<seed_data>>
DECLARE
    as_of date := (SELECT c.as_of FROM lt_config c);
    opening_date date := (as_of - INTERVAL '1 year')::date - 7;
    prefix text := 'lt496-' || to_char(as_of, 'YYYYMMDD');
    price_days date[] := (SELECT array_agg(base_date ORDER BY base_date) FROM lt_price_days);
    asset_ids bigint[];
    member_number integer;
    member_id bigint;
    portfolio_id bigint;
    profile text;
    holding_count integer;
    trade_count integer;
    asset_id bigint;
    kind text;
    quantity bigint;
    unit_price numeric;
    fee numeric;
    tax numeric;
    next_quantity bigint;
    next_average numeric;
    realized numeric;
    stamp timestamptz;
    position record;
    trade record;
BEGIN
    FOR member_number IN 1..53 LOOP
        opening_date := (as_of - INTERVAL '1 year')::date - 7 - floor(random() * 31)::integer;
        -- 측정 계정은 5명마다 light/standard/light/standard/heavy 순서다.
        profile := CASE
            WHEN member_number = 51 THEN 'warmup-light'
            WHEN member_number = 52 THEN 'warmup-standard'
            WHEN member_number = 53 THEN 'warmup-heavy'
            WHEN member_number % 5 IN (1, 3) THEN 'light'
            WHEN member_number % 5 IN (2, 4) THEN 'standard'
            ELSE 'heavy'
        END;
        holding_count := CASE profile
            WHEN 'light' THEN 5 WHEN 'warmup-light' THEN 5
            WHEN 'standard' THEN 15 WHEN 'warmup-standard' THEN 15
            ELSE 30
        END;
        trade_count := CASE profile
            WHEN 'light' THEN 50 WHEN 'warmup-light' THEN 50
            WHEN 'standard' THEN 200 WHEN 'warmup-standard' THEN 200
            ELSE 800
        END;

        INSERT INTO members
            (provider, provider_id, email, nickname, role, status, created_at, updated_at)
        VALUES ('GOOGLE', prefix || '-' || member_number,
                prefix || '-' || member_number || '@example.invalid',
                prefix || '-' || member_number, 'USER', 'ACTIVE',
                opening_date::timestamp AT TIME ZONE 'Asia/Seoul', CURRENT_TIMESTAMP)
        RETURNING id INTO member_id;

        INSERT INTO portfolios (member_id, created_at, updated_at)
        VALUES (member_id, opening_date::timestamp AT TIME ZONE 'Asia/Seoul', CURRENT_TIMESTAMP)
        RETURNING id INTO portfolio_id;

        -- 앞쪽 5종목의 보유 확률을 높이고 나머지는 분산한다. 실제 인기 순위는 아니다.
        SELECT array_agg(chosen.asset_id ORDER BY chosen.asset_id) INTO asset_ids
        FROM (
            SELECT a.asset_id FROM lt_assets a
            ORDER BY random() * CASE WHEN a.popularity <= 5 THEN 0.25 ELSE 1 END
            LIMIT holding_count
        ) chosen;

        FOREACH asset_id IN ARRAY asset_ids LOOP
            quantity := 20 + floor(random() * 181)::bigint;
            SELECT round(p.close_price * (0.8 + random()::numeric * 0.4), 2) INTO unit_price
            FROM stock_daily_prices p
            WHERE p.asset_id = seed_data.asset_id AND p.base_date = price_days[1];
            stamp := opening_date::timestamp AT TIME ZONE 'Asia/Seoul';
            INSERT INTO portfolio_holding_histories
                (portfolio_id, asset_id, change_type, quantity, average_purchase_price,
                 effective_date, fee, tax, created_at, updated_at)
            VALUES (portfolio_id, asset_id, 'OPENING_BALANCE', quantity, unit_price,
                    opening_date, 0, 0, stamp, stamp);
            INSERT INTO lt_positions VALUES (portfolio_id, asset_id, quantity, unit_price);
            INSERT INTO lt_versions VALUES (portfolio_id, asset_id, opening_date, 0, quantity, unit_price);
        END LOOP;

        realized := 0;
        -- 최근 거래 비중을 높인다. 같은 날의 순서를 고정하고 보유 수량 이내에서 매도한다.
        FOR trade IN
            SELECT row_number() OVER (ORDER BY day, tie)::integer AS sequence, day
            FROM (
                SELECT price_days[1 + floor(sqrt(random()) * array_length(price_days, 1))::integer] AS day,
                       n AS tie
                FROM generate_series(1, trade_count) n
            ) generated
            ORDER BY day, tie
        LOOP
            asset_id := CASE WHEN trade.sequence <= 2 THEN asset_ids[1]
                ELSE asset_ids[1 + floor(random() * holding_count)::integer] END;
            SELECT p.* INTO position FROM lt_positions p
            WHERE p.portfolio_id = seed_data.portfolio_id AND p.asset_id = seed_data.asset_id;
            kind := CASE
                WHEN trade.sequence = 1 THEN 'SELL'
                WHEN trade.sequence = 2 OR position.quantity <= 1 THEN 'BUY'
                WHEN random() < 0.65 THEN 'BUY'
                ELSE 'SELL'
            END;
            quantity := CASE
                WHEN trade.sequence = 1 THEN position.quantity
                WHEN kind = 'BUY' THEN 1 + floor(random() * 20)::bigint
                ELSE 1 + floor(random() * least(position.quantity - 1, 20))::bigint
            END;
            SELECT round(p.close_price * (0.97 + random()::numeric * 0.06), 2) INTO unit_price
            FROM stock_daily_prices p
            WHERE p.asset_id = seed_data.asset_id AND p.base_date = trade.day;
            -- 계산 다양성을 위한 합성 수수료·세율이다. 실제 요율을 재현하는 값은 아니다.
            fee := round(unit_price * quantity * 0.00015, 2);
            tax := CASE WHEN kind = 'SELL' THEN round(unit_price * quantity * 0.0015, 2) ELSE 0 END;
            next_quantity := position.quantity + CASE WHEN kind = 'BUY' THEN quantity ELSE -quantity END;
            next_average := CASE
                WHEN kind = 'BUY' THEN round(
                    (position.average_price * position.quantity + unit_price * quantity + fee + tax)
                    / next_quantity, 2)
                WHEN next_quantity = 0 THEN 0
                ELSE position.average_price
            END;
            IF kind = 'SELL' THEN
                realized := realized + (unit_price - position.average_price) * quantity - fee - tax;
            END IF;
            stamp := (trade.day::timestamp AT TIME ZONE 'Asia/Seoul')
                     + INTERVAL '9 hours' + trade.sequence * INTERVAL '1 millisecond';
            INSERT INTO portfolio_holding_histories
                (portfolio_id, asset_id, change_type, quantity, average_purchase_price,
                 effective_date, transaction_quantity, unit_price, fee, tax, created_at, updated_at)
            VALUES (portfolio_id, asset_id, kind, position.quantity, position.average_price,
                    trade.day, quantity, unit_price, fee, tax, stamp, stamp);
            UPDATE lt_positions p SET quantity = next_quantity, average_price = next_average
            WHERE p.portfolio_id = seed_data.portfolio_id AND p.asset_id = seed_data.asset_id;
            INSERT INTO lt_versions VALUES
                (portfolio_id, asset_id, trade.day, trade.sequence, next_quantity, next_average);
        END LOOP;

        INSERT INTO lt_members VALUES
            (member_number, profile, member_id, portfolio_id, holding_count, trade_count, round(realized, 2));
    END LOOP;
END $$;

INSERT INTO portfolio_holdings
    (portfolio_id, asset_id, quantity, average_purchase_price, created_at, updated_at)
SELECT portfolio_id, asset_id, quantity, average_price, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM lt_positions WHERE quantity > 0;

-- 해당 날짜의 보유 수량으로 당일/직전 거래일 평가액을 계산한다. 앱의 스냅샷 계산 규칙을 따른다.
WITH positions_by_day AS (
    SELECT m.portfolio_id, d.base_date, d.previous_date, p.*
    FROM lt_members m CROSS JOIN lt_days d
    CROSS JOIN LATERAL (
        SELECT DISTINCT ON (v.asset_id) v.asset_id, v.quantity, v.average_price
        FROM lt_versions v
        WHERE v.portfolio_id = m.portfolio_id AND v.effective_date <= d.base_date
        ORDER BY v.asset_id, v.effective_date DESC, v.sort_order DESC
    ) p
    WHERE p.quantity > 0
), amounts AS (
    SELECT p.portfolio_id, p.base_date, p.previous_date,
           round(sum(p.quantity * p.average_price), 2) AS purchase,
           round(sum(p.quantity * today.close_price), 2) AS evaluation,
           round(sum(p.quantity * previous.close_price), 2) AS previous_evaluation
    FROM positions_by_day p
    JOIN stock_daily_prices today ON today.asset_id = p.asset_id AND today.base_date = p.base_date
    LEFT JOIN stock_daily_prices previous
        ON previous.asset_id = p.asset_id AND previous.base_date = p.previous_date
    GROUP BY p.portfolio_id, p.base_date, p.previous_date
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
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM amounts;

DO $$
BEGIN
    IF EXISTS (
        SELECT FROM lt_members m
        WHERE (SELECT count(*) FROM portfolio_holdings h WHERE h.portfolio_id = m.portfolio_id)
                  <> m.holding_count
           OR (SELECT count(*) FROM portfolio_holding_histories h
               WHERE h.portfolio_id = m.portfolio_id AND h.change_type IN ('BUY', 'SELL'))
                  <> m.trade_count
           OR (SELECT count(*) FROM portfolio_daily_snapshots s WHERE s.portfolio_id = m.portfolio_id)
                  <> (SELECT count(*) FROM lt_days)
    ) THEN
        RAISE EXCEPTION '적재 건수 검증 실패. 이번 트랜잭션을 취소합니다.';
    END IF;
    IF EXISTS (
        SELECT FROM portfolio_holding_histories h
        JOIN lt_members m ON m.portfolio_id = h.portfolio_id
        GROUP BY h.portfolio_id, h.asset_id
        HAVING sum(CASE h.change_type
            WHEN 'OPENING_BALANCE' THEN h.quantity
            WHEN 'BUY' THEN h.transaction_quantity
            WHEN 'SELL' THEN -h.transaction_quantity ELSE 0 END)
            <> (SELECT p.quantity FROM portfolio_holdings p
                WHERE p.portfolio_id = h.portfolio_id AND p.asset_id = h.asset_id)
    ) THEN
        RAISE EXCEPTION '거래 이력과 현재 보유 수량 불일치. 이번 트랜잭션을 취소합니다.';
    END IF;
END $$;

-- 적재 요약. trade_count는 BUY/SELL 건수이며 초기 잔고 이력은 별도다.
SELECT profile, count(*) AS members, min(holding_count) AS min_holdings,
       max(holding_count) AS max_holdings, min(trade_count) AS min_trades, max(trade_count) AS max_trades
FROM lt_members GROUP BY profile ORDER BY profile;

-- 측정 회원은 number 순서로 users.json에 매핑하고, warmup은 별도 파일에 매핑한다.
-- accessToken은 SQL에서 발급하지 않는다. 1~50번은 users.json, 51~53번은 warmup-users.json이다.
SELECT m.number, m.profile, m.member_id AS "memberId", p.asset_id AS "assetId",
       latest.close_price AS "unitPrice", p.quantity AS "initialQuantity",
       m.realized_profit_loss AS "expectedRealizedProfitLoss",
       (SELECT count(*) FROM portfolio_daily_snapshots s WHERE s.portfolio_id = m.portfolio_id) AS snapshots,
       c.as_of AS today, (c.as_of - INTERVAL '1 month')::date AS one_month_ago,
       (c.as_of - INTERVAL '6 months')::date AS six_months_ago
FROM lt_members m CROSS JOIN lt_config c
JOIN LATERAL (
    SELECT p.* FROM lt_positions p
    WHERE p.portfolio_id = m.portfolio_id AND p.quantity > 0 ORDER BY p.asset_id LIMIT 1
) p ON true
JOIN LATERAL (
    SELECT close_price FROM stock_daily_prices s
    WHERE s.asset_id = p.asset_id AND s.base_date < c.as_of ORDER BY s.base_date DESC LIMIT 1
) latest ON true
ORDER BY m.number;

COMMIT;
