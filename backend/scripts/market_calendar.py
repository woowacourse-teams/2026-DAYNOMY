#!/usr/bin/env python3
"""Validate an official KRX calendar CSV and print reviewable PostgreSQL SQL."""

import argparse
import csv
from datetime import date, timedelta
from pathlib import Path
from urllib.parse import urlsplit


def read_days(stream, year=None):
    reader = csv.DictReader(stream)
    if reader.fieldnames != ["base_date", "trading"]:
        raise ValueError("CSV 헤더는 base_date,trading이어야 합니다.")
    days = {}
    for row in reader:
        if None in row or any(value is None for value in row.values()):
            raise ValueError("CSV 행의 열 개수가 올바르지 않습니다.")
        text = row["base_date"].strip()
        day = date.fromisoformat(text)
        if day.isoformat() != text or day in days:
            raise ValueError("날짜는 중복 없는 YYYY-MM-DD 형식이어야 합니다.")
        trading = row["trading"].strip().lower()
        if trading not in ("true", "false"):
            raise ValueError("trading은 true 또는 false여야 합니다.")
        days[day] = trading == "true"
    if not days:
        raise ValueError("적용할 거래일 달력이 없습니다.")
    if year is not None:
        first, last = date(year, 1, 1), date(year, 12, 31)
        expected = {first + timedelta(days=i) for i in range((last - first).days + 1)}
        if set(days) != expected:
            raise ValueError(f"연도 등록에는 {year}년 전체 {len(expected)}일이 필요합니다.")
    return sorted(days.items())


def render_sql(days, source):
    url = urlsplit(source)
    if (url.scheme != "https" or not url.hostname
            or not (url.hostname == "krx.co.kr" or url.hostname.endswith(".krx.co.kr"))
            or url.username or url.password or any(char.isspace() for char in source)):
        raise ValueError("출처는 인증정보 없는 공식 KRX HTTPS URL이어야 합니다.")
    values = ",\n".join(f"  (DATE '{day.isoformat()}', {'TRUE' if trading else 'FALSE'})" for day, trading in days)
    return f"""-- 공식 거래일 확인 출처: {source}
-- 적용 전 날짜·거래 여부를 검토한다. 가격·성과·확정 이력은 삭제하지 않는다.
BEGIN;
INSERT INTO market_trading_days (base_date, trading) VALUES
{values}
ON CONFLICT (base_date) DO UPDATE SET
  trading = EXCLUDED.trading,
  collected_at = CASE
    WHEN market_trading_days.trading = EXCLUDED.trading THEN market_trading_days.collected_at
    ELSE NULL
  END;
COMMIT;
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--year", type=int, help="연도 등록: 주말·휴장일을 포함한 전체 날짜 검증")
    parser.add_argument("--source", required=True, help="직접 확인한 공식 KRX 자료 URL")
    parser.add_argument("--output", type=Path, help="없으면 SQL을 표준 출력으로 출력")
    args = parser.parse_args()
    try:
        with args.csv.open(encoding="utf-8-sig", newline="") as stream:
            sql = render_sql(read_days(stream, args.year), args.source)
        if args.output:
            args.output.write_text(sql, encoding="utf-8")
        else:
            print(sql, end="")
    except (ValueError, OSError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
