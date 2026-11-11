package org.grit.daynomy.portfolio.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 회원의 분석 시점 손익. 종목 공통 분석·캐시에 전달하지 않는다. 금액은 KRW다. */
public record PortfolioAnalysisPersonalContext(
    LocalDate baseDate,
    BigDecimal purchaseAmount,
    BigDecimal evaluationAmount,
    BigDecimal profitLoss,
    BigDecimal returnRate,
    List<Holding> holdings) {
  public PortfolioAnalysisPersonalContext {
    holdings = List.copyOf(holdings);
  }

  public record Holding(
      String assetName,
      LocalDate baseDate,
      long quantity,
      BigDecimal averagePurchasePrice,
      BigDecimal profitLoss,
      BigDecimal returnRate) {}
}
