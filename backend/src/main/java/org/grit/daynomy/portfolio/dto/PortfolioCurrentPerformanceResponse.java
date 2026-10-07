package org.grit.daynomy.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioCurrentPerformanceResponse(
    LocalDate baseDate,
    LocalDate priceBaseDate,
    BigDecimal totalPurchaseAmount,
    BigDecimal totalEvaluationAmount,
    BigDecimal totalProfitLoss,
    BigDecimal totalReturnRate,
    BigDecimal dailyProfitLoss,
    BigDecimal dailyReturnRate) {

  public static PortfolioCurrentPerformanceResponse from(
      LocalDate baseDate, PortfolioCalculationResponse calculation) {
    return new PortfolioCurrentPerformanceResponse(
        baseDate,
        calculation.baseDate(),
        calculation.totalPurchaseAmount(),
        calculation.totalEvaluationAmount(),
        calculation.totalProfitLoss(),
        calculation.totalReturnRate(),
        calculation.dailyProfitLoss(),
        calculation.dailyReturnRate());
  }

  public static PortfolioCurrentPerformanceResponse empty(LocalDate baseDate) {
    BigDecimal zero = BigDecimal.ZERO.setScale(2);
    return new PortfolioCurrentPerformanceResponse(
        baseDate, null, zero, zero, zero, zero, null, null);
  }
}
