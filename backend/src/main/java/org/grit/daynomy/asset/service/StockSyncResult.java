package org.grit.daynomy.asset.service;

import java.time.LocalDate;

public record StockSyncResult(
    LocalDate baseDate, int syncedCount, int createdCount, int updatedCount, int delistedCount) {}
