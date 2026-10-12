package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PortfolioHoldingHistoryTest {

  private static final LocalDate BALANCE_DATE = LocalDate.of(2026, 10, 1);

  @ParameterizedTest
  @EnumSource(
      value = PortfolioHoldingChangeType.class,
      names = {"OPENING_BALANCE", "ADJUSTED", "ADDED", "UPDATED"})
  void balanceBlocksOnlyEarlierTradeDates(PortfolioHoldingChangeType type) {
    PortfolioHoldingHistory history = history(type);

    assertThat(history.isBalanceAfter(BALANCE_DATE.minusDays(1))).isTrue();
    assertThat(history.isBalanceAfter(BALANCE_DATE)).isFalse();
    assertThat(history.isBalanceAfter(BALANCE_DATE.plusDays(1))).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = PortfolioHoldingChangeType.class,
      names = {"BUY", "SELL", "REMOVED"})
  void nonBalanceHistoriesDoNotRestrictTradeDates(PortfolioHoldingChangeType type) {
    assertThat(history(type).isBalanceAfter(BALANCE_DATE.minusDays(1))).isFalse();
  }

  private PortfolioHoldingHistory history(PortfolioHoldingChangeType type) {
    return new PortfolioHoldingHistory(
        null,
        null,
        type,
        10L,
        new BigDecimal("10000"),
        BALANCE_DATE,
        null,
        null,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }
}
