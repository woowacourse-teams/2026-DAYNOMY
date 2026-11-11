package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.asset.repository.*;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisPriceContext.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

class PortfolioAnalysisPriceServiceTest {
  private final AssetRepository assets = mock(AssetRepository.class);
  private final StockDailyPriceRepository prices = mock(StockDailyPriceRepository.class);

  @SuppressWarnings("unchecked")
  private final ObjectProvider<PortfolioDelayedQuoteService> provider = mock(ObjectProvider.class);

  private final PortfolioDelayedQuoteService quotes = mock(PortfolioDelayedQuoteService.class);
  private final Instant now = Instant.parse("2026-11-11T05:30:00Z");
  private final LocalDate today = LocalDate.of(2026, 11, 11);
  private final PortfolioAnalysisPriceService service =
      new PortfolioAnalysisPriceService(assets, prices, provider, Clock.fixed(now, ZoneOffset.UTC));
  private Asset apple;

  @BeforeEach
  void setUp() {
    apple = asset("Apple", "AAPL.US", StockMarket.NASDAQ, 1L);
    apple.synchronizeKoreanName("애플");
  }

  @Test
  void reusesExactSearchAndBatchedHistoryWithoutPersonalHoldings() {
    when(assets.searchListedSecurities(anyString(), anySet(), anySet(), any()))
        .thenReturn(List.of(apple));
    when(prices.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
            anyList(), any(), any()))
        .thenReturn(
            List.of(
                new StockDailyPrice(apple, today.minusDays(20), new BigDecimal("200")),
                new StockDailyPrice(apple, today.minusDays(1), new BigDecimal("180"))));

    var result = service.lookup(List.of("애플"));

    assertThat(result.get("애플").changeRate()).isEqualByComparingTo("-10");
    assertThat(result.get("애플").currency()).isEqualTo("USD");
    verify(prices)
        .findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
            eq(List.of(1L)), any(), any());
  }

  @Test
  void unknownAndAmbiguousNamesDoNotGuessTheSecurity() {
    when(assets.searchListedSecurities(eq("unknown"), anySet(), anySet(), any()))
        .thenReturn(List.of());
    when(assets.searchListedSecurities(eq("Apple"), anySet(), anySet(), any()))
        .thenReturn(List.of(apple, asset("Apple", "123456", StockMarket.KOSPI, 2L)));
    var result = service.lookup(List.of("unknown", "Apple"));
    assertThat(result.get("unknown").status()).isEqualTo(Status.UNAVAILABLE);
    assertThat(result.get("Apple").status()).isEqualTo(Status.AMBIGUOUS_ASSET);
    verifyNoInteractions(prices, provider);
  }

  @Test
  void availableDelayedQuoteUsesItsNativePriceAndTimestamp() {
    when(assets.searchListedSecurities(anyString(), anySet(), anySet(), any()))
        .thenReturn(List.of(apple));
    when(prices.findLatestTwoByAssetIds(anyList()))
        .thenReturn(List.of(new StockDailyPrice(apple, today.minusDays(1), new BigDecimal("200"))));
    when(provider.getIfAvailable()).thenReturn(quotes);
    when(quotes.requestQuotes(List.of(1L)))
        .thenReturn(
            Map.of(
                1L,
                new PortfolioDelayedQuoteService.ValuationQuote(
                    apple,
                    today,
                    now.minusSeconds(300),
                    new BigDecimal("210"),
                    new BigDecimal("300000"),
                    false)));
    var result = service.lookup(List.of("Apple")).get("Apple");
    assertThat(result.priceType()).isEqualTo("DELAYED");
    assertThat(result.price()).isEqualByComparingTo("210");
    assertThat(result.asOf()).isEqualTo(now.minusSeconds(300));
  }

  @Test
  void expiredDelayedQuoteFallsBackToConfirmedClose() {
    when(assets.searchListedSecurities(anyString(), anySet(), anySet(), any()))
        .thenReturn(List.of(apple));
    when(prices.findLatestTwoByAssetIds(anyList()))
        .thenReturn(List.of(new StockDailyPrice(apple, today.minusDays(1), new BigDecimal("200"))));
    when(provider.getIfAvailable()).thenReturn(quotes);
    when(quotes.requestQuotes(anyList()))
        .thenReturn(
            Map.of(
                1L,
                new PortfolioDelayedQuoteService.ValuationQuote(
                    apple,
                    today,
                    now.minusSeconds(1900),
                    new BigDecimal("210"),
                    new BigDecimal("300000"),
                    true)));
    assertThat(service.lookup(List.of("Apple")).get("Apple").priceType()).isEqualTo("CLOSE");
  }

  @Test
  void queryFailureIsDifferentFromMissingData() {
    when(assets.searchListedSecurities(anyString(), anySet(), anySet(), any()))
        .thenThrow(new DataAccessResourceFailureException("unavailable"));
    assertThat(service.lookup(List.of("Apple")).get("Apple").status())
        .isEqualTo(Status.LOOKUP_FAILED);
    verifyNoInteractions(prices);
  }

  private Asset asset(String name, String code, StockMarket market, long id) {
    var asset = Asset.listedStock(name, code, market, null, today);
    ReflectionTestUtils.setField(asset, "id", id);
    return asset;
  }
}
