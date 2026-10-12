package org.grit.daynomy.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;

public record MarketAnalysisResponse(
    @Schema(description = "기존 클라이언트 호환을 위한 시장 분석 요약") String summary,
    @Schema(description = "뉴스 본문에서 직접 확인되는 사실") String observedFact,
    @Schema(description = "확인된 사실을 바탕으로 한 제한적인 시장 해석") String interpretation,
    @Schema(description = "해석이 달라질 수 있는 위험 요인") String risk,
    @Schema(description = "이후 확인해야 할 지표나 사건") String watchPoint,
    @Schema(description = "분석에 포함되지 않은 정보와 해석 범위") String limitation) {

  public static MarketAnalysisResponse from(NewsMarketAnalysis marketAnalysis) {
    return new MarketAnalysisResponse(
        marketAnalysis.getObservedFact() + " " + marketAnalysis.getInterpretation(),
        marketAnalysis.getObservedFact(),
        marketAnalysis.getInterpretation(),
        marketAnalysis.getRisk(),
        marketAnalysis.getWatchPoint(),
        marketAnalysis.getLimitation());
  }
}
