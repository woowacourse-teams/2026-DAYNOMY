package org.grit.daynomy.asset.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.grit.daynomy.asset.domain.StockDailyPrice;

public record StockPriceResponse(
    Long assetId, String assetCode, String name, LocalDate baseDate, BigDecimal closePrice) {

  public static StockPriceResponse from(StockDailyPrice price) {
    return new StockPriceResponse(
        price.getAsset().getId(),
        price.getAsset().getAssetCode(),
        price.getAsset().getName(),
        price.getBaseDate(),
        price.getClosePrice());
  }
}
