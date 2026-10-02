package org.grit.daynomy.asset.service;

import java.time.LocalDate;

public record StockPriceBackfillResult(
    LocalDate from,
    LocalDate to,
    int synchronizedDateCount,
    int receivedCount,
    int createdCount,
    int updatedCount,
    int skippedCount) {}
