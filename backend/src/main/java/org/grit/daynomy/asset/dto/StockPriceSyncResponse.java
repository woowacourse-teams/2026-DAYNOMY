package org.grit.daynomy.asset.dto;

import java.time.LocalDate;
import org.grit.daynomy.asset.service.StockPriceSyncResult;

public record StockPriceSyncResponse(
    LocalDate baseDate, int receivedCount, int createdCount, int updatedCount, int skippedCount) {

  public static StockPriceSyncResponse from(StockPriceSyncResult result) {
    return new StockPriceSyncResponse(
        result.baseDate(),
        result.receivedCount(),
        result.createdCount(),
        result.updatedCount(),
        result.skippedCount());
  }
}
