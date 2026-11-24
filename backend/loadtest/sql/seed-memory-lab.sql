-- Only the ephemeral local memory experiment databases. No production data.
BEGIN;
DO $$ BEGIN
  IF current_database() NOT IN ('memory_lab_default','memory_lab_heap256','memory_lab_heap192','memory_lab_heap192serial',
                              'memory_lab_heap256arena2','memory_lab_heap192arena2','memory_lab_heap128arena2',
                              'memory_lab_heap192arena2c1','memory_lab_heap128arena2c1','memory_lab_heap96arena2c1') THEN
    RAISE EXCEPTION 'Refusing non-lab database';
  END IF;
END $$;
INSERT INTO assets(id,name,category,asset_code,market,listed,stock_base_date,created_at,updated_at)
SELECT 910000+n,'Memory stock '||n,'STOCK',(990000+n)::text,'KOSPI',true,
       current_date-1,now(),now() FROM generate_series(1,30) n;
INSERT INTO stock_daily_prices(asset_id,base_date,close_price,created_at,updated_at)
SELECT 910000+n,current_date-d,11000,now(),now()
FROM generate_series(1,30) n CROSS JOIN generate_series(1,60) d;
INSERT INTO members(id,provider,provider_id,email,nickname,role,status,created_at,updated_at)
SELECT 920000+n,'GOOGLE','memory-lab-'||n,'memory-'||n||'@example.invalid',
       'memory-'||n,'USER','ACTIVE',now(),now() FROM generate_series(1,50) n;
INSERT INTO portfolios(id,member_id,created_at,updated_at)
SELECT 930000+n,920000+n,now(),now() FROM generate_series(1,50) n;
INSERT INTO portfolio_holdings(portfolio_id,asset_id,quantity,average_purchase_price,native_average_purchase_price,created_at,updated_at)
SELECT 930000+m,910000+a,108,10000,10000,now(),now()
FROM generate_series(1,50) m CROSS JOIN generate_series(1,30) a;
INSERT INTO portfolio_holding_histories(portfolio_id,asset_id,change_type,quantity,average_purchase_price,
       native_average_purchase_price,effective_date,fee,tax,exchange_rate,created_at,updated_at)
SELECT 930000+m,910000+a,'OPENING_BALANCE',100,10000,10000,current_date-90,0,0,1,
       (current_date-90)::timestamptz,(current_date-90)::timestamptz
FROM generate_series(1,50) m CROSS JOIN generate_series(1,30) a;
INSERT INTO portfolio_holding_histories(portfolio_id,asset_id,change_type,quantity,average_purchase_price,
       native_average_purchase_price,effective_date,transaction_quantity,unit_price,fee,tax,exchange_rate,created_at,updated_at)
SELECT 930000+m,910000+a,'BUY',100+2*(t-1),10000,10000,current_date-(90-15*t),2,10000,0,0,1,
       (current_date-(90-15*t))::timestamptz,(current_date-(90-15*t))::timestamptz
FROM generate_series(1,50) m CROSS JOIN generate_series(1,30) a CROSS JOIN generate_series(1,4) t;
COMMIT;
