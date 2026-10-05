package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;

public record PortfolioAssetImpactResponse(
    @Schema(description = "자산 이름", example = "삼성전자") String assetName,
    @Schema(description = "전체 포트폴리오에서 자산이 차지하는 비중", example = "30") BigDecimal weight,
    @Schema(description = "영향 방향", example = "POSITIVE") ImpactDirection direction,
    @Schema(description = "영향 수준", example = "HIGH") ImpactLevel impactLevel,
    @Schema(description = "자산과 관련된 주요 이슈 요약") String issueSummary,
    @Schema(description = "예상되는 시장 반응") String expectedReaction,
    @Schema(description = "향후 방향성") String outlook,
    @Schema(description = "판단 근거", example = "반도체 수요 증가가 실적 개선으로 이어질 수 있습니다.") String reason,
    @Schema(description = "판단에 사용한 웹 검색 근거", example = "반도체 수요가 전년 대비 증가했습니다.")
        String evidenceSentence,
    @Schema(description = "자산 영향도 순위", example = "1") int rank) {

  public static PortfolioAssetImpactResponse of(
      PortfolioAnalysisResult.AssetImpactResult impact, BigDecimal weight) {
    return new PortfolioAssetImpactResponse(
        impact.assetName(),
        weight,
        impact.direction(),
        impact.impactLevel(),
        impact.issueSummary(),
        impact.expectedReaction(),
        impact.outlook(),
        impact.reason(),
        impact.evidenceSentence(),
        impact.sortOrder());
  }
}
