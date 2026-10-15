#!/usr/bin/env python3
"""Sequential, local-only comparison. JFR runs are separate from timing runs."""

import argparse
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import time
from urllib.request import urlopen

from manage import BACKEND, LOCAL, ROOT

METRICS = "http://127.0.0.1:8497/actuator/prometheus"


def metrics():
    with urlopen(METRICS, timeout=5) as response:
        body = response.read().decode()
    result = {}
    names = (
        "process_cpu_time_ns_total", "process_cpu_seconds_total", "process_cpu_usage",
        "system_cpu_usage", "hikaricp_connections_active", "hikaricp_connections_pending",
        "hikaricp_connections_max", "hikaricp_connections_timeout_total",
        "hikaricp_connections_acquire_seconds_count", "hikaricp_connections_acquire_seconds_sum",
        "jvm_gc_pause_seconds_sum", "jvm_memory_used_bytes", "jvm_memory_max_bytes",
    )
    for line in body.splitlines():
        if line.startswith("#"):
            continue
        match = re.match(r"([^ {]+)(?:\{([^}]*)\})?\s+([^ ]+)", line)
        if match and match[1] in names:
            if match[1].startswith("jvm_memory_") and 'area="heap"' not in (match[2] or ""):
                continue
            result[match[1]] = result.get(match[1], 0) + float(match[3])
    return result


def drain():
    # A client timeout does not cancel the server transaction. Never reseed until it ends.
    deadline = time.monotonic() + 1800
    idle = 0
    while time.monotonic() < deadline:
        sample = metrics()
        if "hikaricp_connections_active" not in sample:
            raise RuntimeError("Missing pool metrics; cannot safely reset the fixture")
        if sample["hikaricp_connections_active"] == 0 and sample.get("hikaricp_connections_pending", 0) == 0:
            idle += 1
            if idle == 3:
                return
        else:
            idle = 0
        time.sleep(1)
    raise RuntimeError("Server did not drain; fixture was not reset")


def lifecycle(*args, log):
    with log.open("a") as output:
        return subprocess.run([sys.executable, str(ROOT / "manage.py"), *args],
                              stdout=output, stderr=subprocess.STDOUT).returncode


def jcmd(jdk, pid, *args):
    return subprocess.check_output([str(jdk / "bin/jcmd"), str(pid), *args], text=True)


def one_run(args, destination, version, scenario, vus, iteration, repeat):
    label = f"{args.label}-{version}-{scenario}-vu{vus}-r{repeat}"
    log = destination / f"{label}.log"
    drain()
    if lifecycle("seed", log=log):
        raise RuntimeError("Fixture reset failed")
    recording = None
    if args.mode == "profile":
        recording = destination / f"{label}.jfr"
        jcmd(args.jdk, args.pid, "JFR.start", "name=portfolio496",
             f"settings={destination / 'profile.jfc'}", f"filename={recording}")
    command = [sys.executable, str(ROOT / "manage.py"), "run", "--version", version,
               "--scenario", scenario, "--side", args.side, "--vus", str(vus),
               "--iterations", str(iteration), "--label", label]
    samples = []
    started = time.monotonic()
    with log.open("a") as output:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT)
        while process.poll() is None:
            try:
                samples.append({"elapsedSeconds": time.monotonic() - started, **metrics()})
            except Exception as error:
                samples.append({"elapsedSeconds": time.monotonic() - started,
                                "error": type(error).__name__})
            time.sleep(0.5)
    # Include work continuing after a timeout in drain, but not as successful latency samples.
    drain_started = time.monotonic()
    drain()
    post_client_drain = time.monotonic() - drain_started
    if recording:
        jcmd(args.jdk, args.pid, "JFR.stop", "name=portfolio496", f"filename={recording}")
        recording.chmod(0o600)
    result_dir = LOCAL / "results" / label
    (result_dir / "resources.json").write_text(json.dumps(samples, indent=2) + "\n")
    verified = process.returncode == 0 and lifecycle("verify-result", "--label", label, log=log) == 0
    summary_path = result_dir / "summary.json"
    summary = json.loads(summary_path.read_text()) if summary_path.exists() else {}
    sql_path = result_dir / "sql.json"
    statements = json.loads(sql_path.read_text()) if sql_path.exists() else []
    durations = summary.get("metrics", {}).get("transaction_duration", {}).get("values", {})
    successful = summary.get("result", {}).get("successfulRequests", 0)
    row = {
        "label": label, "version": version, "scenario": scenario, "side": args.side,
        "vus": vus, "iterations": iteration, "repeat": repeat,
        "exitCode": process.returncode, "verified": verified,
        "postClientDrainSeconds": post_client_drain,
        "validForComparison": verified and summary.get("result", {}).get("validForComparison", False),
        "successfulRequests": successful, "expectedRequests": vus * iteration,
        "durationMs": durations,
        "successfulRequestsPerSecond": summary.get("metrics", {}).get("transaction_success", {}).get("values", {}).get("rate"),
        "sqlCalls": sum(statement["calls"] for statement in statements),
        "sqlExecutionMs": sum(statement["total_exec_time"] for statement in statements),
        "poolActiveMax": max((s.get("hikaricp_connections_active", 0) for s in samples), default=0),
        "poolPendingMax": max((s.get("hikaricp_connections_pending", 0) for s in samples), default=0),
        "poolTimeoutsDelta": samples[-1].get("hikaricp_connections_timeout_total", 0) - samples[0].get("hikaricp_connections_timeout_total", 0) if samples else None,
        "resourceSamplingSeconds": 0.5,
        "resourceSampleErrors": sum("error" in sample for sample in samples),
    }
    print(json.dumps(row, ensure_ascii=False), flush=True)
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("matrix", "profile"))
    parser.add_argument("--label", required=True)
    parser.add_argument("--vus", nargs="+", type=int, choices=(1, 5, 10, 20, 50), default=[1, 5, 10, 20, 50])
    parser.add_argument("--scenarios", nargs="+", choices=("today", "one-month", "six-months"), default=["today", "one-month", "six-months"])
    parser.add_argument("--versions", nargs="+", choices=("v1", "v2"), default=["v1", "v2"])
    parser.add_argument("--iterations", type=int, default=3)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--side", choices=("BUY", "SELL"), default="BUY")
    parser.add_argument("--pid", type=int)
    parser.add_argument("--jdk", type=Path, default=Path(os.environ.get("JAVA_HOME", "/invalid")))
    args = parser.parse_args()
    if not re.fullmatch(r"[a-zA-Z0-9_-]+", args.label):
        parser.error("Use a plain filename label")
    if not 1 <= args.iterations <= 500 or args.repeats < 1:
        parser.error("Invalid iterations/repeats")
    if args.mode == "profile" and (not args.pid or not (args.jdk / "bin/jcmd").is_file()):
        parser.error("Profiling requires --pid and a JDK with jcmd/jfr")
    destination = LOCAL / "results" / args.label
    destination.mkdir(parents=True, exist_ok=False)
    manifest = json.loads((LOCAL / "fixture.json").read_text())
    (destination / "environment.json").write_text(json.dumps({
        "platform": platform.platform(), "logicalCpus": os.cpu_count(),
        "fixtureHash": manifest["fixtureHash"], "mode": args.mode,
        "plan": {key: str(value) if isinstance(value, Path) else value for key, value in vars(args).items()},
        "jfrEnabled": args.mode == "profile",
        "note": "k6, application, Docker DB share this host; fixed per-VU request budget, not fixed RPS",
    }, indent=2) + "\n")
    if args.mode == "profile":
        subprocess.run([str(args.jdk / "bin/jfr"), "configure", "--input", "profile.jfc",
                        "socket-threshold=0ms", "jdk.InitialEnvironmentVariable#enabled=false",
                        "jdk.InitialSystemProperty#enabled=false",
                        "--output", str(destination / "profile.jfc")], check=True)
        jcmd(args.jdk, args.pid, "JFR.configure", "stackdepth=128")
    rows = []
    try:
        # Warm both paths at each date range; restore data before every measured run.
        for scenario in args.scenarios:
            for version in args.versions:
                warmup = argparse.Namespace(**vars(args))
                warmup.mode = "matrix"
                warmup.label = f"{args.label}-warmup"
                one_run(warmup, destination, version, scenario, 1, 2, 0)
            for vus in args.vus:
                for repeat in range(1, args.repeats + 1):
                    # Alternate which version runs first to reduce order bias.
                    versions = args.versions if repeat % 2 else list(reversed(args.versions))
                    for version in versions:
                        rows.append(one_run(args, destination, version, scenario, vus, args.iterations, repeat))
                        (destination / "comparison.json").write_text(json.dumps(rows, indent=2) + "\n")
    finally:
        # Only a fully drained server may be reset, including after aborted k6 requests.
        drain()
        lifecycle("seed", log=destination / "cleanup.log")


if __name__ == "__main__":
    main()
