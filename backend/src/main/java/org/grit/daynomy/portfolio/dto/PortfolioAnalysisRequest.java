package org.grit.daynomy.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PortfolioAnalysisRequest(
    @NotNull(message = "포트폴리오 자산 목록은 필수입니다.")
        List<@NotNull(message = "포트폴리오 자산은 null일 수 없습니다.") @Valid PortfolioAssetRequest> assets) {

  public PortfolioAnalysisRequest {
    assets = assets == null ? null : List.copyOf(assets);
  }
}
