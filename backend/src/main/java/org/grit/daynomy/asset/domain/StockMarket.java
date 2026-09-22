package org.grit.daynomy.asset.domain;

import java.util.Arrays;
import java.util.Optional;

public enum StockMarket {
  KOSPI,
  KOSDAQ;

  public static Optional<StockMarket> from(String value) {
    return Arrays.stream(values()).filter(market -> market.name().equals(value)).findFirst();
  }
}
