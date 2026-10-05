package org.grit.daynomy.investmentcalendar.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class PortfolioReactionStatisticsCalculator {

  public static final int MINIMUM_SAMPLE_COUNT = 3;
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  public List<PortfolioReactionStatistics> summarize(
      Map<InvestmentEventDirection, List<BigDecimal>> returnsByDirection,
      BigDecimal totalEvaluationAmount) {
    List<PortfolioReactionStatistics> result = new ArrayList<>();
    for (InvestmentEventDirection direction : resultDirections()) {
      List<BigDecimal> rates =
          returnsByDirection.getOrDefault(direction, List.of()).stream()
              .sorted(Comparator.naturalOrder())
              .toList();
      result.add(statistics(direction, rates, totalEvaluationAmount));
    }
    return List.copyOf(result);
  }

  public PortfolioEventImpactLevel impactLevel(List<PortfolioReactionStatistics> statistics) {
    BigDecimal maximumMedian =
        statistics.stream()
            .filter(PortfolioReactionStatistics::available)
            .map(PortfolioReactionStatistics::medianReturnRate)
            .map(BigDecimal::abs)
            .max(Comparator.naturalOrder())
            .orElse(BigDecimal.ZERO);
    if (maximumMedian.compareTo(BigDecimal.ONE) >= 0) {
      return PortfolioEventImpactLevel.HIGH;
    }
    if (maximumMedian.compareTo(new BigDecimal("0.50")) >= 0) {
      return PortfolioEventImpactLevel.MEDIUM;
    }
    return PortfolioEventImpactLevel.LOW;
  }

  public Map<InvestmentEventDirection, List<BigDecimal>> emptyGroups() {
    return new EnumMap<>(InvestmentEventDirection.class);
  }

  private PortfolioReactionStatistics statistics(
      InvestmentEventDirection direction,
      List<BigDecimal> sortedRates,
      BigDecimal totalEvaluationAmount) {
    int sampleCount = sortedRates.size();
    if (sampleCount < MINIMUM_SAMPLE_COUNT) {
      return new PortfolioReactionStatistics(direction, sampleCount, null, null, null, null, null);
    }
    BigDecimal median = median(sortedRates);
    BigDecimal lower = percentile(sortedRates, 0.25);
    BigDecimal upper = percentile(sortedRates, 0.75);
    return new PortfolioReactionStatistics(
        direction,
        sampleCount,
        rate(median),
        rate(lower),
        rate(upper),
        money(totalEvaluationAmount.multiply(lower).divide(HUNDRED, 8, RoundingMode.HALF_UP)),
        money(totalEvaluationAmount.multiply(upper).divide(HUNDRED, 8, RoundingMode.HALF_UP)));
  }

  private BigDecimal median(List<BigDecimal> sortedRates) {
    int middle = sortedRates.size() / 2;
    if (sortedRates.size() % 2 == 1) {
      return sortedRates.get(middle);
    }
    return sortedRates
        .get(middle - 1)
        .add(sortedRates.get(middle))
        .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
  }

  private BigDecimal percentile(List<BigDecimal> sortedRates, double percentile) {
    int index = (int) Math.round((sortedRates.size() - 1) * percentile);
    return sortedRates.get(index);
  }

  private BigDecimal rate(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal money(BigDecimal value) {
    return value.setScale(0, RoundingMode.HALF_UP);
  }

  private List<InvestmentEventDirection> resultDirections() {
    return List.of(
        InvestmentEventDirection.DECREASED,
        InvestmentEventDirection.UNCHANGED,
        InvestmentEventDirection.INCREASED);
  }
}
