package org.grit.daynomy.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;

public record PortfolioHoldingResponse(
    Long assetId,
    String assetCode,
    String name,
    AssetCategory category,
    StockMarket market,
    boolean priceAvailable,
    LocalDate baseDate,
    long quantity,
    BigDecimal averagePurchasePrice,
    BigDecimal closePrice,
    BigDecimal purchaseAmount,
    BigDecimal evaluationAmount,
    BigDecimal profitLoss,
    BigDecimal returnRate,
    BigDecimal weight) {
  @com.fasterxml.jackson.annotation.JsonProperty("averagePurchasePriceCurrency")
  public String averagePurchasePriceCurrency() {
    return "KRW";
  }

  @com.fasterxml.jackson.annotation.JsonProperty("currency")
  public String currency() {
    return market == null ? "KRW" : market.currency();
  }

  @com.fasterxml.jackson.annotation.JsonProperty("evaluationCurrency")
  public String evaluationCurrency() {
    return "KRW";
  }
}
