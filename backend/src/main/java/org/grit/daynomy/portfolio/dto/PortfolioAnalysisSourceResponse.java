package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;

public record PortfolioAnalysisSourceResponse(
    @Schema(description = "출처명") String title,
    @Schema(description = "출처 링크") String url,
    @Schema(description = "기사·자료 발행일. 확인되지 않으면 null") LocalDate publishedOn,
    @Schema(description = "발표·사건 발생일. 발행일과 다를 수 있으며 확인되지 않으면 null") LocalDate occurredOn,
    @Schema(description = "해당 출처에서 추출한 핵심 근거") String excerpt) {

  public PortfolioAnalysisSourceResponse(String title, String url) {
    this(title, url, null, null, "");
  }

  public static PortfolioAnalysisSourceResponse from(PortfolioAnalysisResult.Source source) {
    return new PortfolioAnalysisSourceResponse(
        source.title(), source.url(), source.publishedOn(), source.occurredOn(), source.excerpt());
  }
}
