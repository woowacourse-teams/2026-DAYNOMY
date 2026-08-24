package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.domain.PortfolioAssetImpact;

public record PortfolioAssetImpactResponse(
    @Schema(description = "북마크 ID", example = "10") Long bookmarkId,
    @Schema(description = "자산 ID", example = "1") Long assetId,
    @Schema(description = "자산 이름", example = "삼성전자") String name,
    @Schema(description = "자산 카테고리", example = "주식") String category,
    @Schema(description = "자산 식별 코드", example = "005930") String assetCode,
    @Schema(description = "영향 방향", example = "POSITIVE") ImpactDirection direction,
    @Schema(description = "영향 수준", example = "HIGH") ImpactLevel impactLevel,
    @Schema(description = "예상 자산 반응", example = "실적 개선 기대에 따라 주가가 상승할 수 있습니다.")
        String expectedReaction,
    @Schema(description = "판단 근거", example = "반도체 수요 증가가 실적 개선으로 이어질 수 있습니다.") String reason,
    @Schema(description = "영향도 정렬 순서", example = "1") int sortOrder) {

  public static PortfolioAssetImpactResponse from(PortfolioAssetImpact impact) {
    Asset asset = impact.getAsset();
    return new PortfolioAssetImpactResponse(
        impact.getBookmark().getId(),
        asset.getId(),
        asset.getName(),
        asset.getCategory().name(),
        asset.getAssetCode(),
        impact.getDirection(),
        impact.getImpactLevel(),
        impact.getExpectedReaction(),
        impact.getReason(),
        impact.getSortOrder());
  }

  public static PortfolioAssetImpactResponse of(
      PortfolioAnalysisResult.AssetImpactResult impact, Asset asset) {
    return new PortfolioAssetImpactResponse(
        impact.bookmarkId(),
        impact.assetId(),
        asset.getName(),
        asset.getCategory().name(),
        asset.getAssetCode(),
        impact.direction(),
        impact.impactLevel(),
        impact.expectedReaction(),
        impact.reason(),
        impact.sortOrder());
  }
}
