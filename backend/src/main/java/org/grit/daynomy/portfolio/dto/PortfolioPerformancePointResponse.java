package org.grit.daynomy.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;

public record PortfolioPerformancePointResponse(
    LocalDate baseDate,
    BigDecimal totalPurchaseAmount,
    BigDecimal totalEvaluationAmount,
    BigDecimal totalProfitLoss,
    BigDecimal totalReturnRate) {

  public static PortfolioPerformancePointResponse from(PortfolioDailySnapshot snapshot) {
    return new PortfolioPerformancePointResponse(
        snapshot.getBaseDate(),
        snapshot.getTotalPurchaseAmount(),
        snapshot.getTotalEvaluationAmount(),
        snapshot.getTotalProfitLoss(),
        snapshot.getTotalReturnRate());
  }
}
