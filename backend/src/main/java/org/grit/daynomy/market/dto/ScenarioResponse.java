package org.grit.daynomy.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.market.domain.scenario.TimeHorizon;
import org.grit.daynomy.market.domain.scenario.Scenario;

public record ScenarioResponse(
    @Schema(description = "시나리오 기간", example = "SHORT_TERM") TimeHorizon timeHorizon,
    @Schema(description = "예측 내용", example = "단기적으로 주식 선호가 개선될 수 있습니다.") String prediction,
    @Schema(description = "발생 가능성", example = "70") int probability,
    @Schema(description = "판단 근거", example = "금리 인하 기대가 투자 심리를 자극하기 때문입니다.") String reason) {

  public static ScenarioResponse from(Scenario scenario) {
    return new ScenarioResponse(
        scenario.getTimeHorizon(),
        scenario.getPrediction(),
        scenario.getProbability(),
        scenario.getReason());
  }
}
