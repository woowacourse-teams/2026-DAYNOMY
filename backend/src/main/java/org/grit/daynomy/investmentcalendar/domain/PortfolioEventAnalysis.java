package org.grit.daynomy.investmentcalendar.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioEventAnalysis(
    PortfolioEventAnalysisStatus status,
    PortfolioEventImpactLevel impactLevel,
    int relatedAssetCount,
    BigDecimal totalEvaluationAmount,
    LocalDate priceBaseDate,
    List<PortfolioReactionStatistics> statistics) {

  public static PortfolioEventAnalysis noPortfolio() {
    return new PortfolioEventAnalysis(
        PortfolioEventAnalysisStatus.NO_PORTFOLIO,
        PortfolioEventImpactLevel.LOW,
        0,
        BigDecimal.ZERO,
        null,
        List.of());
  }
}
