#!/usr/bin/env python3
"""Bounded dev-only V1/V2 idempotency verification. Never prints credentials/tokens.

Requires the existing dev env file and psql; creates only new synthetic members.
Always backs up and removes its fixtures, including committed idempotency records.
"""

import argparse
import base64
import concurrent.futures
import datetime as dt
import hashlib
import hmac
import http.cookies
import http.server
import json
import os
from pathlib import Path
import shlex
import socket
import statistics
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from zoneinfo import ZoneInfo

V1 = "/api/users/me/portfolio/transactions"
V2 = "/api/v2/users/me/portfolio/transactions"


class Probe:
    def __init__(self, args):
        assert args.run, "Explicit --run is required"
        assert args.base_url == "https://dev.daynomy.com", "Only the dev origin is allowed"
        self.base = args.base_url
        self.output = Path(args.output)
        self.output.mkdir(mode=0o700, parents=True, exist_ok=False)
        self.config = {}
        for line in Path(args.env_file).read_text().splitlines():
            line = line.strip().removeprefix("export ")
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values = shlex.split(value)
                self.config[key] = values[0] if values else ""
        url = urllib.parse.urlsplit(self.config["DB_URL"].removeprefix("jdbc:"))
        assert url.path == "/daynomy_dev", "Only daynomy_dev is allowed"
        self.env = os.environ.copy()
        self.env.update(
            PGHOST=url.hostname, PGPORT=str(url.port or 5432),
            PGDATABASE="daynomy_dev", PGUSER=self.config["DB_USERNAME"],
            PGPASSWORD=self.config["DB_PASSWORD"], PGCONNECT_TIMEOUT="5",
            PGAPPNAME="daynomy_idempotency_probe",
        )
        self.run = "idem-after-" + uuid.uuid4().hex[:12]
        self.today = dt.datetime.now(ZoneInfo("Asia/Seoul")).date().isoformat()
        self.members = []
        self.scale = args.scale
        self.report = {"run": self.run, "origin": self.base, "cases": [],
                       "started_at": dt.datetime.now(dt.timezone.utc).isoformat()}
        assert self.query("SELECT to_regclass('public.portfolio_transaction_requests') IS NOT NULL") == "t", "Deploy V98 before probing"
        self.asset = int(self.query(
            "SELECT id FROM assets WHERE market IN ('KOSPI','KOSDAQ') "
            "AND listed=true ORDER BY id LIMIT 1"))

    def query(self, sql, write=False, env=None):
        sql = ("BEGIN" + ("" if write else " READ ONLY") + "; "
               "SET LOCAL statement_timeout='10s'; SET LOCAL lock_timeout='2s'; "
               + sql + "; COMMIT;")
        result = subprocess.run(
            ["psql", "-X", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-c", sql],
            env=env or self.env, capture_output=True, text=True, timeout=20,
        )
        if result.returncode:
            raise RuntimeError("Database probe failed; credential-bearing details withheld")
        return result.stdout.strip()

    def metrics(self, label):
        with urllib.request.urlopen("http://127.0.0.1:8081/actuator/prometheus", timeout=5) as r:
            text = r.read().decode()
        (self.output / (label + ".prom")).write_text(text)
        totals = {}
        for line in text.splitlines():
            if line.startswith("#") or not line.strip():
                continue
            name, value = line.rsplit(" ", 1)
            name = name.split("{")[0]
            if name in {"jvm_gc_pause_seconds_count", "jvm_gc_pause_seconds_sum",
                        "process_uptime_seconds", "hikaricp_connections_pending",
                        "hikaricp_connections_timeout_total"}:
                totals[name] = totals.get(name, 0) + float(value)
        return totals

    def fixture(self, label):
        provider = self.run + "-" + label
        nickname = "il" + uuid.uuid4().hex[:16]
        member = int(self.query(
            "INSERT INTO members(provider,provider_id,email,nickname,role,status,created_at,updated_at) "
            f"VALUES('GOOGLE','{provider}','{provider}@example.invalid','{nickname}',"
            "'USER','ACTIVE',now(),now()) RETURNING id", write=True))
        self.members.append(member)
        (self.output / "member-manifest.json").write_text(json.dumps(self.members))
        enc = lambda value: base64.urlsafe_b64encode(value).rstrip(b"=")
        now = int(time.time())
        claims = {"iss": self.config.get("JWT_ISSUER", "daynomy"), "sub": str(member),
                  "iat": now, "exp": now + 600, "jti": str(uuid.uuid4()),
                  "sid": str(uuid.uuid4()), "token_type": "access", "role": "USER"}
        data = enc(b'{"alg":"HS256","typ":"JWT"}') + b"." + enc(json.dumps(claims).encode())
        secret = self.config["JWT_SECRET"]
        signing_key = base64.b64decode(secret + "=" * ((4 - len(secret) % 4) % 4))
        token = (data + b"." + enc(hmac.new(signing_key, data, hashlib.sha256).digest())).decode()
        request = urllib.request.Request(self.base + "/api/auth/csrf", headers={"Cookie": "access_token=" + token})
        with urllib.request.urlopen(request, timeout=10) as response:
            csrf = json.load(response)
            cookies = http.cookies.SimpleCookie()
            for cookie in response.headers.get_all("Set-Cookie", []):
                cookies.load(cookie)
        return {"member": member, "csrf": csrf,
                "cookie": "access_token=" + token + "; "
                + "; ".join(k + "=" + v.value for k, v in cookies.items())}

    def body(self, quantity=1):
        return {"assetId": self.asset, "type": "BUY", "tradeDate": self.today,
                "quantity": quantity, "unitPrice": 100, "fee": 0, "tax": 0}

    def headers(self, fixture, key):
        headers = {"Content-Type": "application/json", "Cookie": fixture["cookie"],
                   fixture["csrf"]["headerName"]: fixture["csrf"]["token"]}
        if key is not None:
            headers["Idempotency-Key"] = key
        return headers

    def send(self, fixture, endpoint, key, quantity=1):
        started = time.perf_counter()
        request = urllib.request.Request(self.base + endpoint, data=json.dumps(self.body(quantity)).encode(),
                                         headers=self.headers(fixture, key))
        try:
            with urllib.request.urlopen(request, timeout=15) as response:
                status, body = response.status, json.load(response)
        except urllib.error.HTTPError as error:
            status, body = error.code, json.load(error)
        return {"status": status, "body": body,
                "elapsed_ms": round((time.perf_counter() - started) * 1000, 3)}

    def state(self, fixture):
        member = fixture["member"]
        return json.loads(self.query(
            "SELECT json_build_object('histories',(SELECT count(*) FROM portfolio_holding_histories h "
            f"JOIN portfolios p ON p.id=h.portfolio_id WHERE p.member_id={member}),"
            "'quantity',coalesce((SELECT sum(h.quantity) FROM portfolio_holdings h "
            f"JOIN portfolios p ON p.id=h.portfolio_id WHERE p.member_id={member}),0),"
            f"'requests',(SELECT count(*) FROM portfolio_transaction_requests WHERE member_id={member}))"))

    def record(self, label, fixture, responses, expected, statuses):
        state = self.state(fixture)
        case = {"name": label, "responses": responses, "db": state}
        self.report["cases"].append(case)
        self.save()
        assert [r["status"] for r in responses] == statuses, label + " unexpected HTTP status"
        assert state == expected, label + " unexpected persisted effects"
        print(json.dumps({"case": label, "statuses": statuses, "db": state}), flush=True)

    def concurrent(self, fixture, endpoints, key):
        gate = threading.Barrier(len(endpoints))
        def work(endpoint):
            gate.wait(timeout=5)
            return self.send(fixture, endpoint, key)
        with concurrent.futures.ThreadPoolExecutor(max_workers=len(endpoints)) as pool:
            return list(pool.map(work, endpoints))

    def run_cases(self):
        self.report["metrics_before"] = self.metrics("before")
        for version, endpoint in [("v1", V1), ("v2", V2)]:
            for mode in ["sequential", "concurrent", "different_keys", "changed_payload"]:
                fixture, key = self.fixture(version + "-" + mode), str(uuid.uuid4())
                if mode == "concurrent":
                    responses = self.concurrent(fixture, [endpoint] * 4, key)
                else:
                    responses = [self.send(fixture, endpoint, key)]
                    responses.append(self.send(fixture, endpoint,
                        str(uuid.uuid4()) if mode == "different_keys" else key,
                        2 if mode == "changed_payload" else 1))
                count = 2 if mode == "different_keys" else 1
                expected = {"histories": count, "quantity": count, "requests": count}
                statuses = [201, 409] if mode == "changed_payload" else [201] * len(responses)
                self.record(version + "-" + mode, fixture, responses, expected, statuses)
                if mode in {"sequential", "concurrent"}:
                    assert all(r["body"] == responses[0]["body"] for r in responses), "Different replay results"
        fixture, key = self.fixture("cross-version"), str(uuid.uuid4())
        responses = self.concurrent(fixture, [V1, V2, V1, V2], key)
        self.record("cross-version", fixture, responses,
                    {"histories": 1, "quantity": 1, "requests": 1}, [201] * 4)
        assert all(r["body"] == responses[0]["body"] for r in responses)
        self.response_loss()
        self.forced_lock()
        if self.scale:
            self.scale_cases()
        self.report["metrics_after"] = self.metrics("after")
        self.report["finished_at"] = dt.datetime.now(dt.timezone.utc).isoformat()
        self.report["duplicate_effects"] = 0
        self.save()

    def response_loss(self):
        probe, fixture, key = self, self.fixture("response-drop"), str(uuid.uuid4())
        upstream = {}
        class Drop(http.server.BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass
            def do_POST(self):
                body = self.rfile.read(int(self.headers["Content-Length"]))
                with urllib.request.urlopen(urllib.request.Request(probe.base + V2, data=body,
                    headers=probe.headers(fixture, key)), timeout=15) as response:
                    upstream.update(status=response.status, body=json.load(response))
                self.connection.shutdown(socket.SHUT_RDWR)
                self.connection.close()
        server = http.server.HTTPServer(("127.0.0.1", 0), Drop)
        worker = threading.Thread(target=server.serve_forever)
        worker.start()
        error_name = None
        try:
            try:
                urllib.request.urlopen(urllib.request.Request(
                    f"http://127.0.0.1:{server.server_port}" + V2,
                    data=json.dumps(self.body()).encode(), headers=self.headers(fixture, key)), timeout=15)
            except Exception as error:
                error_name = type(error).__name__
            assert upstream.get("status") == 201 and error_name == "RemoteDisconnected"
            assert self.state(fixture) == {"histories": 1, "quantity": 1, "requests": 1}
            retry = self.send(fixture, V2, key)
            assert retry["body"] == upstream["body"]
            self.record("response-drop", fixture, [retry],
                        {"histories": 1, "quantity": 1, "requests": 1}, [201])
            self.report["response_loss"] = {"upstream_status": 201, "client_error": error_name,
                                            "same_response": True}
        finally:
            server.shutdown()
            server.server_close()
            worker.join(timeout=5)

    def forced_lock(self):
        fixture, key = self.fixture("forced-lock"), str(uuid.uuid4())
        assert self.send(fixture, V2, str(uuid.uuid4()))["status"] == 201
        portfolio = int(self.query(f"SELECT id FROM portfolios WHERE member_id={fixture['member']}"))
        holder_name = "idem_hold_" + uuid.uuid4().hex[:12]
        env = dict(self.env, PGAPPNAME=holder_name)
        sql = ("BEGIN; SET LOCAL statement_timeout='6s'; SET LOCAL lock_timeout='1s'; "
               f"SELECT 1 FROM portfolios WHERE id={portfolio} FOR UPDATE; SELECT pg_sleep(3); COMMIT;")
        holder = subprocess.Popen(["psql", "-X", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-c", sql],
                                  env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        samples, stop = [], threading.Event()
        def sample():
            while not stop.is_set():
                sql = ("WITH RECURSIVE affected(pid) AS (SELECT pid FROM pg_stat_activity "
                       f"WHERE application_name='{holder_name}' UNION SELECT a.pid FROM pg_stat_activity a "
                       "JOIN affected b ON b.pid=ANY(pg_blocking_pids(a.pid))) "
                       "SELECT count(*) FROM pg_stat_activity WHERE pid IN (SELECT pid FROM affected) "
                       "AND wait_event_type='Lock'")
                samples.append(int(self.query(sql)))
                stop.wait(.1)
        sampler = threading.Thread(target=sample)
        try:
            for _ in range(25):
                if self.query("SELECT count(*) FROM pg_stat_activity "
                              f"WHERE application_name='{holder_name}' AND wait_event='PgSleep'") == "1":
                    break
                time.sleep(.05)
            else:
                raise RuntimeError("Lock holder not observed; no blocked requests sent")
            sampler.start()
            responses = self.concurrent(fixture, [V1, V2], key)
            self.record("forced-lock", fixture, responses,
                        {"histories": 2, "quantity": 2, "requests": 2}, [201, 201])
            self.report["synthetic_lock"] = {"hold_seconds": 3, "peak_waiters": max(samples, default=0),
                                               "response_ms": [r["elapsed_ms"] for r in responses]}
        finally:
            stop.set()
            if sampler.ident is not None:
                sampler.join(timeout=5)
            if holder.poll() is None:
                holder.wait(timeout=8)
            assert holder.returncode == 0

    def scale_cases(self):
        self.report["scale"] = []
        for size in [100, 1000, 10000]:
            fixture = self.fixture("scale-" + str(size))
            assert self.send(fixture, V2, str(uuid.uuid4()))["status"] == 201
            portfolio = int(self.query(f"SELECT id FROM portfolios WHERE member_id={fixture['member']}"))
            columns = self.query("SELECT string_agg(column_name,',' ORDER BY ordinal_position) "
                                 "FROM information_schema.columns WHERE table_name='portfolio_holding_histories' "
                                 "AND column_name<>'id' AND is_generated='NEVER'").split(",")
            expressions = []
            for column in columns:
                if column in {"created_at", "updated_at"}:
                    value = "now()+gs.i*interval '1 microsecond'"
                elif column == "quantity":
                    value = "gs.i"
                elif column == "average_purchase_price":
                    value = "100"
                else:
                    value = "h." + column
                expressions.append(value)
            self.query("INSERT INTO portfolio_holding_histories(" + ",".join(columns) + ") SELECT "
                       + ",".join(expressions) + " FROM portfolio_holding_histories h CROSS JOIN "
                       + f"generate_series(1,{size - 1}) AS gs(i) WHERE h.id=(SELECT min(id) "
                       + f"FROM portfolio_holding_histories WHERE portfolio_id={portfolio}); "
                       + f"UPDATE portfolio_holdings SET quantity={size} WHERE portfolio_id={portfolio}", write=True)
            assert self.state(fixture) == {"histories": size, "quantity": size, "requests": 1}
            before = self.metrics(f"scale-{size}-before")
            responses = []
            for _ in range(10):
                response = self.send(fixture, V2, str(uuid.uuid4()))
                responses.append(response)
                assert response["status"] == 201 and response["elapsed_ms"] < 3000
                time.sleep(.5)
            after = self.metrics(f"scale-{size}-after")
            self.record("scale-" + str(size), fixture, responses,
                        {"histories": size + 10, "quantity": size + 10, "requests": 11}, [201] * 10)
            self.report["scale"].append({"histories": size, "requests": 10, "concurrency": 1,
                "median_ms": statistics.median(r["elapsed_ms"] for r in responses),
                "max_ms": max(r["elapsed_ms"] for r in responses),
                "metrics_before": before, "metrics_after": after})

    def save(self):
        (self.output / "results.json").write_text(json.dumps(self.report, indent=2))

    def cleanup(self):
        if not self.members:
            return
        ids = ",".join(str(x) for x in self.members)
        condition = f"id IN ({ids}) AND provider_id LIKE '{self.run}-%' AND email=provider_id||'@example.invalid'"
        assert int(self.query("SELECT count(*) FROM members WHERE " + condition)) == len(self.members)
        pids = f"SELECT id FROM portfolios WHERE member_id IN ({ids})"
        tables = {"members": "id IN (" + ids + ")", "portfolios": "member_id IN (" + ids + ")",
                  "portfolio_holdings": f"portfolio_id IN ({pids})",
                  "portfolio_holding_histories": f"portfolio_id IN ({pids})",
                  "portfolio_daily_snapshots": f"portfolio_id IN ({pids})",
                  "portfolio_transaction_requests": "member_id IN (" + ids + ")"}
        backup = {}
        for table, where in tables.items():
            backup[table] = json.loads(self.query(f"SELECT coalesce(json_agg(t),'[]'::json) FROM {table} t WHERE {where}"))
        path = self.output / "fixture-backup.json"
        path.write_text(json.dumps(backup))
        path.chmod(0o600)
        self.query(f"DELETE FROM portfolios WHERE member_id IN ({ids}); DELETE FROM members WHERE {condition}", write=True)
        remaining = int(self.query(f"SELECT count(*) FROM members WHERE id IN ({ids})"))
        assert remaining == 0
        self.report["cleanup"] = {"members_removed": len(self.members), "members_remaining": remaining}
        self.save()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--scale", action="store_true", help="Also run bounded 100/1000/10000-history probes")
    parser.add_argument("--env-file", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--base-url", default="https://dev.daynomy.com")
    probe = Probe(parser.parse_args())
    try:
        probe.run_cases()
    finally:
        probe.cleanup()


if __name__ == "__main__":
    main()
