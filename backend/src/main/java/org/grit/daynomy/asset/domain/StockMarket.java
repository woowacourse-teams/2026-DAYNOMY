package org.grit.daynomy.asset.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum StockMarket {
  KOSPI,
  KOSDAQ,
  NASDAQ,
  NYSE,
  NYSE_ARCA,
  NYSE_AMERICAN,
  BATS;

  public static Set<StockMarket> domesticMarkets() {
    return Set.of(KOSPI, KOSDAQ);
  }

  public static Set<StockMarket> usMarkets() {
    return Set.of(NASDAQ, NYSE, NYSE_ARCA, NYSE_AMERICAN, BATS);
  }

  public boolean isUs() {
    return usMarkets().contains(this);
  }

  public String currency() {
    return isUs() ? "USD" : "KRW";
  }

  public static Optional<StockMarket> fromEodhd(String exchange) {
    if (exchange == null) return Optional.empty();
    return switch (exchange.trim().toUpperCase(Locale.ROOT)) {
      case "NASDAQ" -> Optional.of(NASDAQ);
      case "NYSE" -> Optional.of(NYSE);
      case "NYSE ARCA", "NYSEARCA", "ARCA" -> Optional.of(NYSE_ARCA);
      case "NYSE MKT", "NYSE AMERICAN", "AMEX" -> Optional.of(NYSE_AMERICAN);
      case "BATS", "CBOE" -> Optional.of(BATS);
      default -> Optional.empty();
    };
  }

  public static Optional<StockMarket> from(String value) {
    return Arrays.stream(values()).filter(market -> market.name().equals(value)).findFirst();
  }
}
