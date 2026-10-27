package org.grit.daynomy.emailsubscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;

public record AdminEmailAnalysisItemResponse(
    Long id,
    Long assetId,
    @Schema(description = "현재 종목명") String assetName,
    @Schema(description = "분석 당시 종목 코드") String assetCode,
    StockMarket market,
    AssetCategory category,
    CommonStockAnalysis.Status status,
    @Schema(description = "분석 기준 시점") Instant asOf,
    Instant analyzedAt,
    Instant invalidatedAt) {
  public static AdminEmailAnalysisItemResponse from(CommonStockAnalysis analysis) {
    return new AdminEmailAnalysisItemResponse(
        analysis.getId(),
        analysis.getAsset().getId(),
        analysis.getAsset().getName(),
        analysis.getAssetCode(),
        analysis.getMarket(),
        analysis.getCategory(),
        analysis.getStatus(),
        analysis.getAsOf(),
        analysis.getAnalyzedAt(),
        analysis.getInvalidatedAt());
  }
}
