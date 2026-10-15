import base64
import hashlib
import hmac
import io
import json
import unittest
from datetime import date
from decimal import Decimal

from fixtures import access_token, build_fixture, manifest, months_before, write_seed


class FixtureTest(unittest.TestCase):
    def test_month_subtraction_clamps_leap_day_and_year_boundary(self):
        self.assertEqual(date(2024, 2, 29), months_before(date(2024, 3, 31), 1))
        self.assertEqual(date(2025, 12, 31), months_before(date(2026, 1, 31), 1))

    def test_history_replay_matches_current_holdings_and_snapshots(self):
        fixture = build_fixture(date(2026, 10, 15), members=2, holdings=3, transactions=100)
        quantities = {}
        snapshots = {point["baseDate"]: point for point in fixture["snapshots"]}
        for asset, day, kind, before, price, trade in fixture["histories"]:
            if kind == "OPENING_BALANCE":
                quantities[asset] = before
            else:
                self.assertEqual(quantities[asset], before)
                quantities[asset] += trade
            self.assertEqual(10000 + asset * 100, price)
        self.assertEqual(fixture["quantities"], quantities)
        for day_index, day in enumerate(fixture["priceDates"]):
            if day < fixture["start"]:
                continue
            positions = {}
            for asset, effective, kind, before, price, trade in fixture["histories"]:
                if effective.isoformat() > day:
                    break
                positions[asset] = before if kind == "OPENING_BALANCE" else before + trade
            purchase = sum(quantity * (10000 + asset * 100) for asset, quantity in positions.items())
            evaluation = purchase + day_index * sum(positions.values())
            self.assertEqual(Decimal(purchase), Decimal(snapshots[day]["totalPurchaseAmount"]))
            self.assertEqual(Decimal(evaluation), Decimal(snapshots[day]["totalEvaluationAmount"]))
            self.assertEqual(Decimal(sum(positions.values())), Decimal(snapshots[day]["dailyProfitLoss"]))

    def test_prices_include_prior_weekday_and_cases_follow_balances(self):
        fixture = build_fixture(date(2026, 10, 18), holdings=1, transactions=1)
        self.assertLess(fixture["priceDates"][0], fixture["start"])
        self.assertTrue(all(date.fromisoformat(day).weekday() < 5 for day in fixture["priceDates"]))
        self.assertTrue(all(day >= fixture["start"] for day in fixture["cases"].values()))
        self.assertLess(fixture["snapshots"][-1]["baseDate"], fixture["asOf"])

    def test_seed_is_deterministic_guarded_and_has_no_credentials(self):
        fixture = build_fixture(date(2026, 10, 15), members=2, holdings=2, transactions=5)
        first, second = io.StringIO(), io.StringIO()
        write_seed(fixture, first)
        write_seed(fixture, second)
        self.assertEqual(first.getvalue(), second.getvalue())
        self.assertLess(first.getvalue().index("current_database()"), first.getvalue().index("TRUNCATE"))
        self.assertIn("current_user <> 'daynomy_loadtest'", first.getvalue())
        self.assertIn("@example.invalid", first.getvalue())
        self.assertNotIn("accessToken", first.getvalue())

    def test_token_matches_spring_hs256_access_claims(self):
        secret = base64.b64encode(bytes(range(32))).decode()
        token = access_token(7, secret, 1000)
        header, payload, signature = token.split(".")
        claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
        self.assertEqual({"iss": "daynomy-loadtest", "sub": "7", "iat": 1000, "exp": 87400,
                          "jti": "loadtest-7-1000", "role": "USER", "token_type": "access"}, claims)
        expected = hmac.new(bytes(range(32)), f"{header}.{payload}".encode(), hashlib.sha256).digest()
        self.assertEqual(expected, base64.urlsafe_b64decode(signature + "=" * (-len(signature) % 4)))

    def test_manifest_fingerprint_excludes_rotating_credentials(self):
        fixture = build_fixture(date(2026, 10, 15), members=2, holdings=2, transactions=5)
        secret = base64.b64encode(bytes(range(32))).decode()
        first, second = manifest(fixture, secret, 1000), manifest(fixture, secret, 2000)
        self.assertEqual(first["fixtureHash"], second["fixtureHash"])
        self.assertNotEqual(first["members"][0]["accessToken"], second["members"][0]["accessToken"])
        self.assertEqual(7, first["historyRowsPerMember"])
        self.assertEqual(5, first["transactionsPerMember"])

    def test_invalid_fixture_sizes_are_rejected(self):
        for options in ({"members": 0}, {"holdings": 51}, {"transactions": 0}):
            with self.assertRaises(ValueError):
                build_fixture(date(2026, 10, 15), **options)


if __name__ == "__main__":
    unittest.main()
