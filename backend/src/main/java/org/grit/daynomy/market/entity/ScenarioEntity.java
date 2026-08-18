package org.grit.daynomy.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.market.domain.scenario.TimeHorizon;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Embeddable
public class ScenarioEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "time_horizon", nullable = false)
  private TimeHorizon timeHorizon;

  @Column(name = "prediction", columnDefinition = "TEXT", nullable = false)
  private String prediction;

  @Column(name = "probability", nullable = false)
  private int probability;

  @Column(name = "reason", columnDefinition = "TEXT", nullable = false)
  private String reason;

  public ScenarioEntity(
      TimeHorizon timeHorizon, String prediction, int probability, String reason) {
    this.timeHorizon = timeHorizon;
    this.prediction = prediction;
    this.probability = probability;
    this.reason = reason;
  }
}
