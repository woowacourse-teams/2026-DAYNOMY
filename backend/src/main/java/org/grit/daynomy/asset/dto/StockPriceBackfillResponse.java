package org.grit.daynomy.asset.dto;

import java.time.LocalDate;
import org.grit.daynomy.asset.service.StockPriceBackfillResult;

public record StockPriceBackfillResponse(
    LocalDate from,
    LocalDate to,
    int synchronizedDateCount,
    int receivedCount,
    int createdCount,
    int updatedCount,
    int skippedCount) {

  public static StockPriceBackfillResponse from(StockPriceBackfillResult result) {
    return new StockPriceBackfillResponse(
        result.from(),
        result.to(),
        result.synchronizedDateCount(),
        result.receivedCount(),
        result.createdCount(),
        result.updatedCount(),
        result.skippedCount());
  }
}
