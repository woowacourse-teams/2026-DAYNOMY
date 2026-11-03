package org.grit.daynomy.asset.dto;

import java.time.LocalDate;
import org.grit.daynomy.asset.service.UsStockSyncResult;

public record UsStockSyncResponse(
    LocalDate baseDate,
    int syncedCount,
    int createdCount,
    int updatedCount,
    int delistedCount,
    UsStockKoreanNameSyncResponse koreanNames,
    String koreanNameErrorCode) {
  public static UsStockSyncResponse from(UsStockSyncResult result) {
    var master = result.master();
    return new UsStockSyncResponse(
        master.baseDate(),
        master.syncedCount(),
        master.createdCount(),
        master.updatedCount(),
        master.delistedCount(),
        result.koreanNames() == null
            ? null
            : UsStockKoreanNameSyncResponse.from(result.koreanNames()),
        result.koreanNameErrorCode());
  }
}
