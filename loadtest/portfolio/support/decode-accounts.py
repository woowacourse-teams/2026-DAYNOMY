"""Reconstruct account Secrets without printing any account contents."""
import base64
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import sys


def decode(environment):
    prefix = "K6_ACCOUNTS_JSON_GZ_BASE64"
    manifest = environment.get(prefix + "_MANIFEST", "")
    if manifest:
        match = re.fullmatch(r"([1-9]|1[0-6]):([0-9a-f]{64})", manifest)
        if not match:
            raise ValueError("Invalid account Secret manifest; register all parts again.")
        count, digest = int(match[1]), match[2]
        names = [prefix] + [f"{prefix}_PART_{n:02}" for n in range(2, count + 1)]
        parts = [environment.get(name, "") for name in names]
        if not all(parts):
            raise ValueError("An account Secret part is missing; register all parts again.")
        encoded = "".join(parts)
        if hashlib.sha256(encoded.encode("ascii")).hexdigest() != digest:
            raise ValueError("Account Secret parts do not match; register all parts again.")
    else:
        # Preserve existing analysis accounts stored in one Secret.
        encoded = environment.get(prefix, "")
    try:
        content = gzip.decompress(base64.b64decode(encoded, validate=True))
        accounts = json.loads(content)
        if not isinstance(accounts, list) or not accounts:
            raise ValueError()
        return content
    except (ValueError, OSError, EOFError):
        raise ValueError("Account Secrets must contain a gzip/Base64 JSON account array.") from None


def main():
    try:
        content = decode(os.environ)
        target = Path(sys.argv[1])
        # The workflow supplies its own mktemp path, outside the checkout.
        with target.open("wb") as output:
            os.chmod(target, 0o600)
            output.write(content)
    except (ValueError, OSError, IndexError):
        print("Unable to reconstruct account Secrets. Check all parts and the manifest.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
