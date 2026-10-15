#!/usr/bin/env python3
"""Dev load-test driver. Default is an offline plan; --execute explicitly enables I/O."""
import argparse
import calendar
import csv
import datetime as dt
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shlex
import shutil
import signal
import subprocess
import sys
import time
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent
BACKEND = ROOT.parent
LOCAL = ROOT / ".local"
LOCK = LOCAL / "execution.lock"


class StopRun(Exception):
    pass


def require(condition, message):
    if not condition:
        raise StopRun(message)


MONITOR_DEFAULTS = {
    "pollSeconds": 15,
    "maxMetricAgeSeconds": 45,
    "cooldownSeconds": 30,
    "recoveryTimeoutSeconds": 180,
}


def monitoring_settings(settings):
    result = {**MONITOR_DEFAULTS, **settings}
    result["hostInstances"] = [result["backendInstance"], result["databaseInstance"]]
    return result


def load_config(filename, overrides=None):
    config = json.loads(Path(filename).read_text())
    config.update(overrides or {})
    keys = {"baseUrl", "version", "referenceDate", "tradeMonthsAgo", "rpsSteps", "stageSeconds",
            "warmupSeconds", "vus", "requestTimeoutSeconds", "targetP95Ms", "monitoring"}
    require(set(config) == keys, "Use the documented config keys; namespace and private paths are automatic")
    require(config.get("version") in ("v1", "v2"), "version must be v1 or v2")
    api = urllib.parse.urlsplit(config["baseUrl"])
    require(api.scheme == "https" and api.hostname and re.search(r"\bdev\b", api.hostname)
            and not api.username and not api.password and not api.path and not api.query and not api.fragment,
            "Use an HTTPS dev API origin, without credentials or a path")
    reference = dt.date.fromisoformat(config["referenceDate"])
    require(reference.isoformat() == config["referenceDate"], "Use referenceDate=YYYY-MM-DD")
    require(reference <= dt.datetime.now(dt.timezone(dt.timedelta(hours=9))).date(), "referenceDate is in the future")
    limits = {"tradeMonthsAgo": (1, 6), "stageSeconds": (10, 3600), "warmupSeconds": (1, 600),
              "vus": (1, 500), "requestTimeoutSeconds": (1, 60)}
    for key, (minimum, maximum) in limits.items():
        require(type(config[key]) is int and minimum <= config[key] <= maximum, f"Invalid {key}")
    require(type(config["targetP95Ms"]) is int and 0 < config["targetP95Ms"] <= config["requestTimeoutSeconds"] * 1000,
            "Invalid targetP95Ms")
    rates = config["rpsSteps"]
    require(isinstance(rates, list) and 1 <= len(rates) <= 10, "Use 1..10 rpsSteps")
    for i, rate in enumerate(rates):
        require(type(rate) in (int, float) and math.isfinite(rate) and 0 < rate <= 20
                and abs(rate * 60 - round(rate * 60)) < 1e-8
                and abs(rate * config["stageSeconds"] - round(rate * config["stageSeconds"])) < 1e-8
                and rate * config["stageSeconds"] <= 5000 and (not i or rate > rates[i - 1]),
                "Use increasing RPS, whole requests/minute and whole stage counts, at most 5000/stage")
        require(rate * (config["requestTimeoutSeconds"] + 1) < 50,
                "Rate/timeout may overlap the 50 accounts; reduce rate or timeout")
    monitor = monitoring_settings(config["monitoring"])
    config["monitoring"] = monitor
    uri = urllib.parse.urlsplit(monitor["prometheusUrl"])
    require(uri.scheme in ("http", "https") and uri.hostname and not uri.username and not uri.password
            and not uri.query and not uri.fragment and uri.path in ("", "/"), "Invalid Prometheus origin")
    hosts = monitor["hostInstances"]
    require(len(hosts) == 2 and len(set(hosts)) == 2 and all(re.fullmatch(r"[a-zA-Z0-9_-]+", h) for h in hosts)
            and monitor["backendInstance"] in hosts and monitor["databaseInstance"] in hosts,
            "Set the two dev host instances and backend/database labels")
    for key, bounds in {"pollSeconds": (5, 30), "maxMetricAgeSeconds": (15, 120), "holdSeconds": (15, 120),
                        "cooldownSeconds": (15, 120), "recoveryTimeoutSeconds": (30, 600)}.items():
        require(type(monitor[key]) is int and bounds[0] <= monitor[key] <= bounds[1], f"Invalid monitoring.{key}")
    require(0 < monitor["cpuLimit"] < 1 and 0 < monitor["minAvailableMemoryRatio"] < 1,
            "Invalid resource thresholds")
    months = reference.year * 12 + reference.month - 1 - config["tradeMonthsAgo"]
    year, month = divmod(months, 12)
    trade = dt.date(year, month + 1, min(reference.day, calendar.monthrange(year, month + 1)[1]))
    return config, trade.isoformat()


def write_private(path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_name(path.name + ".tmp")
    with open(temporary, "w", opener=lambda p, f: os.open(p, f, 0o600)) as stream:
        json.dump(value, stream, indent=2, ensure_ascii=False)
        stream.write("\n")
    os.chmod(temporary, 0o600)
    temporary.replace(path)


def preparation(config, command):
    namespace = "lt496-" + config["referenceDate"].replace("-", "")
    args = [command, "--env-file", str(LOCAL / "dev.env"), "--namespace", namespace,
            "--base-url", config["baseUrl"], "--snapshot", "baseline.json"]
    if command == "tokens":
        # Fresh stage-specific tokens; the dev application's global expiration policy is unchanged.
        lifetime = config["stageSeconds"] + config["warmupSeconds"] + 4 * config["requestTimeoutSeconds"] + 300
        args += ["--token-seconds", str(lifetime)]
    result = subprocess.run([str(BACKEND / "gradlew"), "-q", "portfolioLoadtest", "--no-daemon",
                             "--no-configuration-cache", "--args=" + shlex.join(args)], cwd=BACKEND,
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    require(result.returncode == 0, f"Preparation '{command}' failed. Check private environment, baseline and DB; no credentials printed.")


class Monitor:
    def __init__(self, settings):
        self.settings = monitoring_settings(settings)
        self.exceeded_since = {}
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def vector(self, query, instances, fresh=True):
        url = self.settings["prometheusUrl"].rstrip("/") + "/api/v1/query?" + urllib.parse.urlencode({"query": query})
        try:
            with self.opener.open(url, timeout=5) as response:
                result = json.load(response)
        except Exception:
            raise StopRun("Prometheus query failed; stopping rather than assuming healthy") from None
        require(result.get("status") == "success" and result.get("data", {}).get("resultType") == "vector",
                "Prometheus returned an invalid result")
        values = {}
        for row in result["data"]["result"]:
            instance = row["metric"].get("instance")
            stamp, value = row["value"]
            require(instance not in values and math.isfinite(float(value)), "Unexpected/duplicate metric series")
            require(not fresh or 0 <= time.time() - float(stamp) <= self.settings["maxMetricAgeSeconds"],
                    "Monitoring data is stale")
            values[instance] = float(value)
        require(set(values) == set(instances), "Required dev metric series are missing")
        return values

    def sample(self):
        m = self.settings
        hosts = m["hostInstances"]
        selector = 'environment="dev",instance=~"' + "|".join(hosts) + '"'
        for job, instances in (("node", hosts), ("backend", [m["backendInstance"]])):
            query = f'up{{environment="dev",job="{job}",instance=~"' + "|".join(instances) + '"}'
            require(all(v == 1 for v in self.vector(query, instances).values()), "Dev metric target is down")
        require(all(v == 1 for v in self.vector(f'pg_up{{environment="dev",instance="{m["databaseInstance"]}"}}',
                                              [m["databaseInstance"]]).values()), "PostgreSQL is unavailable")
        # Fresh node up guards rate-expression timestamps, which are evaluation times.
        cpu = self.vector(f'1-avg by(instance)(rate(node_cpu_seconds_total{{{selector},mode="idle"}}[1m]))', hosts, fresh=False)
        available = self.vector(f'node_memory_MemAvailable_bytes{{{selector}}}', hosts)
        total = self.vector(f'node_memory_MemTotal_bytes{{{selector}}}', hosts)
        require(all(total[h] > 0 for h in hosts), "Invalid memory metrics")
        memory = {h: available[h] / total[h] for h in hosts}
        require(all(0 <= cpu[h] <= 1 and 0 <= memory[h] <= 1 for h in hosts), "Invalid CPU/memory metrics")
        pending_values = self.vector(f'sum by(instance)(hikaricp_connections_pending{{environment="dev",instance="{m["backendInstance"]}"}})',
                                     [m["backendInstance"]], fresh=False)
        pending = sum(pending_values.values())
        now = time.monotonic()
        above = {}
        for h in hosts:
            above["cpu:" + h] = cpu[h] >= m["cpuLimit"]
            above["memory:" + h] = memory[h] <= m["minAvailableMemoryRatio"]
        for key, bad in above.items():
            if not bad:
                self.exceeded_since.pop(key, None)
            else:
                since = self.exceeded_since.setdefault(key, now)
                require(now - since < m["holdSeconds"], "Sustained server resource limit: " + key)
        return {"time": dt.datetime.now(dt.timezone.utc).isoformat(), "cpu": cpu,
                "availableMemoryRatio": memory, "dbConnectionsPending": pending,
                "quiet": not any(above.values()) and pending == 0}

    def wait_quiet(self):
        deadline = time.monotonic() + self.settings["recoveryTimeoutSeconds"]
        quiet_since = None
        while time.monotonic() < deadline:
            state = self.sample()
            if state["quiet"]:
                quiet_since = quiet_since or time.monotonic()
                if time.monotonic() - quiet_since >= self.settings["cooldownSeconds"]:
                    return
            else:
                quiet_since = None
            time.sleep(self.settings["pollSeconds"])
        raise StopRun("Server did not remain quiet; no restore or next stage was started")


def stop_child(child, timeout):
    if child.poll() is None:
        child.terminate()
        try:
            child.wait(timeout=timeout + 5)
        except subprocess.TimeoutExpired:
            child.kill()
            child.wait()


def run_stage(config, config_file, rps, directory, monitor):
    summary = directory / f"rps-{rps:g}.json"
    log = directory / f"rps-{rps:g}.log"
    env = dict(os.environ, CONFIG_FILE=str(config_file), RUN_RPS=str(rps),
               SUMMARY_FILE=str(summary.relative_to(ROOT)), K6_NO_USAGE_REPORT="true")
    remote_write = os.environ.get("PORTFOLIO_LOADTEST_REMOTE_WRITE") == "true"
    # Only output settings may be inherited; arbitrary k6 options must not change the workload.
    output_keys = {"K6_PROMETHEUS_RW_SERVER_URL", "K6_PROMETHEUS_RW_TREND_STATS"} if remote_write else set()
    for key in list(env):
        if key.startswith("K6_") and key not in output_keys | {"K6_NO_USAGE_REPORT"}:
            env.pop(key)
        if key in {"PORTFOLIO_LOADTEST_DEV_ENV", "DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "JWT_ISSUER"}:
            env.pop(key)
    command = ["k6", "run", "--quiet", "--no-color"]
    if remote_write:
        destination = urllib.parse.urlsplit(env.get("K6_PROMETHEUS_RW_SERVER_URL", ""))
        require(destination.scheme in ("http", "https") and destination.hostname
                and not destination.username and not destination.password and not destination.query and not destination.fragment,
                "Set a credential-free Prometheus remote-write URL")
        env["K6_PROMETHEUS_RW_TREND_STATS"] = "p(95),p(99),avg,min,max"
        # Do not use --tag: it replaces the script's version/date/rate tags.
        env["LOADTEST_ID"] = directory.name
        command += ["-o", "experimental-prometheus-rw"]
    command.append(f"transactions-{config['version']}.js")
    samples = []
    interruption = None
    with open(log, "w", opener=lambda p, f: os.open(p, f, 0o600)) as output:
        child = subprocess.Popen(command,
                                 cwd=ROOT, env=env, stdout=output, stderr=subprocess.STDOUT)
        try:
            next_check = 0
            deadline = time.monotonic() + config["warmupSeconds"] + config["stageSeconds"] + 5 * config["requestTimeoutSeconds"] + 60
            while child.poll() is None:
                require(time.monotonic() < deadline, "k6 exceeded the stage execution deadline")
                if time.monotonic() >= next_check:
                    samples.append(monitor.sample())
                    next_check = time.monotonic() + config["monitoring"]["pollSeconds"]
                time.sleep(0.5)
        except StopRun as failure:
            interruption = str(failure)
        finally:
            stop_child(child, config["requestTimeoutSeconds"])
    if not summary.exists():
        write_private(summary, {"outcome": "incomplete", "stopReason": interruption or "k6 did not produce a summary", "resourceSamples": samples})
        raise StopRun(interruption or "k6 did not produce a summary; inspect its private log")
    os.chmod(summary, 0o600)
    report = json.loads(summary.read_text())
    if interruption:
        report["outcome"] = "protection_stop"
        report["stopReason"] = interruption
    report["resourceSamples"] = samples
    report["k6ExitCode"] = child.returncode
    write_private(summary, report)
    return report


def append_comparison(report, run_id):
    path = LOCAL / "results" / "comparison.csv"
    keys = ["runId", "comparisonKey", "version", "tradeDate", "rps", "expected", "started", "successful",
            "errors", "droppedIterations", "stageSeconds", "startedPerSecond", "successfulPerSecond", "p95Ms", "accountsUsed", "outcome"]
    fields = {k: report.get(k) for k in keys}
    fields.update(runId=run_id, stageSeconds=report["config"]["stageSeconds"], p95Ms=report.get("latency", {}).get("p(95)"))
    # A different version may be compared only with matching fixture AND workload settings.
    comparable = {k: report["config"][k] for k in ("baseUrl", "referenceDate", "tradeMonthsAgo", "stageSeconds",
                  "warmupSeconds", "vus", "requestTimeoutSeconds", "targetP95Ms")}
    comparable.update(fixtureHash=report["fixtureHash"], rps=report["rps"], accountPlan="round-robin-50-v1")
    fields["comparisonKey"] = hashlib.sha256(json.dumps(comparable, sort_keys=True).encode()).hexdigest()
    existed = path.exists()
    with open(path, "a", newline="", opener=lambda p, f: os.open(p, f, 0o600)) as stream:
        writer = csv.DictWriter(stream, fieldnames=keys)
        if not existed:
            writer.writeheader()
        writer.writerow(fields)


def export_results(directory, destination, run_id):
    """Publish this run's reports only; never copy logs, tokens, environment or baseline."""
    destination.mkdir(parents=True, exist_ok=True)
    for file in directory.iterdir():
        if file.name in ("config.json", "results.json") or re.fullmatch(r"rps-[0-9.]+\.json", file.name):
            shutil.copyfile(file, destination / file.name)
    comparison = LOCAL / "results" / "comparison.csv"
    if comparison.exists():
        with comparison.open(newline="") as source:
            rows = csv.DictReader(source)
            with (destination / "comparison.csv").open("w", newline="") as output:
                writer = csv.DictWriter(output, fieldnames=rows.fieldnames)
                writer.writeheader()
                writer.writerows(row for row in rows if row["runId"] == run_id)


def execute(config, config_file, monitor, artifacts=None):
    require(not LOCK.exists(), "Previous execution/recovery marker exists. Check server/DB, then use --acknowledge-recovery")
    run_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + uuid.uuid4().hex[:8]
    directory = LOCAL / "results" / run_id
    directory.mkdir(parents=True, mode=0o700)
    with open(LOCK, "x", opener=lambda p, f: os.open(p, f, 0o600)) as stream:
        json.dump({"pid": os.getpid(), "runId": run_id, "status": "running"}, stream)
    reports = []
    try:
        frozen_config = directory / "config.json"
        write_private(frozen_config, config)
        for rps in config["rpsSteps"]:
            monitor.wait_quiet()
            preparation(config, "check")
            preparation(config, "restore")
            preparation(config, "tokens")
            print(f'{config["version"]}: {rps:g} RPS, {config["stageSeconds"]}s; warmup first', flush=True)
            report = run_stage(config, frozen_config, rps, directory, monitor)
            reports.append(report)
            write_private(directory / "results.json", {"runId": run_id, "config": config, "stages": reports})
            append_comparison(report, run_id)
            print(f'  {report["outcome"]}: {report["successful"]}/{report["expected"]} successful; '
                  f'p95={report["latency"].get("p(95)", "n/a")}ms', flush=True)
            require(report["k6ExitCode"] == 0 and report["outcome"] == "exploratory_pass",
                    "Stage did not pass; no higher rate or automatic restore will run")
        monitor.wait_quiet()
        preparation(config, "check")
        LOCK.unlink()
        print("Exploration complete. This does not establish the absolute maximum RPS.")
    except BaseException as failure:
        reason = str(failure) if isinstance(failure, StopRun) else type(failure).__name__
        write_private(LOCK, {"runId": run_id, "status": "recovery_required", "reason": reason})
        write_private(directory / "results.json", {"runId": run_id, "config": config, "stages": reports, "stopReason": reason})
        raise
    finally:
        if artifacts is not None:
            export_results(directory, artifacts, run_id)
        print(f'Results: {directory.relative_to(ROOT)}', flush=True)


SCRIPT_VERSIONS = {
    "backend/loadtest/transactions-v1.js": "v1",
    "backend/loadtest/transactions-v2.js": "v2",
}

PARAMETERS = { "REFERENCE_DATE": "referenceDate", "TRADE_MONTHS_AGO": "tradeMonthsAgo",
    "RPS_STEPS": "rpsSteps", "STAGE_SECONDS": "stageSeconds", "WARMUP_SECONDS": "warmupSeconds",
    "VUS": "vus", "REQUEST_TIMEOUT_SECONDS": "requestTimeoutSeconds", "TARGET_P95_MS": "targetP95Ms",
}


def actions_configuration(params_json, environment):
    try:
        params = json.loads(params_json)
    except (ValueError, TypeError):
        raise StopRun("params must be a JSON object") from None
    require(isinstance(params, dict) and set(params) <= set(PARAMETERS) | {"ACTION"},
                   "Unknown portfolio parameter; use the documented ACTION/date/rate/duration keys; choose the version with script")
    action = params.get("ACTION", "plan")
    require(action in ("plan", "capture", "execute", "acknowledge-recovery"), "Invalid ACTION")
    script = environment.get("K6_SCRIPT")
    require(script in SCRIPT_VERSIONS, "Choose transactions-v1.js or transactions-v2.js in the workflow script input")
    overrides = {PARAMETERS[key]: value for key, value in params.items() if key in PARAMETERS}
    overrides["version"] = SCRIPT_VERSIONS[script]
    if environment.get("BASE_URL"):
        overrides["baseUrl"] = environment["BASE_URL"]
    else:
        require(action == "plan", "Set DEV_BASE_URL in development environment variables")
    if environment.get("PORTFOLIO_PROMETHEUS_URL"):
        settings = json.loads((ROOT / "loadtest-config.json").read_text())["monitoring"]
        overrides["monitoring"] = {**settings, "prometheusUrl": environment["PORTFOLIO_PROMETHEUS_URL"]}
    config, trade_date = load_config(ROOT / "loadtest-config.json", overrides)
    if action == "execute":
        destination = urllib.parse.urlsplit(environment.get("K6_PROMETHEUS_RW_SERVER_URL", ""))
        require(destination.scheme in ("http", "https") and destination.hostname
                       and not destination.username and not destination.password
                       and not destination.query and not destination.fragment,
                       "Set a credential-free K6_PROMETHEUS_RW_SERVER_URL in development variables")
    # Leave time for quiet checks, bounded DB preparation and cancellation before the 120-minute job limit.
    budget = len(config["rpsSteps"]) * (config["stageSeconds"] + config["warmupSeconds"]
             + 5 * config["requestTimeoutSeconds"] + config["monitoring"]["recoveryTimeoutSeconds"] + 420)
    require(budget <= 6600, "Selected stages exceed the Actions budget; use fewer RPS steps or shorter durations")
    return action, config, trade_date


def state_path(environment):
    configured = environment.get("PORTFOLIO_LOADTEST_STATE_DIR")
    path = Path(configured) if configured else Path.home() / ".local/state/daynomy-portfolio-loadtest-496"
    workspace = Path(environment.get("GITHUB_WORKSPACE", ROOT.parent.parent)).resolve()
    require(path.is_absolute(), "Use an absolute PORTFOLIO_LOADTEST_STATE_DIR")
    path = path.resolve()
    require(not path.is_relative_to(workspace) and path not in (Path("/"), Path.home().resolve()),
                   "Keep persistent state outside the checkout, in a dedicated private directory")
    if environment.get("RUNNER_TEMP"):
        require(not path.is_relative_to(Path(environment["RUNNER_TEMP"]).resolve()),
                       "Persistent state must not be in RUNNER_TEMP")
    return path


def attach_state(environment):
    state = state_path(environment)
    state.mkdir(parents=True, exist_ok=True, mode=0o700)
    state.chmod(0o700)
    identity = state / "runner.json"
    name = environment.get("RUNNER_NAME", "")
    require(bool(name), "RUNNER_NAME is required; use the dedicated loadtest runner")
    if identity.exists():
        require(json.loads(identity.read_text()).get("name") == name,
                       "Persistent state belongs to another runner; do not capture a new baseline automatically")
    else:
        write_private(identity, {"name": name})
    local = ROOT / ".local"
    if local.is_symlink():
        require(local.resolve() == state, "Existing .local link points to another state directory")
    else:
        require(not local.exists(), "Move existing .local data privately before attaching Actions state")
        local.symlink_to(state, target_is_directory=True)
    return state


def cleanup(environment):
    local = ROOT / ".local"
    if not local.is_symlink():
        return
    state = state_path(environment)
    require(local.resolve() == state, "Refusing to clean an unrelated .local link")
    for name in ("dev.env", "users.json", "warmup-users.json"):
        (state / name).unlink(missing_ok=True)
    local.unlink()


def write_summary(output, config, action, trade_date, outcome):
    metadata = {"action": action, "outcome": outcome, "version": config["version"], "tradeDate": trade_date,
                "rpsSteps": config["rpsSteps"], "stageSeconds": config["stageSeconds"],
                "scriptCommit": os.environ.get("GITHUB_SHA"), "runner": os.environ.get("RUNNER_NAME"),
                "workflowRun": os.environ.get("GITHUB_RUN_ID")}
    write_private(output / "request.json", metadata)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
            summary.write(f"### Portfolio transaction load test\n\n{config['version']} / {trade_date} / {action}: {outcome}\n\n")
            result_file = output / "results.json"
            if result_file.exists():
                summary.write("| RPS | Success / planned | p95 (ms) | Result |\n| --- | --- | --- | --- |\n")
                for stage in json.loads(result_file.read_text()).get("stages", []):
                    summary.write(f"| {stage['rps']} | {stage['successful']} / {stage['expected']} | "
                                  f"{stage.get('latency', {}).get('p(95)', 'n/a')} | {stage['outcome']} |\n")


def perform_action(config, config_file, trade_date, action, artifacts=None):
    if action == "seed-sql":
        sql = (ROOT / "sql" / "seed-portfolios.sql").read_text()
        print(sql.replace("-- UPDATE lt_config SET as_of = DATE '2026-10-15';",
                          f"UPDATE lt_config SET as_of = DATE '{config['referenceDate']}';"))
        return
    if action == "plan":
        print(json.dumps({"mode": "offline_plan", "version": config["version"], "tradeDate": trade_date,
                          "vus": config["vus"], "stages": [{"rps": rate, "seconds": config["stageSeconds"],
                          "requests": round(rate * config["stageSeconds"])} for rate in config["rpsSteps"]]}, indent=2))
        return
    monitor = Monitor(config["monitoring"])
    if action == "acknowledge-recovery":
        require(LOCK.exists(), "No recovery marker exists")
        marker = json.loads(LOCK.read_text())
        if marker.get("pid"):
            try:
                os.kill(marker["pid"], 0)
            except ProcessLookupError:
                pass
            else:
                raise StopRun("The recorded runner process is still alive")
        monitor.wait_quiet()
        preparation(config, "check")
        LOCK.unlink()
        print("Recovery acknowledged. No data restored and no load started.")
    elif action == "capture":
        require(not LOCK.exists(), "Resolve the previous execution marker before capture")
        monitor.wait_quiet()
        preparation(config, "capture")
        print("Baseline captured privately. No load started.")
    else:
        execute(config, config_file, monitor, artifacts=artifacts)


def run_actions():
    action, config, trade_date = actions_configuration(os.environ.get("K6_PARAMS_JSON", "{}"), os.environ)
    output = Path(os.environ["PORTFOLIO_LOADTEST_RESULTS_DIR"]).resolve()
    require(not output.is_relative_to(ROOT / ".local"), "Keep published reports separate from private state")
    output.mkdir(parents=True, exist_ok=True)
    outcome = "failed"
    attached = False
    try:
        if action == "plan":
            print(json.dumps({"mode": "offline_plan", "config": config, "tradeDate": trade_date}, indent=2))
            outcome = "plan_only"
            return
        state = attach_state(os.environ)
        attached = True
        require(not output.is_relative_to(state) and not state.is_relative_to(output),
                       "Published reports and private state must use separate directories")
        require(action == "acknowledge-recovery" or not LOCK.exists(),
                       "Recovery marker exists; inspect server/DB before acknowledge-recovery")
        require(action == "capture" or (state / "baseline.json").is_file(),
                       "Missing original baseline; seed SQL manually, then run ACTION=capture once")
        secret = os.environ.get("PORTFOLIO_LOADTEST_DEV_ENV", "")
        require(bool(secret.strip()), "Set PORTFOLIO_LOADTEST_DEV_ENV in development environment secrets")
        env_file = state / "dev.env"
        with open(env_file, "w", opener=lambda p, f: os.open(p, f, 0o600)) as stream:
            stream.write(secret)
            stream.write("\n")
        env_file.chmod(0o600)
        config_file = state / "actions-config.json"
        write_private(config_file, config)
        if action == "execute":
            os.environ["PORTFOLIO_LOADTEST_REMOTE_WRITE"] = "true"
        perform_action(config, config_file, trade_date, action, artifacts=output)
        outcome = "completed"
    finally:
        if attached:
            cleanup(os.environ)
        write_summary(output, config, action, trade_date, outcome)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default=str(ROOT / "loadtest-config.json"))
    commands = parser.add_mutually_exclusive_group()
    commands.add_argument("--actions", action="store_true", help="Read workflow params from K6_PARAMS_JSON")
    commands.add_argument("--cleanup", action="store_true", help="Remove private Actions credentials and checkout link only")
    commands.add_argument("--plan", action="store_true", help="Offline validation only (default)")
    commands.add_argument("--seed-sql", action="store_true", help="Print SQL with the configured reference date; does not execute")
    commands.add_argument("--capture", action="store_true", help="Read the seeded dev cohort into a private baseline")
    commands.add_argument("--execute", action="store_true", help="Connect to dev and run the configured version")
    commands.add_argument("--acknowledge-recovery", action="store_true", help="After manual server/DB inspection, check quiet state and clear the stop marker")
    options = parser.parse_args()
    if options.cleanup:
        cleanup(os.environ)
        return
    if options.actions:
        run_actions()
        return
    config_file = Path(options.config).resolve()
    config, trade_date = load_config(config_file)
    action = next((name for name in ("seed-sql", "capture", "execute", "acknowledge-recovery")
                   if getattr(options, name.replace("-", "_"))), "plan")
    perform_action(config, config_file, trade_date, action)


if __name__ == "__main__":
    def interrupted(_signal, _frame):
        raise KeyboardInterrupt()

    signal.signal(signal.SIGTERM, interrupted)
    try:
        main()
    except (Exception, KeyboardInterrupt) as error:
        print("Stopped: " + (str(error) if isinstance(error, StopRun) else type(error).__name__), file=sys.stderr)
        sys.exit(1)
