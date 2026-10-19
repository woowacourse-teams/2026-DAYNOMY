package org.grit.daynomy.asset.service;

import java.time.LocalDate;

public record StockPriceSyncResult(
    LocalDate baseDate,
    int receivedCount,
    int createdCount,
    int updatedCount,
    int skippedCount,
    LocalDate earliestBaseDate,
    int synchronizedDateCount,
    long pendingDateCount,
    int failedDateCount) {
  public StockPriceSyncResult(
      LocalDate baseDate, int receivedCount, int createdCount, int updatedCount, int skippedCount) {
    this(baseDate, receivedCount, createdCount, updatedCount, skippedCount, baseDate, 1, 0, 0);
  }

  public StockPriceSyncResult(
      LocalDate baseDate,
      int receivedCount,
      int createdCount,
      int updatedCount,
      int skippedCount,
      LocalDate earliestBaseDate) {
    this(
        baseDate,
        receivedCount,
        createdCount,
        updatedCount,
        skippedCount,
        earliestBaseDate,
        1,
        0,
        0);
  }

  public StockPriceSyncResult withEarliestBaseDate(LocalDate date) {
    return new StockPriceSyncResult(
        baseDate,
        receivedCount,
        createdCount,
        updatedCount,
        skippedCount,
        date,
        synchronizedDateCount,
        pendingDateCount,
        failedDateCount);
  }
}
