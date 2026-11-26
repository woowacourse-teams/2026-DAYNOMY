"""Read Linux host and local Actuator memory; never change JVM settings."""

import argparse
import json
import re
import time
import urllib.request
from pathlib import Path


def metric_sum(text, name, label=None):
    values = []
    pattern = re.compile(r"^" + re.escape(name) + r"(?:\{([^}]*)\})?\s+(\S+)")
    for line in text.splitlines():
        match = pattern.match(line)
        if match and (label is None or label in (match[1] or "")):
            values.append(float(match[2]))
    return sum(values) if values else None


def mib(value):
    return round(value / 1048576, 2) if value is not None else None


def sample(metrics, meminfo, status):
    host = dict(re.findall(r"^(\w+):\s+(\d+) kB", meminfo, re.M))
    process = dict(re.findall(r"^(\w+):\s+(\d+) kB", status, re.M))
    return {
        "timestamp_ms": time.time_ns() // 1000000,
        "host_available_mib": round(int(host["MemAvailable"]) / 1024, 2),
        "host_swap_used_mib": round(
            (int(host["SwapTotal"]) - int(host["SwapFree"])) / 1024, 2
        ),
        "process_rss_mib": round(int(process["VmRSS"]) / 1024, 2),
        "heap_used_mib": mib(metric_sum(metrics, "jvm_memory_used_bytes", 'area="heap"')),
        "heap_committed_mib": mib(
            metric_sum(metrics, "jvm_memory_committed_bytes", 'area="heap"')
        ),
        "heap_max_mib": mib(metric_sum(metrics, "jvm_memory_max_bytes", 'area="heap"')),
        "nonheap_used_mib": mib(
            metric_sum(metrics, "jvm_memory_used_bytes", 'area="nonheap"')
        ),
        "gc_pause_count": metric_sum(metrics, "jvm_gc_pause_seconds_count"),
        "gc_pause_seconds_total": metric_sum(metrics, "jvm_gc_pause_seconds_sum"),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", required=True, type=int)
    parser.add_argument("--samples", type=int, default=12)
    parser.add_argument("--interval", type=float, default=5)
    parser.add_argument("--management-port", type=int, default=8081)
    args = parser.parse_args()
    if not (args.pid > 0 and 1 <= args.samples <= 120 and 0.1 <= args.interval <= 30
            and 1024 <= args.management_port <= 65535):
        parser.error("Invalid PID, samples, interval or management port")
    endpoint = f"http://127.0.0.1:{args.management_port}/actuator/prometheus"
    # Do not use HTTP_PROXY for a private, loopback-only management endpoint.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    for index in range(args.samples):
        with opener.open(endpoint, timeout=5) as response:
            metrics = response.read().decode()
        result = sample(metrics, Path('/proc/meminfo').read_text(),
                        Path(f'/proc/{args.pid}/status').read_text())
        print(json.dumps(result, allow_nan=False), flush=True)
        if index + 1 < args.samples:
            time.sleep(args.interval)


if __name__ == "__main__":
    main()
