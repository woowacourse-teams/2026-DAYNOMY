package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;

public record SavedPortfolioHoldingResponse(
    @Schema(description = "종목 ID", example = "1") Long assetId,
    @Schema(description = "종목 코드", example = "005930") String assetCode,
    @Schema(description = "종목명", example = "삼성전자") String name,
    @Schema(description = "자산 분류", example = "STOCK") AssetCategory category,
    @Schema(description = "상장 시장", example = "KOSPI") StockMarket market,
    @Schema(description = "보유 수량(주)", example = "10") long quantity,
    @Schema(description = "원화(KRW) 기준 주당 평균 매입가", example = "70000.00")
        BigDecimal averagePurchasePrice) {
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

  public static SavedPortfolioHoldingResponse from(PortfolioHolding holding) {
    return new SavedPortfolioHoldingResponse(
        holding.getAsset().getId(),
        holding.getAsset().getAssetCode(),
        holding.getAsset().getName(),
        holding.getAsset().getCategory(),
        holding.getAsset().getMarket(),
        holding.getQuantity(),
        holding.getAveragePurchasePrice());
  }
}
