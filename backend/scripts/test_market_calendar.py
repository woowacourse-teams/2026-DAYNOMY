import io
import unittest
from datetime import date, timedelta

from market_calendar import read_days, render_sql


class MarketCalendarTest(unittest.TestCase):
    def test_partial_and_complete_leap_year_preserve_collection_on_unchanged_days(self):
        days = read_days(io.StringIO("base_date,trading\n2026-10-09,false\n2026-10-08,true\n"))
        self.assertEqual(days, [(date(2026, 10, 8), True), (date(2026, 10, 9), False)])
        sql = render_sql(days, "https://open.krx.co.kr/contents/calendar.jsp")
        self.assertIn("ON CONFLICT (base_date) DO UPDATE", sql)
        self.assertIn("THEN market_trading_days.collected_at", sql)
        self.assertIn("ELSE NULL", sql)
        first = date(2028, 1, 1)
        rows = "base_date,trading\n" + "".join(f"{first + timedelta(days=i)},false\n" for i in range(366))
        self.assertEqual(len(read_days(io.StringIO(rows), 2028)), 366)

    def test_incomplete_year_duplicate_and_invalid_input_cannot_generate_sql(self):
        for body, year in [
            ("base_date,trading\n2027-01-01,false\n", 2027),
            ("base_date,trading\n2026-10-09,false\n2026-10-09,true\n", None),
            ("base_date,trading\n20261009,true\n", None),
            ("base_date,trading\n2026-10-09,unknown\n", None),
            ("base_date,trading\n2026-10-09,true,extra\n", None),
        ]:
            with self.subTest(body=body), self.assertRaises(ValueError):
                read_days(io.StringIO(body), year)
        for source in ("https://example.com", "https://open.krx.co.kr/x\nCOMMIT;", "https://secret@open.krx.co.kr"):
            with self.subTest(source=source), self.assertRaises(ValueError):
                render_sql([(date(2026, 10, 9), False)], source)


if __name__ == "__main__":
    unittest.main()
