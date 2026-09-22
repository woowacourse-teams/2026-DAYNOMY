package org.grit.daynomy.asset.dto;

import java.time.LocalDate;
import org.grit.daynomy.asset.service.StockSyncResult;

public record StockSyncResponse(
    LocalDate baseDate, int syncedCount, int createdCount, int updatedCount, int delistedCount) {

  public static StockSyncResponse from(StockSyncResult result) {
    return new StockSyncResponse(
        result.baseDate(),
        result.syncedCount(),
        result.createdCount(),
        result.updatedCount(),
        result.delistedCount());
  }
}
