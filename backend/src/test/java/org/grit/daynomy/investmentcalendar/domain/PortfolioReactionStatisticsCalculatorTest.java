package org.grit.daynomy.investmentcalendar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortfolioReactionStatisticsCalculatorTest {

  private final PortfolioReactionStatisticsCalculator calculator =
      new PortfolioReactionStatisticsCalculator();

  @Test
  void calculatesMedianReactionRangeAndEstimatedAmount() {
    Map<InvestmentEventDirection, List<BigDecimal>> groups =
        Map.of(
            InvestmentEventDirection.INCREASED,
            List.of(
                new BigDecimal("-2.00"),
                new BigDecimal("-1.00"),
                new BigDecimal("1.00"),
                new BigDecimal("3.00")));

    PortfolioReactionStatistics statistics =
        calculator.summarize(groups, new BigDecimal("1000000")).getLast();

    assertThat(statistics.sampleCount()).isEqualTo(4);
    assertThat(statistics.medianReturnRate()).isEqualByComparingTo("0.00");
    assertThat(statistics.lowerReturnRate()).isEqualByComparingTo("-1.00");
    assertThat(statistics.upperReturnRate()).isEqualByComparingTo("1.00");
    assertThat(statistics.lowerEstimatedAmount()).isEqualByComparingTo("-10000");
    assertThat(statistics.upperEstimatedAmount()).isEqualByComparingTo("10000");
  }

  @Test
  void hidesStatisticsWhenSampleCountIsTooSmall() {
    Map<InvestmentEventDirection, List<BigDecimal>> groups =
        Map.of(
            InvestmentEventDirection.DECREASED,
            List.of(new BigDecimal("-1.00"), new BigDecimal("2.00")));

    PortfolioReactionStatistics statistics =
        calculator.summarize(groups, new BigDecimal("1000000")).getFirst();

    assertThat(statistics.sampleCount()).isEqualTo(2);
    assertThat(statistics.available()).isFalse();
  }

  @Test
  void classifiesImpactUsingLargestAbsoluteMedian() {
    List<PortfolioReactionStatistics> statistics =
        List.of(
            new PortfolioReactionStatistics(
                InvestmentEventDirection.INCREASED,
                3,
                new BigDecimal("-1.20"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO));

    assertThat(calculator.impactLevel(statistics)).isEqualTo(PortfolioEventImpactLevel.HIGH);
  }
}
