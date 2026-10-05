package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;

public record PortfolioAnalysisSourceResponse(
    @Schema(description = "출처명") String title, @Schema(description = "출처 링크") String url) {

  public static PortfolioAnalysisSourceResponse from(PortfolioAnalysisResult.Source source) {
    return new PortfolioAnalysisSourceResponse(source.title(), source.url());
  }
}
