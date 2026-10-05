package org.grit.daynomy.investmentcalendar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TradingDayReactionCalculatorTest {

  private final TradingDayReactionCalculator calculator = new TradingDayReactionCalculator();

  @Test
  void usesPreviousAndSameTradingDayWhenAnnouncementIsBeforeMarketClose() {
    Asset asset = asset(1L);
    List<StockDailyPrice> prices =
        List.of(
            price(asset, "2026-10-12", "10000"),
            price(asset, "2026-10-13", "10300"),
            price(asset, "2026-10-14", "10400"));

    AssetEventReaction result =
        calculator.calculate(1L, Instant.parse("2026-10-13T00:30:00Z"), prices).orElseThrow();

    assertThat(result.baseDate()).isEqualTo(LocalDate.parse("2026-10-12"));
    assertThat(result.reactionDate()).isEqualTo(LocalDate.parse("2026-10-13"));
    assertThat(result.returnRate()).isEqualByComparingTo("3.0000");
  }

  @Test
  void usesNextAvailableTradingDayWhenAnnouncementIsAfterMarketClose() {
    Asset asset = asset(1L);
    List<StockDailyPrice> prices =
        List.of(price(asset, "2026-10-16", "10000"), price(asset, "2026-10-19", "9800"));

    AssetEventReaction result =
        calculator.calculate(1L, Instant.parse("2026-10-16T12:00:00Z"), prices).orElseThrow();

    assertThat(result.baseDate()).isEqualTo(LocalDate.parse("2026-10-16"));
    assertThat(result.reactionDate()).isEqualTo(LocalDate.parse("2026-10-19"));
    assertThat(result.returnRate()).isEqualByComparingTo("-2.0000");
  }

  @Test
  void returnsEmptyWhenPriceIsMissingOnEitherSide() {
    Asset asset = asset(1L);

    assertThat(
            calculator.calculate(
                1L,
                Instant.parse("2026-10-13T00:30:00Z"),
                List.of(price(asset, "2026-10-13", "10000"))))
        .isEmpty();
  }

  private Asset asset(Long id) {
    Asset asset = new Asset("테스트 종목", org.grit.daynomy.asset.domain.AssetCategory.STOCK, "000000");
    ReflectionTestUtils.setField(asset, "id", id);
    return asset;
  }

  private StockDailyPrice price(Asset asset, String date, String closePrice) {
    return new StockDailyPrice(asset, LocalDate.parse(date), new BigDecimal(closePrice));
  }
}
