"""Register 1,003 private accounts as split development Actions Secrets."""
import argparse
import base64
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import sys

PREFIX = "K6_ACCOUNTS_JSON_GZ_BASE64"
CHUNK_BYTES = 40000
MAX_PARTS = 16


def prepare(path):
    raw = Path(path).read_bytes()
    try:
        accounts = json.loads(raw)
        if not isinstance(accounts, list) or len(accounts) != 1003:
            raise ValueError()
        if sorted(a["testNumber"] for a in accounts) != list(range(1, 1004)):
            raise ValueError()
        if len({a["memberId"] for a in accounts}) != 1003:
            raise ValueError()
        if any(not a.get("accessToken") for a in accounts):
            raise ValueError()
    except (ValueError, KeyError, TypeError):
        raise ValueError("Expected the complete 1,003-account file from portfolio1000Loadtest tokens.") from None
    encoded = base64.b64encode(gzip.compress(raw, mtime=0))
    parts = [encoded[i:i + CHUNK_BYTES] for i in range(0, len(encoded), CHUNK_BYTES)]
    if len(parts) > MAX_PARTS:
        raise ValueError("Encoded account file exceeds 16 parts (640,000 bytes); nothing uploaded.")
    manifest = f"{len(parts)}:{hashlib.sha256(encoded).hexdigest()}".encode()
    return parts, manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("accounts", help="Path to the private accounts-1000.json")
    parser.add_argument("--repo", default="woowacourse-teams/2026-DAYNOMY")
    parser.add_argument("--environment", default="development")
    args = parser.parse_args()
    try:
        parts, manifest = prepare(args.accounts)
        subprocess.run(["gh", "auth", "status"], check=True, capture_output=True)
        print(f"Registering 1,003 accounts in {len(parts)} parts (each at most 40,000 bytes).")
        names = [PREFIX] + [f"{PREFIX}_PART_{n:02}" for n in range(2, len(parts) + 1)]
        # Publish the manifest last. Hash verification rejects interrupted/mixed uploads.
        for name, value in list(zip(names, parts)) + [(PREFIX + "_MANIFEST", manifest)]:
            subprocess.run(
                ["gh", "secret", "set", name, "--repo", args.repo, "--env", args.environment],
                input=value, check=True, capture_output=True,
            )
            print(f"Registered {name}")
        print("Done. No account values printed or split files created. Old unused parts are ignored.")
    except (ValueError, OSError, subprocess.CalledProcessError):
        print("Registration failed. Check the account file, gh authentication and environment write access; rerun the full command. Do not run the load test until registration succeeds.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
