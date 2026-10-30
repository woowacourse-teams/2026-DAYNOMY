import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


register = load("register_accounts", ROOT / "register-secrets.py")
decoder = load("decode_accounts", ROOT / "support/decode-accounts.py")


class SecretRegistrationTest(unittest.TestCase):
    def test_split_roundtrip_and_mixed_or_missing_parts(self):
        rows = [{"testNumber": n, "memberId": n, "accessToken": str(uuid4())}
                for n in range(1, 1004)]
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "accounts.json"
            source.write_text(json.dumps(rows))
            with patch.object(register, "CHUNK_BYTES", 5000):
                parts, manifest = register.prepare(source)
            self.assertGreater(len(parts), 1)
            self.assertTrue(all(len(part) <= 40000 for part in parts))
            names = [register.PREFIX] + [f"{register.PREFIX}_PART_{n:02}"
                                         for n in range(2, len(parts) + 1)]
            env = dict(zip(names, [part.decode() for part in parts]))
            env[register.PREFIX + "_MANIFEST"] = manifest.decode()
            self.assertEqual(json.loads(decoder.decode(env)), rows)
            missing = dict(env)
            missing.pop(names[-1])
            with self.assertRaises(ValueError):
                decoder.decode(missing)
            mixed = dict(env)
            mixed[names[-1]] = "old-data"
            with self.assertRaises(ValueError):
                decoder.decode(mixed)
            legacy = {register.PREFIX: b"".join(parts).decode()}
            self.assertEqual(json.loads(decoder.decode(legacy)), rows)

    def test_wrong_cohort_is_rejected_before_upload(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "accounts.json"
            source.write_text("[]")
            with self.assertRaises(ValueError):
                register.prepare(source)

    def test_upload_uses_stdin_and_publishes_manifest_last(self):
        calls = []
        def run(args, **kwargs):
            calls.append((args, kwargs))
        with patch.object(register, "prepare", return_value=([b"part-one", b"part-two"], b"manifest")), \
             patch.object(register.subprocess, "run", side_effect=run), \
             patch("sys.argv", ["register-secrets.py", "accounts.json"]):
            self.assertEqual(register.main(), 0)
        uploads = calls[1:]
        self.assertEqual([call[1]["input"] for call in uploads],
                         [b"part-one", b"part-two", b"manifest"])
        self.assertEqual(uploads[-1][0][3], register.PREFIX + "_MANIFEST")
        self.assertNotIn("part-one", uploads[0][0])
