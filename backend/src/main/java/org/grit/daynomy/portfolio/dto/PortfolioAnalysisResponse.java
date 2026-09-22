package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PortfolioAnalysisResponse(
    @Schema(description = "요청으로 전달받은 전체 포트폴리오 자산 수", example = "7") int totalAssetCount,
    @Schema(description = "실제 분석된 자산 수", example = "3") int analyzedAssetCount,
    @Schema(description = "포트폴리오 자산별 뉴스 영향 분석 목록") List<PortfolioAssetImpactResponse> impacts) {

  public PortfolioAnalysisResponse {
    impacts = List.copyOf(impacts);
  }

  public static PortfolioAnalysisResponse of(
      int totalAssetCount, List<PortfolioAssetImpactResponse> impacts) {
    return new PortfolioAnalysisResponse(totalAssetCount, impacts.size(), impacts);
  }

  public static PortfolioAnalysisResponse empty() {
    return new PortfolioAnalysisResponse(0, 0, List.of());
  }
}
