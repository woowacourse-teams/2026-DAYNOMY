package org.grit.daynomy.league.service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.portfolio.service.PortfolioPriceHistoryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "league.calculation.enabled", havingValue = "true")
public class LeaguePricePreparationScheduler {
  private final SharedHoldingRepository holdings;
  private final MarketTradingDayRepository tradingDays;
  private final ExchangeRateService exchangeRates;
  private final PortfolioPriceHistoryService prices;

  @Scheduled(cron = "${league.price-preparation.cron:0 0 7,16 * * MON-SAT}", zone = "Asia/Seoul")
  public void prepare() {
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    LocalDate from = today.with(DayOfWeek.MONDAY).minusDays(3);
    var members = holdings.findActiveUsLeagueMemberIds();
    if (members.isEmpty()) return;
    try {
      exchangeRates.ratesForDates(
          tradingDays.findAllByBaseDateBetweenOrderByBaseDate(from, today).stream()
              .filter(day -> Boolean.TRUE.equals(day.getUsTrading()))
              .filter(day -> !day.usClosesAt().isAfter(Instant.now()))
              .map(day -> day.getBaseDate())
              .toList());
    } catch (RuntimeException exception) {
      log.warn(
          "League exchange rate preparation failed: errorType={}",
          exception.getClass().getSimpleName());
    }
    for (Long memberId : members) {
      try {
        prices.prepare(memberId, from, today, true);
      } catch (RuntimeException exception) {
        log.warn(
            "League price preparation failed: memberId={}, errorType={}",
            memberId,
            exception.getClass().getSimpleName());
      }
    }
    prices.prepareLeagueUsPrices(holdings.findActiveUsLeagueAssetIds(), from, today);
  }
}
