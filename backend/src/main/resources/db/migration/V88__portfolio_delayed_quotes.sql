CREATE TABLE portfolio_delayed_quotes (
    symbol varchar(60) PRIMARY KEY,
    market varchar(2) NOT NULL,
    requested_at timestamptz NOT NULL,
    attempted_at timestamptz,
    price numeric,
    price_as_of timestamptz
);

CREATE INDEX idx_portfolio_delayed_quotes_demand
    ON portfolio_delayed_quotes (market, requested_at);

CREATE TABLE portfolio_delayed_quote_budgets (
    budget_date date PRIMARY KEY,
    reserved_calls integer NOT NULL DEFAULT 0,
    kr_slot timestamptz,
    us_slot timestamptz,
    exhausted boolean NOT NULL DEFAULT false
);
