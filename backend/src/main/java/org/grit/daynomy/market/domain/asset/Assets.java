package org.grit.daynomy.market.domain.asset;

import java.util.List;
import lombok.Getter;

@Getter
public class Assets {

  private final List<AssetImpact> values;

  public Assets(List<AssetImpact> values) {
    this.values = List.copyOf(values);
  }
}
