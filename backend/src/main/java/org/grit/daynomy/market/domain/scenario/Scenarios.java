package org.grit.daynomy.market.domain.scenario;

import java.util.List;
import lombok.Getter;

@Getter
public class Scenarios {

  private final List<Scenario> values;

  public Scenarios(List<Scenario> values) {
    this.values = List.copyOf(values);
  }
}
