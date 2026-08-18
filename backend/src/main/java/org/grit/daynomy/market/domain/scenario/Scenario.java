package org.grit.daynomy.market.domain.scenario;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Scenario {

  private final TimeHorizon timeHorizon;
  private final String prediction;
  private final int probability;
  private final String reason;
}
