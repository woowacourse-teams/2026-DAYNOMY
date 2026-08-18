package org.grit.daynomy.market.domain.asset;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AssetImpact {

  private final Asset asset;
  private final ImpactDirection direction;
  private final ImpactLevel impactLevel;
  private final String reason;
}
