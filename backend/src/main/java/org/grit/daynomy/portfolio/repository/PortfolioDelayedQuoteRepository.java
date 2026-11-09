package org.grit.daynomy.portfolio.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PortfolioDelayedQuoteRepository {

  private final NamedParameterJdbcTemplate jdbc;

  @Transactional
  public List<CachedQuote> request(List<String> symbols, Instant now) {
    if (symbols.isEmpty()) return List.of();
    var requestedAt = utc(now);
    var parameters =
        symbols.stream()
            .sorted()
            .map(
                symbol ->
                    new MapSqlParameterSource()
                        .addValue("symbol", symbol)
                        .addValue("market", symbol.endsWith(".US") ? "US" : "KR")
                        .addValue("requestedAt", requestedAt))
            .toArray(MapSqlParameterSource[]::new);
    jdbc.batchUpdate(
        """
        INSERT INTO portfolio_delayed_quotes (symbol, market, requested_at, queued_at)
        VALUES (:symbol, :market, :requestedAt, :requestedAt)
        ON CONFLICT (symbol) DO UPDATE
        SET requested_at = EXCLUDED.requested_at,
            queued_at = CASE
                WHEN portfolio_delayed_quotes.requested_at < EXCLUDED.requested_at - interval '10 minutes'
                THEN EXCLUDED.queued_at ELSE portfolio_delayed_quotes.queued_at END
        WHERE portfolio_delayed_quotes.requested_at < EXCLUDED.requested_at
        """,
        parameters);
    return jdbc.query(
        """
        SELECT symbol, price, price_as_of FROM portfolio_delayed_quotes
        WHERE symbol IN (:symbols) AND price IS NOT NULL AND price_as_of IS NOT NULL
        """,
        Map.of("symbols", symbols),
        (row, index) ->
            new CachedQuote(
                row.getString("symbol"),
                row.getBigDecimal("price"),
                row.getObject("price_as_of", OffsetDateTime.class).toInstant()));
  }

  @Transactional
  public List<String> reserve(
      String market, Instant slot, Instant now, int maximum, int dailyLimit, int protectedCalls) {
    LocalDate day = utc(now).toLocalDate();
    var date = Map.of("day", day);
    jdbc.update(
        """
        INSERT INTO portfolio_delayed_quote_budgets (budget_date) VALUES (:day)
        ON CONFLICT DO NOTHING
        """,
        date);
    Budget budget =
        jdbc.queryForObject(
            """
        SELECT reserved_calls, kr_slot, us_slot, exhausted
        FROM portfolio_delayed_quote_budgets WHERE budget_date = :day FOR UPDATE
        """,
            date,
            (row, index) ->
                new Budget(
                    row.getInt("reserved_calls"),
                    row.getObject("kr_slot", OffsetDateTime.class),
                    row.getObject("us_slot", OffsetDateTime.class),
                    row.getBoolean("exhausted")));
    boolean us = "US".equals(market);
    OffsetDateTime lastSlot = us ? budget.usSlot() : budget.krSlot();
    int allowed = Math.min(maximum, dailyLimit - budget.reservedCalls() - protectedCalls);
    if (allowed <= 0
        || budget.exhausted()
        || (lastSlot != null && !lastSlot.toInstant().isBefore(slot))) {
      return List.of();
    }
    var symbols =
        jdbc.queryForList(
            """
        SELECT symbol FROM portfolio_delayed_quotes
        WHERE market = :market AND requested_at >= :since
        ORDER BY queued_at, symbol
        LIMIT :allowed
        """,
            Map.of("market", market, "since", utc(now.minusSeconds(300)), "allowed", allowed),
            String.class);
    if (symbols.isEmpty()) return List.of();
    jdbc.update(
        """
        UPDATE portfolio_delayed_quote_budgets
        SET reserved_calls = reserved_calls + :count,
            kr_slot = CASE WHEN :us THEN kr_slot ELSE :slot END,
            us_slot = CASE WHEN :us THEN :slot ELSE us_slot END
        WHERE budget_date = :day
        """,
        Map.of("count", symbols.size(), "us", us, "slot", utc(slot), "day", day));
    return symbols;
  }

  public void markAttempted(List<String> symbols, Instant now) {
    jdbc.update(
        """
        WITH locked AS (
            SELECT symbol FROM portfolio_delayed_quotes
            WHERE symbol IN (:symbols) ORDER BY symbol FOR UPDATE
        )
        UPDATE portfolio_delayed_quotes AS quote
        SET attempted_at = :now, queued_at = GREATEST(quote.queued_at + interval '1 microsecond', :now)
        FROM locked WHERE quote.symbol = locked.symbol
        """,
        Map.of("now", utc(now), "symbols", symbols));
  }

  public void save(String symbol, BigDecimal price, Instant asOf) {
    jdbc.update(
        """
        UPDATE portfolio_delayed_quotes SET price = :price, price_as_of = :asOf
        WHERE symbol = :symbol AND (price_as_of IS NULL OR price_as_of <= :asOf)
        """,
        Map.of("symbol", symbol, "price", price, "asOf", utc(asOf)));
  }

  public void exhaust(Instant now) {
    jdbc.update(
        "UPDATE portfolio_delayed_quote_budgets SET exhausted = true WHERE budget_date = :day",
        Map.of("day", utc(now).toLocalDate()));
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  public record CachedQuote(String symbol, BigDecimal price, Instant asOf) {}

  private record Budget(
      int reservedCalls, OffsetDateTime krSlot, OffsetDateTime usSlot, boolean exhausted) {}
}
