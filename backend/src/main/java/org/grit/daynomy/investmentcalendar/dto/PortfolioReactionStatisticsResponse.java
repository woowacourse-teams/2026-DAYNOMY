package org.grit.daynomy.investmentcalendar.dto;

import java.math.BigDecimal;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventDirection;
import org.grit.daynomy.investmentcalendar.domain.PortfolioReactionStatistics;

public record PortfolioReactionStatisticsResponse(
    InvestmentEventDirection direction,
    int sampleCount,
    BigDecimal medianReturnRate,
    BigDecimal lowerReturnRate,
    BigDecimal upperReturnRate,
    BigDecimal lowerEstimatedAmount,
    BigDecimal upperEstimatedAmount) {

  public static PortfolioReactionStatisticsResponse from(PortfolioReactionStatistics statistics) {
    return new PortfolioReactionStatisticsResponse(
        statistics.direction(),
        statistics.sampleCount(),
        statistics.medianReturnRate(),
        statistics.lowerReturnRate(),
        statistics.upperReturnRate(),
        statistics.lowerEstimatedAmount(),
        statistics.upperEstimatedAmount());
  }
}
