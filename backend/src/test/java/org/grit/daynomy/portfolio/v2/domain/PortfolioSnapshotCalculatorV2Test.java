package org.grit.daynomy.portfolio.v2.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator.Position;
import org.junit.jupiter.api.Test;

class PortfolioSnapshotCalculatorV2Test {

  private final PortfolioSnapshotCalculatorV2 calculator = new PortfolioSnapshotCalculatorV2();

  @Test
  void preservesMoneyAndRateRounding() {
    var amount =
        calculator
            .calculate(
                List.of(position(3, "10.01")),
                Map.of(1L, new BigDecimal("12.34")),
                Map.of(1L, new BigDecimal("11.11")),
                true)
            .orElseThrow();
    assertThat(amount.totalPurchase()).isEqualByComparingTo("30.03");
    assertThat(amount.totalEvaluation()).isEqualByComparingTo("37.02");
    assertThat(amount.totalProfitLoss()).isEqualByComparingTo("6.99");
    assertThat(amount.totalReturnRate()).isEqualByComparingTo("23.28");
    assertThat(amount.dailyProfitLoss()).isEqualByComparingTo("3.69");
    assertThat(amount.dailyReturnRate()).isEqualByComparingTo("11.07");
  }

  @Test
  void missingCurrentOrRequiredPreviousPricePreventsPartialSnapshot() {
    assertThat(calculator.calculate(List.of(position(3, "10")), Map.of(), Map.of(), false))
        .isEmpty();
    assertThat(
            calculator.calculate(
                List.of(position(3, "10")), Map.of(1L, new BigDecimal("12")), Map.of(), true))
        .isEmpty();
    assertThat(calculator.calculate(List.of(), Map.of(), Map.of(), false)).isEmpty();
  }

  @Test
  void firstPriceDateKeepsDailyPerformanceUnavailable() {
    var amount =
        calculator
            .calculate(
                List.of(position(3, "10")), Map.of(1L, new BigDecimal("12")), Map.of(), false)
            .orElseThrow();
    assertThat(amount.dailyProfitLoss()).isNull();
    assertThat(amount.dailyReturnRate()).isNull();
    assertThat(amount.totalReturnRate()).isEqualByComparingTo("20");
  }

  @Test
  void overflowingStoragePrecisionFailsInsteadOfTruncating() {
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    List.of(position(100, "10")),
                    Map.of(1L, new BigDecimal("99999999999999999.99")),
                    Map.of(),
                    false))
        .isInstanceOf(ArithmeticException.class);
  }

  private Position position(long quantity, String average) {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(1L);
    return new Position(asset, quantity, new BigDecimal(average));
  }
}
