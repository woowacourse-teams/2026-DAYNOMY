package org.grit.daynomy.market.domain.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.market.domain.asset.Assets;
import org.grit.daynomy.market.domain.scenario.Scenarios;

@Getter
@RequiredArgsConstructor
public class MarketAnalysis {

  private final String cause;
  private final Assets assets;
  private final Scenarios scenarios;
}
