package org.grit.daynomy.investmentcalendar.domain;

import java.math.BigDecimal;

public record PortfolioReactionStatistics(
    InvestmentEventDirection direction,
    int sampleCount,
    BigDecimal medianReturnRate,
    BigDecimal lowerReturnRate,
    BigDecimal upperReturnRate,
    BigDecimal lowerEstimatedAmount,
    BigDecimal upperEstimatedAmount) {

  public boolean available() {
    return medianReturnRate != null;
  }
}
