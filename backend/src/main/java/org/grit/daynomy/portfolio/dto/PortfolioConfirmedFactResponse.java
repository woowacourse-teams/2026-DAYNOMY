package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PortfolioConfirmedFactResponse(
    @Schema(description = "공시·기업 발표·주요 언론에서 확인한 사실") String summary,
    @Schema(description = "사실을 직접 뒷받침하는 근거 문장") String evidence,
    @Schema(description = "사실 확인에 사용한 출처") List<PortfolioAnalysisSourceResponse> sources) {

  public PortfolioConfirmedFactResponse {
    sources = List.copyOf(sources);
  }
}
