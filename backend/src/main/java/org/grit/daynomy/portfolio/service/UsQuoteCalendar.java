package org.grit.daynomy.portfolio.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class UsQuoteCalendar {

  // NYSE equity calendar: https://www.nyse.com/trade/hours-calendars
  private static final Map<Integer, Set<String>> CLOSED =
      Map.of(
          2026,
              Set.of(
                  "--01-01", "--01-19", "--02-16", "--04-03", "--05-25", "--06-19", "--07-03",
                  "--09-07", "--11-26", "--12-25"),
          2027,
              Set.of(
                  "--01-01", "--01-18", "--02-15", "--03-26", "--05-31", "--06-18", "--07-05",
                  "--09-06", "--11-25", "--12-24"),
          2028,
              Set.of(
                  "--01-17", "--02-21", "--04-14", "--05-29", "--06-19", "--07-04", "--09-04",
                  "--11-23", "--12-25"));
  private static final Set<LocalDate> EARLY_CLOSE =
      Set.of(
          LocalDate.of(2026, 11, 27),
          LocalDate.of(2026, 12, 24),
          LocalDate.of(2027, 11, 26),
          LocalDate.of(2028, 7, 3),
          LocalDate.of(2028, 11, 24));

  private UsQuoteCalendar() {}

  static Optional<LocalTime> closeOn(LocalDate date) {
    Set<String> holidays = CLOSED.get(date.getYear());
    if (holidays == null
        || date.getDayOfWeek().getValue() > 5
        || holidays.contains(MonthDay.from(date).toString())) {
      return Optional.empty();
    }
    return Optional.of(EARLY_CLOSE.contains(date) ? LocalTime.of(13, 0) : LocalTime.of(16, 0));
  }
}
