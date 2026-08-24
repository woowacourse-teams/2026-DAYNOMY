package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PortfolioAnalysisResponse(
    @Schema(description = "포트폴리오 자산별 뉴스 영향 분석 목록") List<PortfolioAssetImpactResponse> impacts) {

  public PortfolioAnalysisResponse {
    impacts = List.copyOf(impacts);
  }
}
