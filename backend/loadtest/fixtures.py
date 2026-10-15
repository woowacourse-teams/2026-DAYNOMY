"""Deterministic synthetic portfolio fixtures; no application or DB connection."""

import base64
import calendar
import hashlib
import hmac
import io
import json
from datetime import date, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP

SEOUL = timezone(timedelta(hours=9))
ISSUER = "daynomy-loadtest"
DATABASE = "daynomy_loadtest"


def months_before(day, months):
    index = day.year * 12 + day.month - 1 - months
    year, month = divmod(index, 12)
    month += 1
    return date(year, month, min(day.day, calendar.monthrange(year, month)[1]))


def money(value):
    return Decimal(value).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def percentage(amount, total):
    return money(Decimal(amount) * 100 / total) if total else money(0)


def access_token(member_id, secret, issued_at):
    def encode(value):
        raw = json.dumps(value, separators=(",", ":")).encode()
        return base64.urlsafe_b64encode(raw).rstrip(b"=")

    header = encode({"alg": "HS256", "typ": "JWT"})
    claims = encode({
        "iss": ISSUER, "sub": str(member_id), "iat": issued_at,
        "exp": issued_at + 86400, "jti": f"loadtest-{member_id}-{issued_at}",
        "role": "USER", "token_type": "access",
    })
    signing_input = header + b"." + claims
    signature = hmac.new(base64.b64decode(secret), signing_input, hashlib.sha256).digest()
    return (signing_input + b"." + base64.urlsafe_b64encode(signature).rstrip(b"=")).decode()


def build_fixture(as_of, members=50, holdings=20, transactions=1000):
    if not 1 <= members <= 100 or not 1 <= holdings <= 50 or transactions < 1:
        raise ValueError("members: 1..100, holdings: 1..50, transactions: positive")
    if transactions > 100000:
        raise ValueError("transactions must be <= 100000 per member")
    start = months_before(as_of, 6)
    prior = start - timedelta(days=1)
    while prior.weekday() >= 5:
        prior -= timedelta(days=1)
    price_dates = []
    cursor = prior
    while cursor < as_of:
        if cursor.weekday() < 5:
            price_dates.append(cursor)
        cursor += timedelta(days=1)
    trading_dates = [day for day in price_dates if day >= start]
    quantities = {asset: 1000 for asset in range(1, holdings + 1)}
    # Opening balances precede every case's trade date, including six-months.
    histories = [
        (asset, start, "OPENING_BALANCE", 1000, 10000 + asset * 100, None)
        for asset in quantities
    ]
    for index in range(transactions):
        asset = index % holdings + 1
        day = trading_dates[index * len(trading_dates) // transactions]
        histories.append((asset, day, "BUY", quantities[asset], 10000 + asset * 100, 1))
        quantities[asset] += 1

    snapshots = []
    historical_quantities = {asset: 0 for asset in quantities}
    history_index = 0
    for price_index, day in enumerate(price_dates):
        if day < start:
            continue
        while history_index < len(histories) and histories[history_index][1] <= day:
            asset, _, kind, quantity, _, trade_quantity = histories[history_index]
            historical_quantities[asset] = quantity if kind == "OPENING_BALANCE" else quantity + trade_quantity
            history_index += 1
        purchase = sum(qty * (10000 + asset * 100) for asset, qty in historical_quantities.items())
        evaluation = sum(qty * (10000 + asset * 100 + price_index) for asset, qty in historical_quantities.items())
        previous = sum(qty * (10000 + asset * 100 + price_index - 1) for asset, qty in historical_quantities.items())
        profit = evaluation - purchase
        daily = evaluation - previous
        snapshots.append({
            "baseDate": day.isoformat(), "totalPurchaseAmount": str(money(purchase)),
            "totalEvaluationAmount": str(money(evaluation)), "totalProfitLoss": str(money(profit)),
            "totalReturnRate": str(percentage(profit, purchase)),
            "dailyProfitLoss": str(money(daily)), "dailyReturnRate": str(percentage(daily, previous)),
        })
    return {
        "asOf": as_of.isoformat(), "start": start.isoformat(), "memberCount": members,
        "holdingCount": holdings, "transactionsPerMember": transactions,
        "historyRowsPerMember": transactions + holdings,
        "cases": {"today": as_of.isoformat(), "one-month": months_before(as_of, 1).isoformat(), "six-months": start.isoformat()},
        "priceDates": [day.isoformat() for day in price_dates], "quantities": quantities,
        "histories": histories, "snapshots": snapshots,
    }


def write_seed(fixture, output):
    """Seed only the named disposable DB. Share templates across synthetic members."""
    stamp = f"{fixture['start']}T00:00:00+09:00"
    output.write(f"""\\set ON_ERROR_STOP on
BEGIN;
DO $$ BEGIN
  IF current_database() <> '{DATABASE}' OR current_user <> '{DATABASE}' THEN
    RAISE EXCEPTION 'This seed requires the dedicated daynomy_loadtest database and role';
  END IF;
END $$;
TRUNCATE TABLE members, assets RESTART IDENTITY CASCADE;
INSERT INTO members (id, provider, provider_id, email, nickname, role, status, created_at, updated_at)
SELECT n, 'GOOGLE', 'loadtest-' || n, 'loadtest-' || n || '@example.invalid',
       'loadtest-' || n, 'USER', 'ACTIVE', '{stamp}', '{stamp}'
FROM generate_series(1, {fixture['memberCount']}) AS n;
INSERT INTO portfolios (id, member_id, created_at, updated_at)
SELECT id, id, '{stamp}', '{stamp}' FROM members;
INSERT INTO assets (id, name, category, asset_code, market, listed, stock_base_date, created_at, updated_at)
SELECT n, 'Synthetic stock ' || n, 'STOCK', 'LOADTEST-' || n, 'KOSPI', true,
       '{fixture['start']}', '{stamp}', '{stamp}' FROM generate_series(1, {fixture['holdingCount']}) AS n;
SELECT setval(pg_get_serial_sequence('members', 'id'), {fixture['memberCount']});
SELECT setval(pg_get_serial_sequence('portfolios', 'id'), {fixture['memberCount']});
SELECT setval(pg_get_serial_sequence('assets', 'id'), {fixture['holdingCount']});
CREATE TEMP TABLE loadtest_dates (day DATE, price_index INTEGER) ON COMMIT DROP;
INSERT INTO loadtest_dates VALUES
""")
    output.write(",\n".join(f"('{day}', {index})" for index, day in enumerate(fixture["priceDates"])) + ";\n")
    output.write(f"""INSERT INTO stock_daily_prices (asset_id, base_date, close_price, created_at, updated_at)
SELECT a.id, d.day, 10000 + a.id * 100 + d.price_index, '{stamp}', '{stamp}'
FROM assets a CROSS JOIN loadtest_dates d;
CREATE TEMP TABLE loadtest_histories (sort_order INTEGER, asset_id BIGINT, effective_date DATE,
    change_type VARCHAR(20), quantity BIGINT, average_price NUMERIC(19,2), transaction_quantity BIGINT) ON COMMIT DROP;
INSERT INTO loadtest_histories VALUES
""")
    rows = []
    for index, (asset, day, kind, quantity, price, trade_quantity) in enumerate(fixture["histories"]):
        trade = "NULL" if trade_quantity is None else str(trade_quantity)
        rows.append(f"({index}, {asset}, '{day}', '{kind}', {quantity}, {price}, {trade})")
    output.write(",\n".join(rows) + ";\n")
    output.write(f"""INSERT INTO portfolio_holding_histories
  (portfolio_id, asset_id, change_type, quantity, average_purchase_price, effective_date,
   transaction_quantity, unit_price, fee, tax, created_at, updated_at)
SELECT p.id, h.asset_id, h.change_type, h.quantity, h.average_price, h.effective_date,
       h.transaction_quantity, CASE WHEN h.transaction_quantity IS NULL THEN NULL ELSE h.average_price END,
       0, 0, TIMESTAMPTZ '{stamp}' + h.sort_order * INTERVAL '1 microsecond', '{stamp}'
FROM portfolios p CROSS JOIN loadtest_histories h ORDER BY p.id, h.sort_order;
CREATE TEMP TABLE loadtest_holdings (asset_id BIGINT, quantity BIGINT) ON COMMIT DROP;
INSERT INTO loadtest_holdings VALUES
""")
    output.write(",\n".join(f"({asset}, {quantity})" for asset, quantity in fixture["quantities"].items()) + ";\n")
    output.write(f"""INSERT INTO portfolio_holdings
  (portfolio_id, asset_id, quantity, average_purchase_price, created_at, updated_at)
SELECT p.id, h.asset_id, h.quantity, 10000 + h.asset_id * 100, '{stamp}', '{stamp}'
FROM portfolios p CROSS JOIN loadtest_holdings h;
CREATE TEMP TABLE loadtest_snapshots (base_date DATE, purchase NUMERIC(19,2), evaluation NUMERIC(19,2),
    profit NUMERIC(19,2), rate NUMERIC(10,2), daily_profit NUMERIC(19,2), daily_rate NUMERIC(10,2)) ON COMMIT DROP;
INSERT INTO loadtest_snapshots VALUES
""")
    fields = ("totalPurchaseAmount", "totalEvaluationAmount", "totalProfitLoss", "totalReturnRate", "dailyProfitLoss", "dailyReturnRate")
    output.write(",\n".join("('" + point["baseDate"] + "', " + ", ".join(point[key] for key in fields) + ")" for point in fixture["snapshots"]) + ";\n")
    output.write(f"""INSERT INTO portfolio_daily_snapshots
  (portfolio_id, base_date, total_purchase_amount, total_evaluation_amount, total_profit_loss,
   total_return_rate, daily_profit_loss, daily_return_rate, created_at, updated_at)
SELECT p.id, s.base_date, s.purchase, s.evaluation, s.profit, s.rate, s.daily_profit, s.daily_rate,
       '{stamp}', '{stamp}' FROM portfolios p CROSS JOIN loadtest_snapshots s;
COMMIT;
ANALYZE;
""")


def manifest(fixture, secret, issued_at):
    fingerprint_data = {
        key: fixture[key] for key in ("asOf", "memberCount", "holdingCount", "transactionsPerMember")
    }
    seed = io.StringIO()
    write_seed(fixture, seed)
    fingerprint = hashlib.sha256(seed.getvalue().encode()).hexdigest()
    return {
        **fingerprint_data, "schemaVersion": 1, "fixtureHash": fingerprint,
        "historyRowsPerMember": fixture["historyRowsPerMember"], "cases": fixture["cases"],
        "priceDateCount": len(fixture["priceDates"]), "snapshotCountPerMember": len(fixture["snapshots"]),
        "initialSnapshots": fixture["snapshots"], "tokenExpiresAt": issued_at + 86400,
        "assetId": 1, "unitPrice": 10100, "maxIterations": 500,
        "members": [
            {"memberId": member, "accessToken": access_token(member, secret, issued_at),
             "initialQuantity": fixture["quantities"][1]}
            for member in range(1, fixture["memberCount"] + 1)
        ],
    }
