package org.grit.daynomy.asset.dto;

import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;

public record StockSearchItemResponse(
    Long assetId, String assetCode, String name, AssetCategory category, StockMarket market) {

  public static StockSearchItemResponse from(Asset asset) {
    return new StockSearchItemResponse(
        asset.getId(),
        asset.getAssetCode(),
        asset.getName(),
        asset.getCategory(),
        asset.getMarket());
  }
}
