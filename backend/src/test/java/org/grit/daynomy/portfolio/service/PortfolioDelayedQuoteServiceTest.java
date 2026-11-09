package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.exchangerate.repository.UsdKrwRateRepository;
import org.grit.daynomy.external.eodhd.EodhdClient;
import org.grit.daynomy.external.krx.KrxCalendarClient;
import org.grit.daynomy.portfolio.repository.PortfolioDelayedQuoteRepository;
import org.grit.daynomy.portfolio.repository.PortfolioDelayedQuoteRepository.CachedQuote;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PortfolioDelayedQuoteServiceTest {

  private final AssetRepository assets = mock(AssetRepository.class);
  private final PortfolioDelayedQuoteRepository quotes =
      mock(PortfolioDelayedQuoteRepository.class);
  private final EodhdClient client = mock(EodhdClient.class);

  @Test
  void retainsOldSameDayQuoteAndMarksItPendingButRejectsFutureQuotes() {
    Instant now = Instant.parse("2026-11-09T02:00:00Z");
    var samsung = domesticStock(1L, "005930");
    var hynix = domesticStock(2L, "000660");
    var naver = domesticStock(3L, "035420");
    when(assets.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(samsung, hynix, naver));
    var symbols =
        org.mockito.ArgumentMatchers.<List<String>>argThat(
            list ->
                list.size() == 3
                    && list.containsAll(List.of("005930.KO", "000660.KO", "035420.KO")));
    when(quotes.request(symbols, org.mockito.ArgumentMatchers.eq(now)))
        .thenReturn(
            List.of(
                new CachedQuote("005930.KO", BigDecimal.TEN, now.minusSeconds(2400)),
                new CachedQuote("000660.KO", BigDecimal.TEN, now.minusSeconds(2401)),
                new CachedQuote("035420.KO", BigDecimal.TEN, now.plusSeconds(1))));

    var quotesAtForty = service(now, Duration.ofMinutes(40)).requestQuotes(List.of(1L, 2L, 3L));
    assertThat(quotesAtForty).containsOnlyKeys(1L, 2L);
    assertThat(quotesAtForty.get(1L).refreshPending()).isFalse();
    assertThat(quotesAtForty.get(2L).refreshPending()).isTrue();
    var quotesAtThirty = service(now, Duration.ofMinutes(30)).requestQuotes(List.of(1L, 2L, 3L));
    assertThat(quotesAtThirty.get(1L).refreshPending()).isTrue();
    assertThat(quotesAtThirty.get(1L).price()).isEqualByComparingTo(BigDecimal.TEN);
  }

  @Test
  void usMarketDoesNotPollAfterAnEarlyCloseGracePeriod() {
    var date = LocalDate.of(2026, 11, 27);
    assertThat(UsQuoteCalendar.closeOn(date)).contains(LocalTime.of(13, 0));
    assertThat(UsQuoteCalendar.closeOn(date.minusDays(1))).isEmpty();

    service(Instant.parse("2026-11-27T18:25:00Z"), Duration.ofMinutes(40)).refresh();

    verifyNoInteractions(quotes, client);
  }

  @Test
  void koreanSessionProtectsTheUsBudgetAndItsOwnRemainingSlots() {
    Instant now = Instant.parse("2026-11-09T00:00:00Z");

    service(now, Duration.ofMinutes(30)).refresh();

    verify(quotes).reserve("KR", now, now, 600, 50_000, 49_600);
  }

  @Test
  void koreanSessionCanUseTheUsBudgetOnAUsHoliday() {
    Instant now = Instant.parse("2026-11-26T00:00:00Z");

    service(now, Duration.ofMinutes(30)).refresh();

    verify(quotes).reserve("KR", now, now, 600, 50_000, 24_600);
  }

  @Test
  void providerDailyLimitStopsFurtherRequests() {
    Instant now = Instant.parse("2026-11-09T15:00:00Z");
    when(quotes.reserve("US", now, now, 600, 50_000, 22_800)).thenReturn(List.of("SPY.US"));
    when(client.getDelayedQuotes(List.of("SPY.US")))
        .thenReturn(new EodhdClient.DelayedQuotes(List.of(), 402));

    service(now, Duration.ofMinutes(40)).refresh();

    verify(quotes).exhaust(now);
  }

  private Asset domesticStock(long id, String symbol) {
    var asset =
        Asset.listedStock(symbol, symbol, StockMarket.KOSPI, null, LocalDate.of(2026, 11, 9));
    ReflectionTestUtils.setField(asset, "id", id);
    return asset;
  }

  private PortfolioDelayedQuoteService service(Instant now, Duration maxAge) {
    var calendar = mock(KrxCalendarClient.class);
    when(calendar.holidays(2026)).thenReturn(java.util.Set.of());
    return new PortfolioDelayedQuoteService(
        assets,
        quotes,
        mock(UsdKrwRateRepository.class),
        mock(MarketTradingDayRepository.class),
        calendar,
        client,
        Clock.fixed(now, ZoneOffset.UTC),
        maxAge);
  }
}
