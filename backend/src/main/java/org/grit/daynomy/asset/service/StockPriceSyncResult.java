package org.grit.daynomy.asset.service;

import java.time.LocalDate;

public record StockPriceSyncResult(
    LocalDate baseDate,
    int receivedCount,
    int createdCount,
    int updatedCount,
    int skippedCount,
    LocalDate earliestBaseDate) {
  public StockPriceSyncResult(
      LocalDate baseDate, int receivedCount, int createdCount, int updatedCount, int skippedCount) {
    this(baseDate, receivedCount, createdCount, updatedCount, skippedCount, baseDate);
  }

  public StockPriceSyncResult withEarliestBaseDate(LocalDate date) {
    return new StockPriceSyncResult(
        baseDate, receivedCount, createdCount, updatedCount, skippedCount, date);
  }
}
