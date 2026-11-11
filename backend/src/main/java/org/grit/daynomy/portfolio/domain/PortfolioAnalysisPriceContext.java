package org.grit.daynomy.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.TreeMap;

/** 개인 매수가·수익률과 독립적인 종목 통화 기준 가격 관측값. */
public record PortfolioAnalysisPriceContext(
    Status status,
    String currency,
    String priceType,
    LocalDate baseDate,
    Instant asOf,
    BigDecimal price,
    LocalDate comparisonDate,
    BigDecimal comparisonPrice,
    BigDecimal changeRate,
    int observationCount) {
  public enum Status {
    AVAILABLE,
    INSUFFICIENT_HISTORY,
    STALE_PRICE,
    UNAVAILABLE,
    AMBIGUOUS_ASSET,
    LOOKUP_FAILED
  }

  public record Observation(LocalDate date, Instant asOf, BigDecimal price, String type) {}

  public static PortfolioAnalysisPriceContext unavailable(Status status) {
    return new PortfolioAnalysisPriceContext(
        status, null, null, null, null, null, null, null, null, 0);
  }

  public static PortfolioAnalysisPriceContext calculate(
      String currency, List<Observation> observations, LocalDate from, LocalDate to, Instant now) {
    var daily = new TreeMap<LocalDate, Observation>();
    observations.stream()
        .filter(
            point ->
                point.date() != null
                    && !point.date().isAfter(to)
                    && point.price() != null
                    && point.price().signum() > 0
                    && (point.asOf() == null || !point.asOf().isAfter(now)))
        .forEach(point -> daily.put(point.date(), point));
    if (daily.isEmpty()) return unavailable(Status.UNAVAILABLE);
    var latest = daily.lastEntry().getValue();
    var period = daily.tailMap(from, true);
    int count = period.size();
    var first = period.isEmpty() ? null : period.firstEntry().getValue();
    boolean comparable = first != null && first.date().isBefore(latest.date());
    BigDecimal rate =
        comparable
            ? latest
                .price()
                .subtract(first.price())
                .multiply(BigDecimal.valueOf(100))
                .divide(first.price(), 2, RoundingMode.HALF_UP)
            : null;
    return new PortfolioAnalysisPriceContext(
        latest.date().isBefore(to.minusDays(7))
            ? Status.STALE_PRICE
            : comparable ? Status.AVAILABLE : Status.INSUFFICIENT_HISTORY,
        currency,
        latest.type(),
        latest.date(),
        latest.asOf(),
        latest.price(),
        comparable ? first.date() : null,
        comparable ? first.price() : null,
        rate,
        count);
  }
}
