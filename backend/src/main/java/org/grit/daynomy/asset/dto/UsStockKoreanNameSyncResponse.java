package org.grit.daynomy.asset.dto;

import org.grit.daynomy.asset.service.UsStockKoreanNameSyncResult;

public record UsStockKoreanNameSyncResponse(
    int downloadedCount, int matchedCount, int updatedCount, int unmatchedCount, int skippedCount) {
  public static UsStockKoreanNameSyncResponse from(UsStockKoreanNameSyncResult result) {
    return new UsStockKoreanNameSyncResponse(
        result.downloadedCount(),
        result.matchedCount(),
        result.updatedCount(),
        result.unmatchedCount(),
        result.skippedCount());
  }
}
