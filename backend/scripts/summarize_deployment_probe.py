"""Summarize k6 raw console samples without claiming exact downtime."""

import argparse
import json
from pathlib import Path


def summarize(samples):
    rows = sorted(samples, key=lambda row: row["started_ms"])
    windows = []
    previous_success = None
    current = None
    for row in rows:
        if row["ok"]:
            if current is not None:
                current["next_success_start_ms"] = row["started_ms"]
                windows.append(current)
                current = None
            previous_success = row["started_ms"]
        else:
            if current is None:
                current = {
                    "previous_success_start_ms": previous_success,
                    "first_failure_start_ms": row["started_ms"],
                    "failed_requests": 0,
                }
            current["last_failure_start_ms"] = row["started_ms"]
            current["failed_requests"] += 1
    if current is not None:
        current["next_success_start_ms"] = None
        windows.append(current)
    for window in windows:
        # Request start timestamps bracket observations, not exact server state.
        before = window["previous_success_start_ms"]
        after = window["next_success_start_ms"]
        window["surrounding_success_gap_ms"] = (
            after - before if before is not None and after is not None else None
        )
        window["failure_start_span_ms"] = (
            window["last_failure_start_ms"] - window["first_failure_start_ms"]
        )
    return {
        "samples": len(rows),
        "failures": sum(not row["ok"] for row in rows),
        "max_observed_elapsed_ms": max(
            (row["finished_ms"] - row["started_ms"] for row in rows), default=None
        ),
        "failure_windows_by_request_start": windows,
        "note": "Client observations include network/proxy effects; gaps are not exact downtime. Check k6 summary completeness separately.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("samples", type=Path)
    args = parser.parse_args()
    rows = []
    for line in args.samples.read_text().splitlines():
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            continue
        if isinstance(row, dict) and row.get("kind") == "deployment_probe":
            rows.append(row)
    if not rows:
        parser.error("No probe samples found; use k6 --log-format raw")
    print(json.dumps(summarize(rows), indent=2))


if __name__ == "__main__":
    main()
