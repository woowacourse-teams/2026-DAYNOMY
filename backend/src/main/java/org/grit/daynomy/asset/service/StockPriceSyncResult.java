package org.grit.daynomy.asset.service;

import java.time.LocalDate;

public record StockPriceSyncResult(
    LocalDate baseDate, int receivedCount, int createdCount, int updatedCount, int skippedCount) {}
