package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPriceContext.Observation;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPriceContext.Status;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisPriceContextTest {
  private final LocalDate today = LocalDate.of(2026, 11, 11);
  private final Instant now = Instant.parse("2026-11-11T03:00:00Z");

  @Test
  void calculatesNativeCurrencyChangeUsingActualAvailableDates() {
    var result = calculate(List.of(close(20, "200"), close(1, "180")));
    assertThat(result.status()).isEqualTo(Status.AVAILABLE);
    assertThat(result.changeRate()).isEqualByComparingTo("-10.00");
    assertThat(result.currency()).isEqualTo("USD");
    assertThat(result.comparisonDate()).isEqualTo(today.minusDays(20));
    assertThat(result.baseDate()).isEqualTo(today.minusDays(1));
    assertThat(result.priceType()).isEqualTo("CLOSE");
    assertThat(result.asOf()).isNull();
  }

  @Test
  void insufficientHistoryDoesNotBecomeZeroPercent() {
    var result = calculate(List.of(close(1, "200")));
    assertThat(result.status()).isEqualTo(Status.INSUFFICIENT_HISTORY);
    assertThat(result.price()).isEqualByComparingTo("200");
    assertThat(result.changeRate()).isNull();
    assertThat(result.comparisonDate()).isNull();
  }

  @Test
  void excludesFutureInvalidAndOutOfWindowComparisonPrices() {
    var result =
        calculate(
            List.of(
                close(40, "100"),
                close(-1, "999"),
                close(3, "0"),
                close(2, "-10"),
                close(1, "200")));
    assertThat(result.price()).isEqualByComparingTo("200");
    assertThat(result.observationCount()).isEqualTo(1);
    assertThat(result.changeRate()).isNull();
    assertThat(calculate(List.of(close(-1, "100"))).status()).isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  void staleLastKnownPriceRemainsDatedAndIsNotCalledCurrent() {
    var result = calculate(List.of(close(40, "200")));
    assertThat(result.status()).isEqualTo(Status.STALE_PRICE);
    assertThat(result.baseDate()).isEqualTo(today.minusDays(40));
    assertThat(result.changeRate()).isNull();
  }

  @Test
  void identifiesDelayedQuotesAndRejectsFutureTimestamps() {
    var fresh = new Observation(today, now.minusSeconds(300), new BigDecimal("210"), "DELAYED");
    var future = new Observation(today, now.plusSeconds(1), new BigDecimal("999"), "DELAYED");
    var result = calculate(List.of(close(3, "200"), fresh, future));
    assertThat(result.priceType()).isEqualTo("DELAYED");
    assertThat(result.asOf()).isEqualTo(now.minusSeconds(300));
    assertThat(result.changeRate()).isEqualByComparingTo("5.00");
  }

  private Observation close(int daysAgo, String price) {
    return new Observation(today.minusDays(daysAgo), null, new BigDecimal(price), "CLOSE");
  }

  private PortfolioAnalysisPriceContext calculate(List<Observation> observations) {
    return PortfolioAnalysisPriceContext.calculate(
        "USD", observations, today.minusDays(30), today, now);
  }
}
