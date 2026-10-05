package org.grit.daynomy.investmentcalendar.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.StockDailyPrice;

public class TradingDayReactionCalculator {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final LocalTime KOREA_MARKET_CLOSE = LocalTime.of(15, 30);

  public Optional<AssetEventReaction> calculate(
      Long assetId, Instant announcedAt, List<StockDailyPrice> prices) {
    if (prices.size() < 2) {
      return Optional.empty();
    }
    List<StockDailyPrice> sorted =
        prices.stream().sorted(Comparator.comparing(StockDailyPrice::getBaseDate)).toList();
    LocalDate announcedDate = announcedAt.atZone(SEOUL).toLocalDate();
    LocalTime announcedTime = announcedAt.atZone(SEOUL).toLocalTime();

    StockDailyPrice basePrice;
    StockDailyPrice reactionPrice;
    if (!announcedTime.isAfter(KOREA_MARKET_CLOSE)) {
      reactionPrice = firstOnOrAfter(sorted, announcedDate).orElse(null);
      basePrice =
          reactionPrice == null
              ? null
              : lastBefore(sorted, reactionPrice.getBaseDate()).orElse(null);
    } else {
      basePrice = lastOnOrBefore(sorted, announcedDate).orElse(null);
      reactionPrice =
          basePrice == null ? null : firstAfter(sorted, basePrice.getBaseDate()).orElse(null);
    }
    if (basePrice == null || reactionPrice == null) {
      return Optional.empty();
    }

    BigDecimal returnRate =
        reactionPrice
            .getClosePrice()
            .subtract(basePrice.getClosePrice())
            .multiply(BigDecimal.valueOf(100))
            .divide(basePrice.getClosePrice(), 4, RoundingMode.HALF_UP);
    return Optional.of(
        new AssetEventReaction(
            assetId, basePrice.getBaseDate(), reactionPrice.getBaseDate(), returnRate));
  }

  private Optional<StockDailyPrice> firstOnOrAfter(List<StockDailyPrice> prices, LocalDate date) {
    return prices.stream().filter(price -> !price.getBaseDate().isBefore(date)).findFirst();
  }

  private Optional<StockDailyPrice> firstAfter(List<StockDailyPrice> prices, LocalDate date) {
    return prices.stream().filter(price -> price.getBaseDate().isAfter(date)).findFirst();
  }

  private Optional<StockDailyPrice> lastOnOrBefore(List<StockDailyPrice> prices, LocalDate date) {
    return prices.stream()
        .filter(price -> !price.getBaseDate().isAfter(date))
        .reduce((first, second) -> second);
  }

  private Optional<StockDailyPrice> lastBefore(List<StockDailyPrice> prices, LocalDate date) {
    return prices.stream()
        .filter(price -> price.getBaseDate().isBefore(date))
        .reduce((first, second) -> second);
  }
}
