package org.grit.daynomy.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;

public record MarketAnalysisResponse(
    @Schema(description = "기사에 근거한 위험과 불확실성") String risk,
    @Schema(description = "확인해야 할 지표나 후속 조치와 그 이유") String watchPoint) {

  public static MarketAnalysisResponse from(NewsMarketAnalysis marketAnalysis) {
    return new MarketAnalysisResponse(marketAnalysis.getRisk(), marketAnalysis.getWatchPoint());
  }
}
