package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;

public record PortfolioAssetImpactResponse(
    @Schema(description = "자산 ID", example = "1") Long assetId,
    @Schema(description = "자산 이름", example = "삼성전자") String assetName,
    @Schema(description = "자산 카테고리", example = "STOCK") String category,
    @Schema(description = "자산 식별 코드", example = "005930") String assetCode,
    @Schema(description = "전체 포트폴리오에서 자산이 차지하는 비중", example = "30") BigDecimal weight,
    @Schema(description = "영향 방향", example = "POSITIVE") ImpactDirection direction,
    @Schema(description = "영향 수준", example = "HIGH") ImpactLevel impactLevel,
    @Schema(description = "한 줄 분석 요약", example = "실적 개선 기대에 따라 긍정적인 영향이 예상됩니다.") String summary,
    @Schema(description = "판단 근거", example = "반도체 수요 증가가 실적 개선으로 이어질 수 있습니다.") String reason,
    @Schema(description = "판단에 사용한 뉴스 원문 문장", example = "반도체 수요가 전년 대비 증가했습니다.")
        String evidenceSentence,
    @Schema(description = "뉴스 영향도 순위", example = "1") int rank) {

  public static PortfolioAssetImpactResponse of(
      PortfolioAnalysisResult.AssetImpactResult impact, Asset asset, BigDecimal weight) {
    return new PortfolioAssetImpactResponse(
        impact.assetId(),
        asset.getName(),
        asset.getCategory().name(),
        asset.getAssetCode(),
        weight,
        impact.direction(),
        impact.impactLevel(),
        impact.expectedReaction(),
        impact.reason(),
        impact.evidenceSentence(),
        impact.sortOrder());
  }
}
