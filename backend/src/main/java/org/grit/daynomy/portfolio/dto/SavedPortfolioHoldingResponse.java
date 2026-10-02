package org.grit.daynomy.portfolio.dto;

import java.math.BigDecimal;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;

public record SavedPortfolioHoldingResponse(
    Long assetId,
    String assetCode,
    String name,
    AssetCategory category,
    StockMarket market,
    long quantity,
    BigDecimal averagePurchasePrice) {

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
