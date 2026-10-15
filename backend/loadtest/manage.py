#!/usr/bin/env python3
"""Local-only portfolio transaction load test lifecycle. Uses Python's standard library."""

import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
import time
from datetime import date, datetime
from urllib.request import Request, urlopen

from fixtures import DATABASE, ISSUER, SEOUL, build_fixture, manifest, money, percentage, write_seed

ROOT = Path(__file__).resolve().parent
BACKEND = ROOT.parent
LOCAL = ROOT / ".local"
BASE_URL = "http://127.0.0.1:8496"


def private_write(path, value):
    with open(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "w") as output:
        output.write(value)
    path.chmod(0o600)


def environment():
    path = LOCAL / "runtime.env"
    if not path.exists():
        raise ValueError("Run prepare first")
    return dict(line.split("=", 1) for line in path.read_text().splitlines() if line)


def compose(*args, **kwargs):
    return subprocess.run([
        "docker", "compose", "--project-name", "daynomy-loadtest-496",
        "--env-file", str(LOCAL / "runtime.env"), "--file", str(ROOT / "compose.yaml"), *args,
    ], check=True, **kwargs)


def sql(query=None, source=None):
    command = ["exec", "-T", "postgres", "psql", "-X", "-q", "-A", "-t",
               "-v", "ON_ERROR_STOP=1", "-U", DATABASE, "-d", DATABASE]
    if query is not None:
        result = compose(*command, "-c", query, capture_output=True, text=True)
    else:
        result = compose(*command, stdin=source, capture_output=True, text=True)
    return result.stdout.strip()


def load_manifest():
    return json.loads((LOCAL / "fixture.json").read_text())


def api(path, member):
    request = Request(BASE_URL + path, headers={"Cookie": "access_token=" + member["accessToken"]})
    with urlopen(request, timeout=30) as response:
        return json.load(response)


def prepare(args):
    LOCAL.mkdir(mode=0o700, parents=True, exist_ok=True)
    LOCAL.chmod(0o700)
    if not (LOCAL / "runtime.env").exists():
        runtime = {
            "DB_PASSWORD": secrets.token_urlsafe(32),
            "JWT_SECRET": base64.b64encode(secrets.token_bytes(32)).decode(),
            "DB_URL": "jdbc:postgresql://127.0.0.1:55496/daynomy_loadtest",
            "DB_USERNAME": DATABASE, "GOOGLE_CLIENT_ID": "loadtest-client",
            "GOOGLE_CLIENT_SECRET": "loadtest-placeholder", "PUBLIC_DATA_SERVICE_KEY": "loadtest-placeholder",
            "AWS_S3_PUBLIC_BASE_URL": "https://example.invalid", "AWS_REGION": "ap-northeast-2",
            "AWS_S3_BUCKET": "loadtest-placeholder", "OPENAI_API_KEY": "",
            "YOUTUBE_API_KEY": "", "FRONTEND_BASE_URL": "http://localhost:5173",
            "COOKIE_SECURE": "false", "SPRING_CONFIG_IMPORT": "", "SPRING_PROFILES_ACTIVE": "loadtest",
        }
        private_write(LOCAL / "runtime.env", "".join(f"{key}={value}\n" for key, value in runtime.items()))
    fixture = build_fixture(args.as_of, args.members, args.holdings, args.transactions)
    with (LOCAL / "seed.sql").open("w") as output:
        write_seed(fixture, output)
    (LOCAL / "seed.sql").chmod(0o600)
    data = manifest(fixture, environment()["JWT_SECRET"], int(time.time()))
    private_write(LOCAL / "fixture.json", json.dumps(data, ensure_ascii=False, indent=2))
    print(f"Prepared {args.members} members × {args.holdings} holdings × {args.transactions} transactions")
    print(f"Fixture: {data['fixtureHash']} (as of {data['asOf']}, tokens valid for 24 hours)")


def verify_seed():
    data = load_manifest()
    today = datetime.now(SEOUL).date().isoformat()
    if data["asOf"] != today:
        raise ValueError("Prepare and seed with today's Seoul date before HTTP tests")
    counts = json.loads(sql("""SELECT json_build_object(
        'members', (SELECT count(*) FROM members),
        'holdings', (SELECT count(*) FROM portfolio_holdings),
        'histories', (SELECT count(*) FROM portfolio_holding_histories),
        'snapshots', (SELECT count(*) FROM portfolio_daily_snapshots),
        'prices', (SELECT count(*) FROM stock_daily_prices))"""))
    expected = {
        "members": data["memberCount"], "holdings": data["memberCount"] * data["holdingCount"],
        "histories": data["memberCount"] * data["historyRowsPerMember"],
        "snapshots": data["memberCount"] * data["snapshotCountPerMember"],
        "prices": data["holdingCount"] * data["priceDateCount"],
    }
    if counts != expected:
        raise ValueError("DB is not at its initial fixture state. Run seed before each test")
    if data["tokenExpiresAt"] <= time.time() + 600:
        raise ValueError("Fixture tokens are expiring; run prepare and seed again")
    member = data["members"][0]
    body = api("/api/users/me/portfolio", member)
    target = next(holding for holding in body["holdings"] if holding["assetId"] == data["assetId"])
    if target["quantity"] != member["initialQuantity"] or target["averagePurchasePrice"] != data["unitPrice"]:
        raise ValueError("Authenticated API does not match the fixture")
    print("Initial fixture counts and authenticated portfolio read API verified", flush=True)


def verify_result(label):
    data = load_manifest()
    result_dir = LOCAL / "results" / label
    run = json.loads((result_dir / "run.json").read_text())
    if run["exitCode"] != 0 or run["fixtureHash"] != data["fixtureHash"]:
        raise ValueError("Run failed or uses a different fixture")
    delta = run["iterationsPerVu"] * (1 if run["side"] == "BUY" else -1)
    trade_date = data["cases"][run["scenario"]]
    fields = ("totalPurchaseAmount", "totalEvaluationAmount", "totalProfitLoss",
              "totalReturnRate", "dailyProfitLoss", "dailyReturnRate")
    for member in data["members"][:run["vus"]]:
        holdings = api("/api/users/me/portfolio", member)
        target = next(row for row in holdings["holdings"] if row["assetId"] == data["assetId"])
        if (target["quantity"] != member["initialQuantity"] + delta or
                target["averagePurchasePrice"] != data["unitPrice"] or
                holdings["totalRealizedProfitLoss"] != 0):
            raise ValueError("Final holdings do not match deterministic requests")
        first = data["initialSnapshots"][0]["baseDate"]
        last = data["initialSnapshots"][-1]["baseDate"]
        actual = api(f"/api/users/me/portfolio/performance?from={first}&to={last}", member)["points"]
        if len(actual) != len(data["initialSnapshots"]):
            raise ValueError("Historical snapshot count changed unexpectedly")
        for index, (expected, point) in enumerate(zip(data["initialSnapshots"], actual)):
            values = {field: money(expected[field]) for field in fields}
            if expected["baseDate"] >= trade_date:
                values["totalPurchaseAmount"] += delta * data["unitPrice"]
                values["totalEvaluationAmount"] += delta * (data["unitPrice"] + index + 1)
                values["totalProfitLoss"] = values["totalEvaluationAmount"] - values["totalPurchaseAmount"]
                values["totalReturnRate"] = percentage(values["totalProfitLoss"], values["totalPurchaseAmount"])
                values["dailyProfitLoss"] += delta
                values["dailyReturnRate"] = percentage(values["dailyProfitLoss"], values["totalEvaluationAmount"] - values["dailyProfitLoss"])
            if point["baseDate"] != expected["baseDate"] or any(money(point[field]) != values[field] for field in fields):
                raise ValueError("Historical snapshot differs from the expected calculation")
    (result_dir / "verification.json").write_text(json.dumps({
        "verifiedMembers": run["vus"], "snapshotsPerMember": len(data["initialSnapshots"]),
        "holdingsAndSnapshotsMatch": True,
    }, indent=2) + "\n")
    print("Final holdings, realized profit and every historical snapshot verified")


def run_test(args):
    verify_seed()
    data = load_manifest()
    if args.vus > data["memberCount"] or args.iterations > data["maxIterations"]:
        raise ValueError("VUs/iterations exceed prepared fixture limits")
    result_dir = LOCAL / "results" / args.label
    result_dir.mkdir(parents=True, exist_ok=False)
    metadata = {
        "version": args.version,
        "fixtureHash": data["fixtureHash"], "asOf": data["asOf"],
        "members": data["memberCount"], "holdings": data["holdingCount"],
        "transactionsPerMember": data["transactionsPerMember"], "scenario": args.scenario,
        "side": args.side, "vus": args.vus, "iterationsPerVu": args.iterations,
        "expectedRequests": args.vus * args.iterations,
        "commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=BACKEND, text=True).strip(),
        "dirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=BACKEND, text=True).strip()),
        "startedAt": datetime.now(SEOUL).isoformat(),
        "k6Version": subprocess.check_output(["k6", "version"], text=True).strip(),
        "postgresVersion": sql("SELECT version();"),
    }
    # Reset after preflight. Collect SQL separately; never expose auth cookies or raw responses.
    sql("CREATE EXTENSION IF NOT EXISTS pg_stat_statements; SELECT pg_stat_statements_reset();")
    command = ["k6", "run"]
    params = {
        "BASE_URL": BASE_URL, "FIXTURE_FILE": str(LOCAL / "fixture.json"),
        "SUMMARY_FILE": str(result_dir / "summary.json"), "VUS": str(args.vus),
        "ITERATIONS": str(args.iterations), "SCENARIO": args.scenario, "SIDE": args.side,
    }
    for key, value in params.items():
        command.extend(["-e", f"{key}={value}"])
    command.append(str(ROOT / f"transactions-{args.version}.js"))
    # Prevent ambient K6_* options from changing the fixed request budget or logging cookies.
    k6_env = {key: value for key, value in os.environ.items() if not key.startswith("K6_")}
    result = subprocess.run(command, check=False, env=k6_env)
    metadata["exitCode"] = result.returncode
    metadata["finishedAt"] = datetime.now(SEOUL).isoformat()
    statements = sql("""SELECT coalesce(json_agg(row_to_json(s)), '[]'::json) FROM
        (SELECT query, calls, rows, total_exec_time, mean_exec_time
         FROM pg_stat_statements WHERE dbid = (SELECT oid FROM pg_database WHERE datname = 'daynomy_loadtest')
         AND query NOT ILIKE '%pg_stat_statements%' ORDER BY total_exec_time DESC) s""")
    (result_dir / "sql.json").write_text(statements + "\n")
    (result_dir / "run.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
    print(f"Results saved in {result_dir.relative_to(BACKEND)}")
    return result.returncode


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prepare_parser = commands.add_parser("prepare")
    prepare_parser.add_argument("--members", type=int, default=50)
    prepare_parser.add_argument("--holdings", type=int, default=20)
    prepare_parser.add_argument("--transactions", type=int, default=1000)
    prepare_parser.add_argument("--as-of", type=date.fromisoformat, default=datetime.now(SEOUL).date())
    for name in ("up", "serve", "seed", "verify", "down"):
        commands.add_parser(name)
    verification = commands.add_parser("verify-result")
    verification.add_argument("--label", required=True)
    run = commands.add_parser("run")
    run.add_argument("--version", choices=("v1", "v2"), default="v1")
    run.add_argument("--scenario", choices=("today", "one-month", "six-months"), default="today")
    run.add_argument("--side", choices=("BUY", "SELL"), default="BUY")
    run.add_argument("--vus", type=int, choices=(1, 5, 10, 20, 50), default=1)
    run.add_argument("--iterations", type=int, default=5)
    run.add_argument("--label", required=True)
    args = parser.parse_args()
    if args.command == "prepare":
        prepare(args)
    elif args.command == "up":
        environment()
        compose("up", "--detach", "--wait")
    elif args.command == "serve":
        env = {**os.environ, **environment()}
        arguments = ("--spring.config.additional-location=file:loadtest/application-loadtest.yaml "
                     "--jwt.issuer=" + ISSUER)
        return subprocess.call(["./gradlew", "bootRun", "--args=" + arguments], cwd=BACKEND, env=env)
    elif args.command == "seed":
        environment()
        with (LOCAL / "seed.sql").open() as source:
            sql(source=source)
        print("Dedicated loadtest DB restored to the initial fixture")
    elif args.command == "verify":
        verify_seed()
    elif args.command == "verify-result":
        if not args.label or any(char not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_" for char in args.label):
            raise ValueError("label must use letters, numbers, hyphens or underscores")
        verify_result(args.label)
    elif args.command == "down":
        compose("down")
    else:
        if not args.label or any(char not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_" for char in args.label):
            raise ValueError("label must use letters, numbers, hyphens or underscores")
        if args.iterations < 1:
            raise ValueError("iterations must be positive")
        return run_test(args)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except KeyboardInterrupt:
        print("Loadtest command interrupted", file=sys.stderr)
        sys.exit(130)
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        # Do not print subprocess args/output: they may contain credentials.
        print(f"Loadtest command failed: {type(error).__name__}. Check the local service and command prerequisites.", file=sys.stderr)
        if isinstance(error, ValueError):
            print(str(error), file=sys.stderr)
        sys.exit(1)
