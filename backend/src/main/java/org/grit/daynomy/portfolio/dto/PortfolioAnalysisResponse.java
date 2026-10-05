package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.portfolio.domain.PortfolioImpactSummary;

public record PortfolioAnalysisResponse(
    @Schema(description = "요청으로 전달받은 전체 포트폴리오 자산 수", example = "7") int totalAssetCount,
    @Schema(description = "실제 분석된 자산 수", example = "3") int analyzedAssetCount,
    @Schema(description = "보유 비중과 자산별 영향 수준을 반영한 전체 영향 방향", example = "POSITIVE")
        ImpactDirection overallDirection,
    @Schema(description = "전체 영향 점수(-100~100). 예상 수익률이나 확률이 아닌 가중 영향 지수", example = "61.00")
        BigDecimal overallScore,
    @Schema(description = "긍정 자산의 보유 비중과 영향 수준을 반영한 기여 점수(0~100)", example = "70.00")
        BigDecimal positiveImpactScore,
    @Schema(description = "부정 자산의 보유 비중과 영향 수준을 반영한 기여 점수(0~100)", example = "9.00")
        BigDecimal negativeImpactScore,
    @Schema(description = "자산별 분석을 종합한 전체 포트폴리오 예상 영향") String overallImpact,
    @Schema(description = "포트폴리오 자산별 주요 이슈 영향 분석 목록") List<PortfolioAssetImpactResponse> impacts,
    @Schema(description = "분석에 사용된 웹 검색 출처") List<PortfolioAnalysisSourceResponse> sources) {

  public PortfolioAnalysisResponse {
    impacts = List.copyOf(impacts);
    sources = List.copyOf(sources);
  }

  public static PortfolioAnalysisResponse of(
      int totalAssetCount,
      PortfolioImpactSummary impactSummary,
      String overallImpact,
      List<PortfolioAssetImpactResponse> impacts,
      List<PortfolioAnalysisSourceResponse> sources) {
    return new PortfolioAnalysisResponse(
        totalAssetCount,
        impacts.size(),
        impactSummary.direction(),
        impactSummary.score(),
        impactSummary.positiveScore(),
        impactSummary.negativeScore(),
        overallImpact,
        impacts,
        sources);
  }

  public static PortfolioAnalysisResponse empty() {
    return new PortfolioAnalysisResponse(
        0,
        0,
        ImpactDirection.NEUTRAL,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "",
        List.of(),
        List.of());
  }
}
