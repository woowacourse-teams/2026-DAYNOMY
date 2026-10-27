ALTER TABLE portfolio_holding_histories ADD COLUMN trade_time TIME;
COMMENT ON COLUMN portfolio_holding_histories.trade_time IS '거래 시각 (Asia/Seoul), 기존 기록은 NULL';
