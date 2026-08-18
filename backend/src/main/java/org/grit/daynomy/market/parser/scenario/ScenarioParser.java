package org.grit.daynomy.market.parser.scenario;

import java.util.List;
import org.grit.daynomy.market.domain.scenario.Scenario;
import org.grit.daynomy.market.domain.scenario.Scenarios;
import org.grit.daynomy.market.entity.ScenarioEntity;

public final class ScenarioParser {

  private ScenarioParser() {}

  public static Scenarios toDomain(List<ScenarioEntity> entities) {
    return new Scenarios(entities.stream().map(ScenarioParser::toDomain).toList());
  }

  public static Scenario toDomain(ScenarioEntity entity) {
    return new Scenario(
        entity.getTimeHorizon(),
        entity.getPrediction(),
        entity.getProbability(),
        entity.getReason());
  }

  public static List<ScenarioEntity> toEntity(Scenarios scenarios) {
    return scenarios.getValues().stream().map(ScenarioParser::toEntity).toList();
  }

  public static ScenarioEntity toEntity(Scenario scenario) {
    return new ScenarioEntity(
        scenario.getTimeHorizon(),
        scenario.getPrediction(),
        scenario.getProbability(),
        scenario.getReason());
  }
}
