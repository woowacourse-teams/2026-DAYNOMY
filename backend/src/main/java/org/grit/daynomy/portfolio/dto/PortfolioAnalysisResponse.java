package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PortfolioAnalysisResponse(
    @Schema(description = "요청으로 전달받은 전체 포트폴리오 자산 수", example = "7") int totalAssetCount,
    @Schema(description = "실제 분석된 자산 수", example = "3") int analyzedAssetCount,
    @Schema(description = "자산별 분석을 종합한 전체 포트폴리오 예상 영향") String overallImpact,
    @Schema(description = "포트폴리오 자산별 주요 이슈 영향 분석 목록") List<PortfolioAssetImpactResponse> impacts,
    @Schema(description = "분석에 사용된 웹 검색 출처") List<PortfolioAnalysisSourceResponse> sources) {

  public PortfolioAnalysisResponse {
    impacts = List.copyOf(impacts);
    sources = List.copyOf(sources);
  }

  public static PortfolioAnalysisResponse of(
      int totalAssetCount,
      String overallImpact,
      List<PortfolioAssetImpactResponse> impacts,
      List<PortfolioAnalysisSourceResponse> sources) {
    return new PortfolioAnalysisResponse(
        totalAssetCount, impacts.size(), overallImpact, impacts, sources);
  }

  public static PortfolioAnalysisResponse empty() {
    return new PortfolioAnalysisResponse(0, 0, "", List.of(), List.of());
  }
}
