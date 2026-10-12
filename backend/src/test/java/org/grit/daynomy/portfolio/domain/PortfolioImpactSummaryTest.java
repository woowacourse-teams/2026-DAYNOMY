package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioImpactSummaryTest {

  @Test
  @DisplayName("부정 자산이 더 많아도 비중과 영향 수준이 큰 긍정 자산이 있으면 긍정으로 계산한다")
  void calculatePositiveDirectionByWeightedImpact() {
    PortfolioImpactSummary summary =
        PortfolioImpactSummary.calculate(
            List.of(
                impact("70", ImpactDirection.POSITIVE, ImpactLevel.HIGH),
                impact("10", ImpactDirection.NEGATIVE, ImpactLevel.LOW),
                impact("10", ImpactDirection.NEGATIVE, ImpactLevel.LOW),
                impact("10", ImpactDirection.NEGATIVE, ImpactLevel.LOW)));

    assertThat(summary.direction()).isEqualTo(ImpactDirection.POSITIVE);
    assertThat(summary.score()).isEqualByComparingTo("61.00");
    assertThat(summary.positiveScore()).isEqualByComparingTo("70.00");
    assertThat(summary.negativeScore()).isEqualByComparingTo("9.00");
  }

  @Test
  @DisplayName("긍정과 부정 기여 점수의 차이가 기준 미만이면 중립으로 계산한다")
  void calculateNeutralDirectionWhenScoreIsWithinThreshold() {
    PortfolioImpactSummary summary =
        PortfolioImpactSummary.calculate(
            List.of(
                impact("50", ImpactDirection.POSITIVE, ImpactLevel.MEDIUM),
                impact("50", ImpactDirection.NEGATIVE, ImpactLevel.MEDIUM)));

    assertThat(summary.direction()).isEqualTo(ImpactDirection.NEUTRAL);
    assertThat(summary.score()).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("부정 기여 점수가 충분히 크면 부정으로 계산한다")
  void calculateNegativeDirectionByWeightedImpact() {
    PortfolioImpactSummary summary =
        PortfolioImpactSummary.calculate(
            List.of(
                impact("20", ImpactDirection.POSITIVE, ImpactLevel.LOW),
                impact("80", ImpactDirection.NEGATIVE, ImpactLevel.HIGH)));

    assertThat(summary.direction()).isEqualTo(ImpactDirection.NEGATIVE);
    assertThat(summary.score()).isEqualByComparingTo("-74.00");
  }

  private PortfolioImpactSummary.AssetImpact impact(
      String weight, ImpactDirection direction, ImpactLevel level) {
    return new PortfolioImpactSummary.AssetImpact(new BigDecimal(weight), direction, level);
  }
}
