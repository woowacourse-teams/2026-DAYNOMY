package org.grit.daynomy.asset.dto;

import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;

public record StockSearchItemResponse(
    Long assetId, String assetCode, String name, StockMarket market) {

  public static StockSearchItemResponse from(Asset asset) {
    return new StockSearchItemResponse(
        asset.getId(), asset.getAssetCode(), asset.getName(), asset.getMarket());
  }
}
