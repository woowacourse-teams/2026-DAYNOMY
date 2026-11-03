package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioCurrentPerformanceResponse(
    @Schema(description = "현재 값의 기준일(서울 기준 오늘)", example = "2026-10-23") LocalDate baseDate,
    @Schema(description = "평가에 사용한 종가 기준일. 보유자산이 없으면 null", example = "2026-10-22", nullable = true)
        LocalDate priceBaseDate,
    @Schema(description = "전체 매입금액(KRW)", example = "700000.00") BigDecimal totalPurchaseAmount,
    @Schema(description = "전체 평가금액(KRW)", example = "750000.00") BigDecimal totalEvaluationAmount,
    @Schema(description = "평가손익(KRW). 평가금액 − 매입금액", example = "50000.00")
        BigDecimal totalProfitLoss,
    @Schema(description = "매입금액 대비 수익률(%). 7.14는 7.14%를 의미", example = "7.14")
        BigDecimal totalReturnRate,
    @Schema(description = "현재 값의 일별 손익은 제공하지 않으므로 항상 null", nullable = true)
        BigDecimal dailyProfitLoss,
    @Schema(description = "현재 값의 일별 수익률은 제공하지 않으므로 항상 null", nullable = true)
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
        null,
        null);
  }

  public static PortfolioCurrentPerformanceResponse empty(LocalDate baseDate) {
    BigDecimal zero = BigDecimal.ZERO.setScale(2);
    return new PortfolioCurrentPerformanceResponse(
        baseDate, null, zero, zero, zero, zero, null, null);
  }
}
