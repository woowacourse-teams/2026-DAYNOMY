package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;

public record PortfolioPerformancePointResponse(
    @Schema(description = "기준일(yyyy-MM-dd)", example = "2026-10-22") LocalDate baseDate,
    @Schema(description = "전체 매입금액(KRW)", example = "700000.00") BigDecimal totalPurchaseAmount,
    @Schema(description = "전체 평가금액(KRW)", example = "750000.00") BigDecimal totalEvaluationAmount,
    @Schema(description = "평가손익(KRW). 평가금액 − 매입금액", example = "50000.00")
        BigDecimal totalProfitLoss,
    @Schema(description = "매입금액 대비 수익률(%). 7.14는 7.14%를 의미", example = "7.14")
        BigDecimal totalReturnRate,
    @Schema(
            description = "해당 기준일 보유 수량으로 계산한 현재·직전 기준일 평가금액 차이(KRW). 직전 기준일이 없으면 null",
            example = "10000.00",
            nullable = true)
        BigDecimal dailyProfitLoss,
    @Schema(
            description = "해당 기준일 보유 수량의 직전 기준일 평가금액 대비 등락률(%). 직전 기준일이 없으면 null",
            example = "1.35",
            nullable = true)
        BigDecimal dailyReturnRate) {

  public static PortfolioPerformancePointResponse from(PortfolioDailySnapshot snapshot) {
    return new PortfolioPerformancePointResponse(
        snapshot.getBaseDate(),
        snapshot.getTotalPurchaseAmount(),
        snapshot.getTotalEvaluationAmount(),
        snapshot.getTotalProfitLoss(),
        snapshot.getTotalReturnRate(),
        snapshot.getDailyProfitLoss(),
        snapshot.getDailyReturnRate());
  }
}
