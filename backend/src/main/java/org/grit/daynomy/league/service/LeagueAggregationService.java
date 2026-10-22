package org.grit.daynomy.league.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.grit.daynomy.league.dto.LeagueDto.AggregationResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

@Service
public class LeagueAggregationService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private final MarketTradingDayRepository tradingDayRepository;
  private final CronExpression priceCron;
  private final CronExpression calculationCron;
  private final boolean priceEnabled;
  private final boolean calculationEnabled;

  public LeagueAggregationService(
      MarketTradingDayRepository tradingDayRepository,
      @Value("${stock.price.sync.cron}") String priceCron,
      @Value("${league.calculation.cron}") String calculationCron,
      @Value("${stock.price.sync.enabled:false}") boolean priceEnabled,
      @Value("${league.calculation.enabled:false}") boolean calculationEnabled) {
    this.tradingDayRepository = tradingDayRepository;
    this.priceCron = CronExpression.parse(priceCron);
    this.calculationCron = CronExpression.parse(calculationCron);
    this.priceEnabled = priceEnabled;
    this.calculationEnabled = calculationEnabled;
  }

  public AggregationResponse ranking(LocalDate week, LocalDate pendingCalculationDate) {
    return ranking(week, pendingCalculationDate, Instant.now());
  }

  AggregationResponse ranking(LocalDate week, LocalDate pendingCalculationDate, Instant now) {
    var collection = estimate(week, null, Map.of(), now);
    if (pendingCalculationDate == null
        || (collection != null
            && (collection.baseDate() == null
                || collection.baseDate().isBefore(pendingCalculationDate)))) return collection;
    if (!calculationEnabled) return unavailable(pendingCalculationDate, "COLLECTION_PAUSED");
    return new AggregationResponse(
        pendingCalculationDate, next(calculationCron, now), null, "CALCULATION_PENDING", false);
  }

  public AggregationResponse investor(
      LocalDate week, InvestorProfile profile, Map<LocalDate, PortfolioDailyReturn> values) {
    return estimate(week, profile, values, Instant.now());
  }

  AggregationResponse estimate(
      LocalDate week,
      InvestorProfile profile,
      Map<LocalDate, PortfolioDailyReturn> values,
      Instant now) {
    var calendar =
        tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(week, week.plusDays(6));
    if (calendar.size() != 7) return unavailable(null, "CALENDAR_PENDING");
    var days =
        calendar.stream()
            .filter(MarketTradingDay::isTrading)
            .filter(
                day ->
                    profile == null
                        || profile.getParticipations().stream()
                            .anyMatch(period -> period.contains(day.closesAt())))
            .toList();
    if (days.isEmpty()) return null;
    var pending =
        days.stream()
            .filter(
                day ->
                    day.getCollectedAt() == null
                        || (profile != null
                            && (!values.containsKey(day.getBaseDate())
                                || !values.get(day.getBaseDate()).isEligible())))
            .findFirst()
            .orElse(null);
    if (pending == null) return null;
    var value = values.get(pending.getBaseDate());
    if (value != null
        && !value.isEligible()
        && !"PRICE_COLLECTION_PENDING".equals(value.getIneligibleReason())) {
      return unavailable(pending.getBaseDate(), value.getIneligibleReason());
    }
    if (!calculationEnabled || (pending.getCollectedAt() == null && !priceEnabled)) {
      return unavailable(pending.getBaseDate(), "COLLECTION_PAUSED");
    }
    Instant expected;
    boolean delayed = false;
    String reason;
    if (pending.getCollectedAt() != null) {
      expected = next(calculationCron, now);
      reason = "CALCULATION_PENDING";
    } else {
      Instant scheduled = collectionAt(pending.getBaseDate());
      if (scheduled == null) return unavailable(pending.getBaseDate(), "CALENDAR_PENDING");
      delayed = now.isAfter(scheduled);
      expected = delayed ? next(priceCron, now) : scheduled;
      reason =
          delayed
              ? "PRICE_COLLECTION_DELAYED"
              : pending.closesAt().isAfter(now)
                  ? "MARKET_CLOSE_PENDING"
                  : "PRICE_COLLECTION_PENDING";
    }
    boolean awaitingFirstReturn =
        profile != null
            && ((profile.getTrackingStartedOn() == null
                    && profile.getParticipations().stream()
                        .noneMatch(period -> period.getPerformanceStartedAt() != null))
                || values.values().stream().anyMatch(PortfolioDailyReturn::isTrackingBaseline))
            && values.values().stream()
                .noneMatch(day -> day.isEligible() && !day.isTrackingBaseline());
    Instant firstReturn = null;
    if (awaitingFirstReturn && priceEnabled) {
      boolean hasBaseline = values.values().stream().anyMatch(PortfolioDailyReturn::isEligible);
      LocalDate returnDate =
          hasBaseline
              ? pending.getBaseDate()
              : tradingDayRepository
                  .findFirstByTradingTrueAndBaseDateGreaterThanOrderByBaseDateAsc(
                      pending.getBaseDate())
                  .map(MarketTradingDay::getBaseDate)
                  .orElse(null);
      firstReturn = returnDate == null ? null : collectionAt(returnDate);
      if (firstReturn != null && now.isAfter(firstReturn)) firstReturn = next(priceCron, now);
    }
    return new AggregationResponse(pending.getBaseDate(), expected, firstReturn, reason, delayed);
  }

  private AggregationResponse unavailable(LocalDate date, String reason) {
    return new AggregationResponse(date, null, null, reason, false);
  }

  private Instant collectionAt(LocalDate date) {
    return tradingDayRepository
        .findFirstByTradingTrueAndBaseDateGreaterThanOrderByBaseDateAsc(date)
        .map(
            day ->
                next(
                    priceCron,
                    day.getBaseDate().atTime(14, 0).atZone(SEOUL).toInstant().minusNanos(1)))
        .orElse(null);
  }

  private Instant next(CronExpression cron, Instant after) {
    var next = cron.next(after.atZone(SEOUL));
    return next == null ? null : next.toInstant();
  }
}
