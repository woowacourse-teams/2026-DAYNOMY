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
  private final CronExpression snapshotCron;
  private final CronExpression usPriceCron;
  private final boolean priceEnabled;
  private final boolean calculationEnabled;
  private final boolean snapshotEnabled;

  public LeagueAggregationService(
      MarketTradingDayRepository tradingDayRepository,
      @Value("${stock.price.sync.cron}") String priceCron,
      @Value("${league.calculation.cron}") String calculationCron,
      @Value("${portfolio.snapshot.cron}") String snapshotCron,
      @Value("${league.price-preparation.cron:0 0 7,16 * * MON-SAT}") String usPriceCron,
      @Value("${stock.price.sync.enabled:false}") boolean priceEnabled,
      @Value("${league.calculation.enabled:false}") boolean calculationEnabled,
      @Value("${portfolio.snapshot.enabled:false}") boolean snapshotEnabled) {
    this.tradingDayRepository = tradingDayRepository;
    this.priceCron = CronExpression.parse(priceCron);
    this.calculationCron = CronExpression.parse(calculationCron);
    this.snapshotCron = CronExpression.parse(snapshotCron);
    this.usPriceCron = CronExpression.parse(usPriceCron);
    this.priceEnabled = priceEnabled;
    this.calculationEnabled = calculationEnabled;
    this.snapshotEnabled = snapshotEnabled;
  }

  public AggregationResponse ranking(LocalDate week, LocalDate pendingCalculationDate) {
    return ranking(week, pendingCalculationDate, false);
  }

  public AggregationResponse ranking(
      LocalDate week, LocalDate pendingCalculationDate, boolean usRelevant) {
    return ranking(week, pendingCalculationDate, usRelevant, true);
  }

  public AggregationResponse ranking(
      LocalDate week,
      LocalDate pendingCalculationDate,
      boolean usRelevant,
      boolean domesticRelevant) {
    return ranking(week, pendingCalculationDate, Instant.now(), usRelevant, domesticRelevant);
  }

  AggregationResponse ranking(LocalDate week, LocalDate pendingCalculationDate, Instant now) {
    return ranking(week, pendingCalculationDate, now, false, true);
  }

  AggregationResponse ranking(
      LocalDate week,
      LocalDate pendingCalculationDate,
      Instant now,
      boolean usRelevant,
      boolean domesticRelevant) {
    var collection = estimate(week, null, Map.of(), now, usRelevant, domesticRelevant);
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
    return estimate(week, profile, values, Instant.now(), false, true);
  }

  public AggregationResponse investor(
      LocalDate week,
      InvestorProfile profile,
      Map<LocalDate, PortfolioDailyReturn> values,
      boolean usRelevant) {
    return investor(week, profile, values, usRelevant, true);
  }

  public AggregationResponse investor(
      LocalDate week,
      InvestorProfile profile,
      Map<LocalDate, PortfolioDailyReturn> values,
      boolean usRelevant,
      boolean domesticRelevant) {
    return estimate(week, profile, values, Instant.now(), usRelevant, domesticRelevant);
  }

  AggregationResponse estimate(
      LocalDate week,
      InvestorProfile profile,
      Map<LocalDate, PortfolioDailyReturn> values,
      Instant now) {
    return estimate(week, profile, values, now, false, true);
  }

  AggregationResponse estimate(
      LocalDate week,
      InvestorProfile profile,
      Map<LocalDate, PortfolioDailyReturn> values,
      Instant now,
      boolean usRelevant,
      boolean domesticRelevant) {
    var calendar =
        tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(week, week.plusDays(6));
    if (calendar.size() != 7
        || (usRelevant && calendar.stream().anyMatch(day -> day.getUsTrading() == null)))
      return unavailable(null, "CALENDAR_PENDING");
    var days =
        calendar.stream()
            .filter(
                day ->
                    (domesticRelevant && day.isTrading())
                        || (usRelevant && Boolean.TRUE.equals(day.getUsTrading())))
            .filter(
                day ->
                    profile == null
                        || profile.getParticipations().stream()
                            .anyMatch(
                                period ->
                                    period.contains(
                                        usRelevant ? day.leagueClosesAt() : day.closesAt())))
            .toList();
    if (days.isEmpty()) return null;
    var pending =
        days.stream()
            .filter(
                day ->
                    (domesticRelevant && day.isTrading() && day.getCollectedAt() == null)
                        || (usRelevant
                            && Boolean.TRUE.equals(day.getUsTrading())
                            && day.getUsCollectedAt() == null)
                        || (profile != null
                            && (!values.containsKey(day.getBaseDate())
                                || !values.get(day.getBaseDate()).isEligible())))
            .findFirst()
            .orElse(null);
    if (pending == null) return null;
    var value = values.get(pending.getBaseDate());
    if (value != null && "SNAPSHOT_PENDING".equals(value.getIneligibleReason())) {
      if (!calculationEnabled || (!snapshotEnabled && !priceEnabled)) {
        return unavailable(pending.getBaseDate(), "COLLECTION_PAUSED");
      }
      Instant refresh = snapshotEnabled ? next(snapshotCron, now) : null;
      Instant expected = refresh == null ? null : next(calculationCron, refresh);
      return new AggregationResponse(
          pending.getBaseDate(), expected, null, "SNAPSHOT_PENDING", false);
    }
    boolean exchangeRatePending =
        value != null && "EXCHANGE_RATE_PENDING".equals(value.getIneligibleReason());
    if (value != null
        && !value.isEligible()
        && !"PRICE_COLLECTION_PENDING".equals(value.getIneligibleReason())
        && !exchangeRatePending) {
      return unavailable(pending.getBaseDate(), value.getIneligibleReason());
    }
    boolean domesticReady =
        !domesticRelevant || !pending.isTrading() || pending.getCollectedAt() != null;
    boolean usReady =
        !usRelevant
            || !Boolean.TRUE.equals(pending.getUsTrading())
            || pending.getUsCollectedAt() != null;
    if (!calculationEnabled || (!domesticReady && !priceEnabled)) {
      return unavailable(pending.getBaseDate(), "COLLECTION_PAUSED");
    }
    Instant expected = null;
    boolean delayed = false;
    if (!domesticReady) {
      Instant scheduled = collectionAt(pending.getBaseDate());
      if (scheduled == null) return unavailable(pending.getBaseDate(), "CALENDAR_PENDING");
      delayed = now.isAfter(scheduled);
      expected = delayed ? next(priceCron, now) : scheduled;
    }
    if (!usReady || exchangeRatePending) {
      Instant scheduled = usCollectionAt(pending.getBaseDate());
      if (scheduled == null) return unavailable(pending.getBaseDate(), "CALENDAR_PENDING");
      Instant firstCalculation = next(calculationCron, scheduled);
      boolean usDelayed = firstCalculation == null || now.isAfter(firstCalculation);
      delayed |= usDelayed;
      Instant collection = usDelayed ? next(usPriceCron, now) : scheduled;
      Instant calculated = collection == null ? null : next(calculationCron, collection);
      if (calculated == null) return unavailable(pending.getBaseDate(), "COLLECTION_PAUSED");
      expected = later(expected, calculated);
    }
    String reason =
        exchangeRatePending
            ? "EXCHANGE_RATE_PENDING"
            : domesticReady && usReady
                ? "CALCULATION_PENDING"
                : delayed
                    ? "PRICE_COLLECTION_DELAYED"
                    : (usRelevant ? pending.leagueClosesAt() : pending.closesAt()).isAfter(now)
                        ? "MARKET_CLOSE_PENDING"
                        : "PRICE_COLLECTION_PENDING";
    if (expected == null) expected = next(calculationCron, now);
    boolean awaitingFirstReturn =
        profile != null
            && ((profile.getTrackingStartedOn() == null
                    && profile.getParticipations().stream()
                        .noneMatch(period -> period.getPerformanceStartedAt() != null))
                || values.values().stream().anyMatch(PortfolioDailyReturn::isTrackingBaseline))
            && values.values().stream()
                .noneMatch(day -> day.isEligible() && !day.isTrackingBaseline());
    Instant firstReturn = null;
    if (awaitingFirstReturn && (usRelevant || priceEnabled)) {
      boolean hasBaseline = values.values().stream().anyMatch(PortfolioDailyReturn::isEligible);
      MarketTradingDay returnDay =
          hasBaseline
              ? pending
              : nextTradingDay(pending.getBaseDate(), usRelevant, domesticRelevant);
      if (returnDay != null) {
        if (domesticRelevant && returnDay.isTrading() && priceEnabled) {
          firstReturn = collectionAt(returnDay.getBaseDate());
          if (firstReturn != null && now.isAfter(firstReturn)) firstReturn = next(priceCron, now);
        }
        if (usRelevant && Boolean.TRUE.equals(returnDay.getUsTrading())) {
          Instant collection = usCollectionAt(returnDay.getBaseDate());
          if (collection != null && now.isAfter(collection)) collection = next(usPriceCron, now);
          Instant calculated = collection == null ? null : next(calculationCron, collection);
          firstReturn = later(firstReturn, calculated);
        }
      }
    }
    return new AggregationResponse(pending.getBaseDate(), expected, firstReturn, reason, delayed);
  }

  private MarketTradingDay nextTradingDay(LocalDate date, boolean us, boolean domestic) {
    MarketTradingDay kr =
        domestic
            ? tradingDayRepository
                .findFirstByTradingTrueAndBaseDateGreaterThanOrderByBaseDateAsc(date)
                .orElse(null)
            : null;
    if (!us) return kr;
    MarketTradingDay nextUs =
        tradingDayRepository
            .findAllByBaseDateBetweenOrderByBaseDate(date.plusDays(1), date.plusDays(14))
            .stream()
            .filter(day -> Boolean.TRUE.equals(day.getUsTrading()))
            .findFirst()
            .orElse(null);
    if (kr == null) return nextUs;
    if (nextUs == null || kr.getBaseDate().isBefore(nextUs.getBaseDate())) return kr;
    return nextUs;
  }

  private Instant usCollectionAt(LocalDate date) {
    // 가격 수집의 뉴욕 기준 익일 제한에 맞춰 실제 요청 가능한 일정을 안내한다.
    return next(
        usPriceCron,
        date.plusDays(1).atStartOfDay(ZoneId.of("America/New_York")).toInstant().minusNanos(1));
  }

  private Instant later(Instant first, Instant second) {
    if (first == null) return second;
    return second == null || first.isAfter(second) ? first : second;
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
