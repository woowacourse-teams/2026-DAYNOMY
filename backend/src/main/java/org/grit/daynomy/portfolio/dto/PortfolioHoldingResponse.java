package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;

public record PortfolioHoldingResponse(
    @Schema(description = "종목 ID", example = "1") Long assetId,
    @Schema(description = "종목 코드", example = "005930") String assetCode,
    @Schema(description = "종목명", example = "삼성전자") String name,
    @Schema(description = "자산 분류", example = "STOCK") AssetCategory category,
    @Schema(description = "상장 시장", example = "KOSPI") StockMarket market,
    @Schema(description = "종가와 필요한 환율이 있어 평가 가능한지 여부", example = "true") boolean priceAvailable,
    @Schema(description = "사용한 종가 기준일. 평가 불가하면 null", example = "2026-10-22", nullable = true)
        LocalDate baseDate,
    @Schema(description = "보유 수량(주)", example = "10") long quantity,
    @Schema(description = "원화(KRW) 기준 주당 평균 매입가", example = "70000.00")
        BigDecimal averagePurchasePrice,
    @Schema(
            description = "종목 통화(currency) 기준 종가. 국내 KRW, 미국 USD. 평가 불가하면 null",
            example = "75000.00",
            nullable = true)
        BigDecimal closePrice,
    @Schema(description = "매입금액(KRW). 보유 수량 × 평균 매입가", example = "700000.00")
        BigDecimal purchaseAmount,
    @Schema(
            description = "평가금액(KRW). 미국 종목은 종가 기준일의 환율로 환산. 평가 불가하면 null",
            example = "750000.00",
            nullable = true)
        BigDecimal evaluationAmount,
    @Schema(description = "평가손익(KRW). 평가 불가하면 null", example = "50000.00", nullable = true)
        BigDecimal profitLoss,
    @Schema(description = "매입금액 대비 수익률(%). 평가 불가하면 null", example = "7.14", nullable = true)
        BigDecimal returnRate,
    @Schema(
            description = "평가 가능한 자산의 전체 평가금액 대비 비중(%). 평가 불가하면 null",
            example = "100.00",
            nullable = true)
        BigDecimal weight) {
  @Schema(description = "평균 매입가의 통화. 항상 KRW", example = "KRW")
  @com.fasterxml.jackson.annotation.JsonProperty("averagePurchasePriceCurrency")
  public String averagePurchasePriceCurrency() {
    return "KRW";
  }

  @Schema(description = "종목 통화. 국내 KRW, 미국 USD", example = "KRW")
  @com.fasterxml.jackson.annotation.JsonProperty("currency")
  public String currency() {
    return market == null ? "KRW" : market.currency();
  }

  @Schema(description = "평가금액과 손익의 통화. 항상 KRW", example = "KRW")
  @com.fasterxml.jackson.annotation.JsonProperty("evaluationCurrency")
  public String evaluationCurrency() {
    return "KRW";
  }
}
