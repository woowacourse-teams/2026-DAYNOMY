ALTER TABLE market_trading_days ADD COLUMN us_trading BOOLEAN;
ALTER TABLE market_trading_days ADD COLUMN us_collected_at TIMESTAMPTZ;

-- 기존에 수집된 미국 종가는 해당 날짜의 수집 근거로 사용한다. 개별 종목 누락은 집계 시 다시 확인한다.
UPDATE market_trading_days day
SET us_trading = TRUE, us_collected_at = CURRENT_TIMESTAMP
WHERE EXISTS (
  SELECT 1 FROM stock_daily_prices price
  JOIN assets asset ON asset.id = price.asset_id
  WHERE price.base_date = day.base_date AND asset.market IN
    ('NASDAQ', 'NYSE', 'NYSE_ARCA', 'NYSE_AMERICAN', 'BATS')
);
