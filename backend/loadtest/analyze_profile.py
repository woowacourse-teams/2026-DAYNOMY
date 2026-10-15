#!/usr/bin/env python3
"""Summarize JFR stack samples, not exact per-method elapsed times."""

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import subprocess
import tempfile


def seconds(duration):
    match = re.fullmatch(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?", duration)
    if not match:
        raise ValueError(f"Unexpected JFR duration: {duration}")
    hours, minutes, value = (float(part or 0) for part in match.groups())
    return hours * 3600 + minutes * 60 + value


def summarize(events):
    categories, inclusive, leaves = Counter(), Counter(), Counter()
    by_event = {}
    sockets = Counter()
    gc_seconds = 0
    for event in events:
        value = event["values"]
        if event["type"] == "jdk.GCPhasePause":
            gc_seconds += seconds(value["duration"])
            continue
        thread = value.get("sampledThread") or value.get("eventThread") or {}
        if "8496-exec-" not in thread.get("javaName", ""):
            continue
        frames = (value.get("stackTrace") or {}).get("frames", [])
        stack = [frame["method"]["type"]["name"].replace("/", ".") + "." +
                 frame["method"]["name"] for frame in frames]
        if event["type"] == "jdk.SocketRead":
            if value.get("port") == 55496:
                sockets["reads"] += 1
                sockets["seconds"] += seconds(value["duration"])
            continue
        if event["type"] not in ("jdk.ExecutionSample", "jdk.NativeMethodSample"):
            continue
        joined = "\n".join(stack)
        if "autoFlushIfRequired" in joined or "DefaultAutoFlushEventListener" in joined:
            category = "hibernate_auto_flush"
        elif "AbstractFlushingEventListener" in joined or "DefaultFlushEntityEventListener" in joined or "DefaultFlushEventListener" in joined:
            category = "hibernate_other_flush"
        elif "PortfolioLedgerCalculator.replay" in joined:
            category = "ledger_replay"
        elif "org.postgresql." in joined or "sun.nio.ch." in joined:
            category = "jdbc_transport"
        elif "org.hibernate." in joined:
            category = "hibernate_other"
        else:
            category = "other_request_work"
        categories[category] += 1
        by_event.setdefault(event["type"], Counter())[category] += 1
        if stack:
            leaves[stack[0]] += 1
        for method in set(stack):
            inclusive[method] += 1
    total = sum(categories.values())
    return {
        "requestThreadSamples": total,
        "exclusiveSampleCategories": [
            {"category": name, "samples": count, "percent": count * 100 / total}
            for name, count in categories.most_common()
        ],
        "sampleCategoriesByEventType": {
            kind: [{"category": name, "samples": count,
                    "percent": count * 100 / sum(counts.values())}
                   for name, count in counts.most_common()]
            for kind, counts in by_event.items()
        },
        "topInclusiveMethods": inclusive.most_common(35),
        "topLeafMethods": leaves.most_common(20),
        "postgresSocketReads": sockets["reads"],
        "postgresSocketReadSeconds": sockets["seconds"],
        "gcPauseSecondsAllThreads": gc_seconds,
        "caveat": "Stack sample shares are not exact CPU/wall-time percentages. Execution/native events have different periods (profile default: 10/20 ms); use per-event categories separately. Includes preflight/CSRF; recordings are excluded from benchmark latency results. Socket times include waiting, not just DB execution. GC is JVM-wide.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("recordings", nargs="+", type=Path)
    parser.add_argument("--jdk", type=Path, required=True)
    args = parser.parse_args()
    for recording in args.recordings:
        # Socket events can outnumber samples by orders of magnitude. Their stacks are
        # unnecessary for duration totals; avoid expanding thousands of 64-frame stacks.
        events = []
        for depth, kinds in (("64", "jdk.ExecutionSample,jdk.NativeMethodSample"),
                             ("1", "jdk.SocketRead,jdk.GCPhasePause")):
            with tempfile.TemporaryFile(mode="w+") as raw:
                subprocess.run([str(args.jdk / "bin/jfr"), "print", "--json", "--stack-depth", depth,
                                "--events", kinds, str(recording)], stdout=raw, check=True)
                raw.seek(0)
                events.extend(json.load(raw)["recording"]["events"])
        summary = summarize(events)
        output = recording.with_suffix(".analysis.json")
        output.write_text(json.dumps(summary, indent=2) + "\n")
        print(recording.name, json.dumps({key: summary[key] for key in (
            "requestThreadSamples", "sampleCategoriesByEventType", "postgresSocketReadSeconds",
            "gcPauseSecondsAllThreads")}), flush=True)


if __name__ == "__main__":
    main()
