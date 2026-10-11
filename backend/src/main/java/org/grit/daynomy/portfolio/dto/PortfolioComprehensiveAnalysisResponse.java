package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PortfolioComprehensiveAnalysisResponse(
    @Schema(description = "확인된 사실을 바탕으로 한 종합 판단") String summary,
    @Schema(description = "자산의 사업과 이슈가 영향으로 이어지는 과정") List<String> reasoningSteps,
    @Schema(description = "보유 비중을 반영한 포트폴리오 영향") String portfolioImpact,
    @Schema(description = "종합 판단과 다른 결과가 발생할 수 있는 시나리오") String counterScenario,
    @Schema(description = "향후 확인할 지표 또는 이슈") String nextIndicator) {

  public PortfolioComprehensiveAnalysisResponse {
    reasoningSteps = List.copyOf(reasoningSteps);
  }
}
