package org.grit.daynomy.league.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.portfolio.service.PortfolioPriceHistoryService;
import org.junit.jupiter.api.Test;

class LeaguePricePreparationSchedulerTest {
  private final SharedHoldingRepository holdings = mock(SharedHoldingRepository.class);
  private final MarketTradingDayRepository days = mock(MarketTradingDayRepository.class);
  private final ExchangeRateService rates = mock(ExchangeRateService.class);
  private final PortfolioPriceHistoryService prices = mock(PortfolioPriceHistoryService.class);
  private final LeaguePricePreparationScheduler scheduler =
      new LeaguePricePreparationScheduler(holdings, days, rates, prices);

  @Test
  void preparesClosedUsDatesAndOnlyUsParticipants() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    LocalDate friday = today.with(DayOfWeek.MONDAY).minusDays(3);
    MarketTradingDay day = new MarketTradingDay(friday, true);
    day.reviseUsTrading(true);
    given(holdings.findActiveUsLeagueMemberIds()).willReturn(List.of(7L));
    given(holdings.findActiveUsLeagueAssetIds()).willReturn(List.of(9L));
    given(days.findAllByBaseDateBetweenOrderByBaseDate(friday, today)).willReturn(List.of(day));

    scheduler.prepare();

    verify(rates).ratesForDates(List.of(friday));
    verify(prices).prepare(7L, friday, today, true);
    verify(prices).prepareLeagueUsPrices(List.of(9L), friday, today);
  }

  @Test
  void skipsFeedWhenNobodyHoldsUsStocks() {
    scheduler.prepare();
    verifyNoInteractions(days, rates, prices);
  }
}
