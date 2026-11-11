package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPriceContext;

public record PortfolioAnalysisPriceContextResponse(
    @Schema(
            description =
                "AVAILABLE, INSUFFICIENT_HISTORY, STALE_PRICE(7일 초과), UNAVAILABLE, AMBIGUOUS_ASSET, LOOKUP_FAILED")
        String status,
    @Schema(description = "종목 통화. 개인 손익의 원화 통화와 구분") String currency,
    @Schema(description = "CLOSE: 확정 종가, DELAYED: 30분 이내 저장된 지연 시세") String priceType,
    @Schema(description = "가격의 현지 시장 기준일") LocalDate baseDate,
    @Schema(description = "지연 시세 기준 시각. 종가는 기준일만 제공하며 null") Instant asOf,
    BigDecimal price,
    @Schema(description = "최근 30일 범위에서 실제 확보한 최초 비교 가격의 기준일") LocalDate comparisonDate,
    BigDecimal comparisonPrice,
    @Schema(description = "실제 비교일 대비 가격 등락률(%). 비교 자료 부족 시 null, 개인 수익률이 아님") BigDecimal changeRate,
    @Schema(description = "최근 30일 범위에서 확보한 가격 관측일 수") int observationCount) {
  public static PortfolioAnalysisPriceContextResponse from(PortfolioAnalysisPriceContext context) {
    return new PortfolioAnalysisPriceContextResponse(
        context.status().name(),
        context.currency(),
        context.priceType(),
        context.baseDate(),
        context.asOf(),
        context.price(),
        context.comparisonDate(),
        context.comparisonPrice(),
        context.changeRate(),
        context.observationCount());
  }

  public PortfolioAnalysisPriceContext toSnapshot() {
    return new PortfolioAnalysisPriceContext(
        PortfolioAnalysisPriceContext.Status.valueOf(status),
        currency,
        priceType,
        baseDate,
        asOf,
        price,
        comparisonDate,
        comparisonPrice,
        changeRate,
        observationCount);
  }
}
