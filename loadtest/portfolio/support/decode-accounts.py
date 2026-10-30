"""Reconstruct account Secrets without printing any account contents."""
import base64
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import sys
import zlib


class AccountSecretError(ValueError):
    """A diagnostic that contains no Secret values."""


def decode(environment):
    prefix = "K6_ACCOUNTS_JSON_GZ_BASE64"
    manifest = environment.get(prefix + "_MANIFEST", "").strip()
    if manifest:
        match = re.fullmatch(r"([1-9]|1[0-6]):([0-9a-f]{64})", manifest)
        if not match:
            raise AccountSecretError("Invalid K6_ACCOUNTS_JSON_GZ_BASE64_MANIFEST format: expected part-count:SHA-256.")
        count, digest = int(match[1]), match[2]
        names = [prefix] + [f"{prefix}_PART_{n:02}" for n in range(2, count + 1)]
        parts = [re.sub(r"\s+", "", environment.get(name, "")) for name in names]
        missing = [name for name, value in zip(names, parts) if not value]
        if missing:
            raise AccountSecretError("Missing or empty account Secrets: " + ", ".join(missing))
        encoded = "".join(parts)
        if not re.fullmatch(r"[A-Za-z0-9+/=]+", encoded):
            raise AccountSecretError("Account Secret parts contain characters outside Base64; copy only file contents.")
        if hashlib.sha256(encoded.encode("ascii")).hexdigest() != digest:
            raise AccountSecretError("Account Secret SHA-256 mismatch: parts and MANIFEST are from different data or a part was copied incorrectly.")
    else:
        # Preserve existing analysis accounts stored in one Secret.
        if any(environment.get(f"{prefix}_PART_{n:02}", "").strip() for n in range(2, 17)):
            raise AccountSecretError("Split account Secrets exist but K6_ACCOUNTS_JSON_GZ_BASE64_MANIFEST is missing or empty.")
        encoded = re.sub(r"\s+", "", environment.get(prefix, ""))
    try:
        content = gzip.decompress(base64.b64decode(encoded, validate=True))
        accounts = json.loads(content)
        if not isinstance(accounts, list) or not accounts:
            raise ValueError()
        return content
    except (ValueError, OSError, EOFError, zlib.error):
        raise AccountSecretError("Account Secrets are not a valid gzip/Base64 JSON account array.") from None


def main():
    try:
        content = decode(os.environ)
        target = Path(sys.argv[1])
        # The workflow supplies its own mktemp path, outside the checkout.
        with target.open("wb") as output:
            os.chmod(target, 0o600)
            output.write(content)
    except AccountSecretError as error:
        print(f"Unable to reconstruct account Secrets: {error}", file=sys.stderr)
        return 1
    except (OSError, IndexError):
        print("Unable to reconstruct account Secrets. Check all parts and the manifest.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
