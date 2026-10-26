package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.junit.jupiter.api.Test;

class PortfolioExchangeRateServiceTest {
  @Test
  void loadsAffectedDatesAndOnlyOnePreviousDate() {
    var prices = mock(StockDailyPriceRepository.class);
    var histories = mock(PortfolioHoldingHistoryRepository.class);
    var rates = mock(ExchangeRateService.class);
    var service = new PortfolioExchangeRateService(prices, histories, rates);
    LocalDate first = LocalDate.of(2020, 1, 1);
    when(histories.findAssetIdsByMemberId(1L)).thenReturn(List.of(2L));
    when(prices.findBaseDatesByAssetIdsAndMarkets(List.of(2L), StockMarket.usMarkets()))
        .thenReturn(List.of(first, first.plusDays(1), first.plusDays(2), first.plusDays(3)));
    var expectedDates = List.of(first.plusDays(1), first.plusDays(2), first.plusDays(3));
    var expectedRates = Map.of(first.plusDays(2), new BigDecimal("1300"));
    when(rates.ratesForDates(expectedDates)).thenReturn(expectedRates);
    assertThat(service.forMember(1L, 2L, first.plusDays(2))).isEqualTo(expectedRates);
    verify(rates).ratesForDates(expectedDates);
  }

  @Test
  void todayTradeDoesNotFetchHistoricalRates() {
    var prices = mock(StockDailyPriceRepository.class);
    var histories = mock(PortfolioHoldingHistoryRepository.class);
    var rates = mock(ExchangeRateService.class);
    var service = new PortfolioExchangeRateService(prices, histories, rates);
    assertThat(service.forMember(1L, 2L, LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))))
        .isEmpty();
    verifyNoInteractions(prices, rates);
  }

  @Test
  void snapshotUsesOnlyCurrentAndPreviousHistoricalRateDates() {
    var prices = mock(StockDailyPriceRepository.class);
    var histories = mock(PortfolioHoldingHistoryRepository.class);
    var rates = mock(ExchangeRateService.class);
    var service = new PortfolioExchangeRateService(prices, histories, rates);
    LocalDate previous = LocalDate.of(2026, 9, 28);
    LocalDate current = LocalDate.of(2026, 9, 30);
    var expectedDates = List.of(previous, current);
    var expectedRates = Map.of(previous, new BigDecimal("1320"), current, new BigDecimal("1300"));
    when(histories.findAssetIdsByPortfolioId(10L)).thenReturn(List.of(2L));
    when(prices.findBaseDatesByAssetIdsAndMarkets(List.of(2L), StockMarket.usMarkets()))
        .thenReturn(List.of(previous.minusDays(1), previous, current));
    when(rates.ratesForDates(expectedDates)).thenReturn(expectedRates);

    assertThat(service.forSnapshot(10L, current, previous)).isEqualTo(expectedRates);
    verify(rates).ratesForDates(expectedDates);
  }
}
