package org.grit.daynomy.asset.service;

public record UsStockKoreanNameSyncResult(
    int downloadedCount,
    int matchedCount,
    int updatedCount,
    int unmatchedCount,
    int skippedCount) {}
