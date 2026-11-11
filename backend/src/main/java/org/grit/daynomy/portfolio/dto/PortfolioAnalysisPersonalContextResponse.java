package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPersonalContext;

@Schema(description = "회원의 분석 당시 손익. 금액은 모두 KRW, 종목 통화 기준 가격 등락과 구분. 비회원·구형 분석은 null")
public record PortfolioAnalysisPersonalContextResponse(
    LocalDate baseDate,
    BigDecimal purchaseAmount,
    BigDecimal evaluationAmount,
    BigDecimal profitLoss,
    BigDecimal returnRate,
    List<Holding> holdings) {
  public PortfolioAnalysisPersonalContextResponse {
    holdings = List.copyOf(holdings);
  }

  public record Holding(
      String assetName,
      LocalDate baseDate,
      long quantity,
      BigDecimal averagePurchasePrice,
      BigDecimal profitLoss,
      BigDecimal returnRate) {}

  public static PortfolioAnalysisPersonalContextResponse from(
      PortfolioCalculationResponse calculation) {
    return new PortfolioAnalysisPersonalContextResponse(
        calculation.baseDate(),
        calculation.totalPurchaseAmount(),
        calculation.totalEvaluationAmount(),
        calculation.totalProfitLoss(),
        calculation.totalReturnRate(),
        calculation.holdings().stream()
            .map(
                item ->
                    new Holding(
                        item.name(),
                        item.baseDate(),
                        item.quantity(),
                        item.averagePurchasePrice(),
                        item.profitLoss(),
                        item.returnRate()))
            .toList());
  }

  public static PortfolioAnalysisPersonalContextResponse from(
      PortfolioAnalysisPersonalContext context) {
    if (context == null) return null;
    return new PortfolioAnalysisPersonalContextResponse(
        context.baseDate(),
        context.purchaseAmount(),
        context.evaluationAmount(),
        context.profitLoss(),
        context.returnRate(),
        context.holdings().stream()
            .map(
                item ->
                    new Holding(
                        item.assetName(),
                        item.baseDate(),
                        item.quantity(),
                        item.averagePurchasePrice(),
                        item.profitLoss(),
                        item.returnRate()))
            .toList());
  }

  public PortfolioAnalysisPersonalContext toSnapshot() {
    return new PortfolioAnalysisPersonalContext(
        baseDate,
        purchaseAmount,
        evaluationAmount,
        profitLoss,
        returnRate,
        holdings.stream()
            .map(
                item ->
                    new PortfolioAnalysisPersonalContext.Holding(
                        item.assetName(),
                        item.baseDate(),
                        item.quantity(),
                        item.averagePurchasePrice(),
                        item.profitLoss(),
                        item.returnRate()))
            .toList());
  }
}
