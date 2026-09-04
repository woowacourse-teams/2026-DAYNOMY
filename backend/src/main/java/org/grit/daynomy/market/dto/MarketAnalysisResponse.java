package org.grit.daynomy.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;

public record MarketAnalysisResponse(
    @Schema(
            description = "발생 원인과 이슈가 중요한 이유를 통합한 시장 분석 요약",
            example = "금리 인하 기대가 위험자산 선호를 높이며, 통화정책 변화는 여러 자산의 가격과 투자 심리에 영향을 줍니다.")
        String summary,
    @Schema(description = "자산 영향 목록") List<AssetImpactResponse> assets,
    @Schema(description = "기간별 시나리오 목록") List<ScenarioResponse> scenarios) {

  public static MarketAnalysisResponse from(NewsMarketAnalysis marketAnalysis) {
    return new MarketAnalysisResponse(marketAnalysis.getSummary(), List.of(), List.of());
  }
}
