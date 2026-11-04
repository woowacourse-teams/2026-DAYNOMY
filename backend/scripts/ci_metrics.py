"""Collect Linux CI resource usage without reading command lines or environments."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from datetime import datetime, timezone


INTERVAL_SECONDS = 5
PROC = Path("/proc")
CGROUP = Path("/sys/fs/cgroup")


def timestamp():
    return datetime.now(timezone.utc).isoformat()


def read_optional(path):
    try:
        return path.read_text().strip()
    except OSError:
        return None


def snapshot():
    # guest/guest_nice are already included in user/nice, so do not count them twice.
    cpu = list(map(int, (PROC / "stat").read_text().splitlines()[0].split()[1:9]))
    memory = {}
    for line in (PROC / "meminfo").read_text().splitlines():
        key, value = line.split(":", 1)
        if key in ("MemTotal", "MemAvailable", "SwapTotal", "SwapFree"):
            memory[key] = int(value.split()[0]) * 1024

    processes = {}
    for directory in PROC.glob("[0-9]*"):
        try:
            name = (directory / "comm").read_text().strip()
            if name not in ("java", "postgres"):
                continue
            fields = (directory / "stat").read_text().rsplit(")", 1)[1].split()
            processes[directory.name] = {
                "name": name,
                "start_ticks": int(fields[19]),
                "cpu_ticks": int(fields[11]) + int(fields[12]),
                "rss_bytes": int(fields[21]) * os.sysconf("SC_PAGE_SIZE"),
            }
        except (OSError, ValueError, IndexError):
            # A process may exit while /proc is being read.
            continue
    return {
        "cpu": cpu,
        "memory_bytes": memory,
        "processes": processes,
        "cgroup_memory_current": read_optional(CGROUP / "memory.current"),
        "cgroup_cpu_stat": read_optional(CGROUP / "cpu.stat"),
    }


def sample(current, previous, elapsed):
    cpu_percent = None
    iowait_percent = None
    if previous:
        total = sum(current["cpu"]) - sum(previous["cpu"])
        idle = sum(current["cpu"][3:5]) - sum(previous["cpu"][3:5])
        if total > 0:
            cpu_percent = round(100 * (total - idle) / total, 2)
            iowait_percent = round(100 * (current["cpu"][4] - previous["cpu"][4]) / total, 2)

    processes = []
    for pid, process in current["processes"].items():
        old = previous["processes"].get(pid) if previous else None
        usage = None
        if old and old["start_ticks"] == process["start_ticks"] and elapsed > 0:
            usage = round(
                100 * (process["cpu_ticks"] - old["cpu_ticks"])
                / os.sysconf("SC_CLK_TCK") / elapsed,
                2,
            )
        processes.append({"pid": int(pid), **process, "cpu_percent_one_core": usage})
    return {
        "timestamp_utc": timestamp(),
        "cpu_busy_percent_all_cores": cpu_percent,
        "cpu_iowait_percent_all_cores": iowait_percent,
        "memory_bytes": current["memory_bytes"],
        "processes": processes,
        "cgroup_memory_current": current["cgroup_memory_current"],
        "cgroup_cpu_stat": current["cgroup_cpu_stat"],
    }


def collect(output):
    metadata = {
        "started_at_utc": timestamp(),
        "cpu_count": os.cpu_count(),
        "clock_ticks_per_second": os.sysconf("SC_CLK_TCK"),
        "interval_seconds": INTERVAL_SECONDS,
        "cgroup_cpu_max": read_optional(CGROUP / "cpu.max"),
        "cgroup_memory_max": read_optional(CGROUP / "memory.max"),
        "cgroup_memory_current": read_optional(CGROUP / "memory.current"),
    }
    (output / "environment.json").write_text(json.dumps(metadata, indent=2) + "\n")
    started = time.monotonic()
    previous_time = started
    previous = None
    count = 0
    sampling_seconds = 0
    with (output / "resources.jsonl").open("w", buffering=1) as stream:
        while not (output / "stop").exists():
            before = time.monotonic()
            current = snapshot()
            stream.write(json.dumps(sample(current, previous, before - previous_time)) + "\n")
            previous, previous_time = current, before
            count += 1
            sampling_seconds += time.monotonic() - before
            # Check for shutdown frequently without increasing the sampling rate.
            while time.monotonic() - before < INTERVAL_SECONDS:
                if (output / "stop").exists():
                    break
                time.sleep(0.1)
    (output / "summary.json").write_text(json.dumps({
        "finished_at_utc": timestamp(),
        "samples": count,
        "elapsed_seconds": round(time.monotonic() - started, 3),
        "sampling_wall_seconds": round(sampling_seconds, 3),
        "collector_cpu_seconds": round(time.process_time(), 3),
    }, indent=2) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("start", "stop", "collect"))
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    if args.command == "start":
        if not PROC.joinpath("stat").exists():
            parser.error("Resource collection requires Linux /proc.")
        output.mkdir(parents=True, exist_ok=True)
        (output / "gc").mkdir(exist_ok=True)
        with (output / "collector.log").open("w") as log:
            subprocess.Popen(
                [sys.executable, str(Path(__file__).resolve()), "collect", str(output)],
                stdout=log, stderr=log, start_new_session=True,
            )
    elif args.command == "stop":
        if not output.exists():
            print("Metrics directory is absent; collection was not started.")
            return
        (output / "stop").touch()
        deadline = time.monotonic() + 10
        while not (output / "summary.json").exists():
            if time.monotonic() >= deadline:
                raise SystemExit("Collector did not finish; inspect collector.log.")
            time.sleep(0.1)
        print((output / "summary.json").read_text())
    else:
        collect(output)


if __name__ == "__main__":
    main()
