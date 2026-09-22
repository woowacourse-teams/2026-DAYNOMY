package org.grit.daynomy.asset.dto;

import java.util.List;
import org.grit.daynomy.asset.domain.Asset;

public record StockSearchResponse(List<StockSearchItemResponse> stocks) {

  public static StockSearchResponse from(List<Asset> assets) {
    return new StockSearchResponse(assets.stream().map(StockSearchItemResponse::from).toList());
  }
}
