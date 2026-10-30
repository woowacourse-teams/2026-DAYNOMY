-- seed-portfolio-1000.sql 커밋 후 실행하는 읽기 전용 검증/계정 매핑 쿼리.
-- 적재 파일의 기준일을 바꿨다면 아래 namespace도 함께 바꾼다.
-- 예상: 1,003명, 보유 14,050행, 매수/매도 261,050건, 전체 이력 275,100건.
BEGIN READ ONLY;

WITH cohort AS (
    SELECT m.id AS member_id, p.id AS portfolio_id,
           substring(m.provider_id FROM '[0-9]+$')::integer AS test_number
    FROM members m JOIN portfolios p ON p.member_id = m.id
    WHERE m.provider = 'GOOGLE' AND m.provider_id ~ '^lt1000-20261029-[0-9]+$'
), profiles AS (
    SELECT *, CASE WHEN test_number <= 1000 THEN 'measure' ELSE 'warmup' END AS phase,
           CASE WHEN test_number = 1001 THEN 'light' WHEN test_number = 1002 THEN 'standard'
                WHEN test_number = 1003 THEN 'heavy' WHEN test_number % 5 IN (1,3) THEN 'light'
                WHEN test_number % 5 IN (2,4) THEN 'standard' ELSE 'heavy' END AS profile
    FROM cohort
)
SELECT phase, profile, count(*) AS members,
       sum((SELECT count(*) FROM portfolio_holdings h WHERE h.portfolio_id = c.portfolio_id)) AS holdings,
       sum((SELECT count(*) FROM portfolio_holding_histories h WHERE h.portfolio_id = c.portfolio_id
            AND h.change_type IN ('BUY','SELL'))) AS trades,
       sum((SELECT count(*) FROM portfolio_holding_histories h WHERE h.portfolio_id = c.portfolio_id)) AS all_histories,
       sum((SELECT count(*) FROM portfolio_daily_snapshots s WHERE s.portfolio_id = c.portfolio_id)) AS snapshots,
       min((SELECT min(s.base_date) FROM portfolio_daily_snapshots s WHERE s.portfolio_id = c.portfolio_id)) AS first_snapshot,
       max((SELECT max(s.base_date) FROM portfolio_daily_snapshots s WHERE s.portfolio_id = c.portfolio_id)) AS last_snapshot
FROM profiles c GROUP BY phase, profile ORDER BY phase, profile;

-- 결과가 0행이어야 한다. 원장의 모든 중간 수량과 현재 보유 수량을 대조한다.
WITH cohort AS (
    SELECT p.id FROM portfolios p JOIN members m ON m.id = p.member_id
    WHERE m.provider = 'GOOGLE' AND m.provider_id ~ '^lt1000-20261029-[0-9]+$'
), running AS (
    SELECT h.portfolio_id, h.asset_id,
           sum(CASE h.change_type WHEN 'OPENING_BALANCE' THEN h.quantity
                    WHEN 'BUY' THEN h.transaction_quantity WHEN 'SELL' THEN -h.transaction_quantity ELSE 0 END)
           OVER (PARTITION BY h.portfolio_id, h.asset_id ORDER BY h.effective_date, h.created_at, h.id) AS quantity,
           row_number() OVER (PARTITION BY h.portfolio_id, h.asset_id
                              ORDER BY h.effective_date DESC, h.created_at DESC, h.id DESC) AS reverse_order
    FROM portfolio_holding_histories h JOIN cohort c ON c.id = h.portfolio_id
)
SELECT r.portfolio_id, r.asset_id, r.quantity AS ledger_quantity, h.quantity AS current_quantity
FROM running r LEFT JOIN portfolio_holdings h ON h.portfolio_id = r.portfolio_id AND h.asset_id = r.asset_id
WHERE r.quantity < 0 OR (r.reverse_order = 1 AND r.quantity <> coalesce(h.quantity,0));

-- 회원별 비밀값 없는 준비 자료. accessToken은 서버의 JWT 발급 도구로 별도 생성한다.
-- 기존 53명용 PortfolioLoadtestTool은 이 1,003명 namespace를 지원하지 않는다.
-- 이 결과를 기존 accounts.json 대신 그대로 넣으면 안 된다(accessToken/fixtureHash 등 없음).
WITH cohort AS (
    SELECT m.id AS member_id, p.id AS portfolio_id,
           substring(m.provider_id FROM '[0-9]+$')::integer AS test_number
    FROM members m JOIN portfolios p ON p.member_id = m.id
    WHERE m.provider = 'GOOGLE' AND m.provider_id ~ '^lt1000-20261029-[0-9]+$'
)
SELECT c.test_number, c.member_id, c.portfolio_id,
       CASE WHEN c.test_number <= 1000 THEN 'measure' ELSE 'warmup' END AS phase,
       chosen.asset_id AS transaction_asset_id, chosen.quantity AS initial_quantity,
       latest.base_date AS price_date, latest.close_price AS unit_price,
       CASE WHEN a.market IN ('KOSPI','KOSDAQ') THEN 1::numeric ELSE r.rate END AS exchange_rate,
       (SELECT jsonb_agg(jsonb_build_object('assetId',h.asset_id,'quantity',h.quantity,
                        'averagePurchasePrice',h.average_purchase_price) ORDER BY h.asset_id)
        FROM portfolio_holdings h WHERE h.portfolio_id = c.portfolio_id) AS calculation_holdings
FROM cohort c
JOIN LATERAL (SELECT h.* FROM portfolio_holdings h WHERE h.portfolio_id = c.portfolio_id
              ORDER BY h.quantity DESC,h.asset_id LIMIT 1) chosen ON true
JOIN assets a ON a.id = chosen.asset_id
JOIN LATERAL (SELECT p.base_date,p.close_price FROM stock_daily_prices p WHERE p.asset_id = a.id
              ORDER BY p.base_date DESC LIMIT 1) latest ON true
LEFT JOIN usd_krw_rates r ON r.requested_date = latest.base_date
ORDER BY c.test_number;

COMMIT;
