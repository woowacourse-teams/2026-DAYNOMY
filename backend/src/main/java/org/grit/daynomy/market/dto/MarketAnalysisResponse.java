package org.grit.daynomy.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;

public record MarketAnalysisResponse(
    @Schema(description = "발생 원인", example = "금리 인하 기대가 위험자산 선호를 높입니다.") String cause,
    @Schema(description = "자산 영향 목록") List<AssetImpactResponse> assets,
    @Schema(description = "기간별 시나리오 목록") List<ScenarioResponse> scenarios) {

  public static MarketAnalysisResponse from(NewsMarketAnalysis marketAnalysis) {
    return new MarketAnalysisResponse(
        marketAnalysis.getCause(),
        marketAnalysis.getAssets().stream().map(AssetImpactResponse::from).toList(),
        marketAnalysis.getScenarios().stream().map(ScenarioResponse::from).toList());
  }
}
