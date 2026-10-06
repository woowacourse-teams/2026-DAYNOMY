package org.grit.daynomy.portfolio.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;

public record PortfolioImpactSummary(
    ImpactDirection direction,
    BigDecimal score,
    BigDecimal positiveScore,
    BigDecimal negativeScore) {

  private static final BigDecimal DIRECTION_THRESHOLD = BigDecimal.valueOf(15);
  private static final int SCORE_SCALE = 2;

  public static PortfolioImpactSummary calculate(List<AssetImpact> impacts) {
    BigDecimal positiveScore = BigDecimal.ZERO;
    BigDecimal negativeScore = BigDecimal.ZERO;

    for (AssetImpact impact : impacts) {
      BigDecimal contribution = impact.weight().multiply(levelWeight(impact.impactLevel()));
      if (impact.direction() == ImpactDirection.POSITIVE) {
        positiveScore = positiveScore.add(contribution);
      } else if (impact.direction() == ImpactDirection.NEGATIVE) {
        negativeScore = negativeScore.add(contribution);
      }
    }

    BigDecimal score = rounded(positiveScore.subtract(negativeScore));
    BigDecimal roundedPositiveScore = rounded(positiveScore);
    BigDecimal roundedNegativeScore = rounded(negativeScore);

    return new PortfolioImpactSummary(
        directionOf(score), score, roundedPositiveScore, roundedNegativeScore);
  }

  private static BigDecimal levelWeight(ImpactLevel impactLevel) {
    return switch (impactLevel) {
      case HIGH -> BigDecimal.ONE;
      case MEDIUM -> BigDecimal.valueOf(0.6);
      case LOW -> BigDecimal.valueOf(0.3);
    };
  }

  private static ImpactDirection directionOf(BigDecimal score) {
    if (score.compareTo(DIRECTION_THRESHOLD) >= 0) {
      return ImpactDirection.POSITIVE;
    }
    if (score.compareTo(DIRECTION_THRESHOLD.negate()) <= 0) {
      return ImpactDirection.NEGATIVE;
    }
    return ImpactDirection.NEUTRAL;
  }

  private static BigDecimal rounded(BigDecimal score) {
    return score.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
  }

  public record AssetImpact(
      BigDecimal weight, ImpactDirection direction, ImpactLevel impactLevel) {}
}
