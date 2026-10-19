package org.grit.daynomy.league.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueParticipation;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.PortfolioDailyReturnRepository;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@RequiredArgsConstructor
@Service
public class LeagueReturnService {
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private final InvestorProfileRepository profileRepository;
  private final SharedPortfolioRepository portfolioRepository;
  private final SharedHoldingHistoryRepository historyRepository;
  private final StockDailyPriceRepository priceRepository;
  private final PortfolioDailyReturnRepository returnRepository;
  private final MarketTradingDayRepository tradingDayRepository;
  private final PlatformTransactionManager transactionManager;
  private final LeagueCalculationMetrics metrics;
  private final LeagueService leagueService;

  public synchronized int recalculate() {
    return recalculateSince(null, false);
  }

  public synchronized int recalculatePending() {
    return recalculateSince(null, true);
  }

  public synchronized int recalculateFrom(LocalDate from) {
    return recalculateSince(from, true);
  }

  private int recalculateSince(LocalDate from, boolean incremental) {
    long started = metrics.startRun();
    boolean completed = false;
    String mode = !incremental ? "FULL" : from == null ? "PENDING" : "FROM_DATE";
    String phase = "LOAD_CALENDAR";
    int calculated = 0;
    int failed = 0;
    int succeeded = 0;
    int target = 0;
    Map<String, Integer> outcomeCounts = new HashMap<>();
    log.atDebug()
        .addKeyValue("event", LogEvent.LEAGUE_CALCULATION_STARTED.code())
        .addKeyValue("mode", mode)
        .addKeyValue("fromDate", from)
        .log(LogEvent.LEAGUE_CALCULATION_STARTED.message());
    try {
      List<MarketTradingDay> days =
          tradingDayRepository
              .findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(LocalDate.now(SEOUL))
              .stream()
              .filter(day -> !day.closesAt().isAfter(Instant.now()))
              .toList();
      LocalDate firstCalculated = null;
      phase = "LOAD_PROFILES";
      List<InvestorProfile> profiles = profileRepository.findAllByLeagueEnabledAtIsNotNull();
      target = profiles.size();
      metrics.resetProfiles(profiles.size());
      phase = "CALCULATE_PROFILES";
      for (InvestorProfile candidate : profiles) {
        Map<String, Integer> outcomes = new HashMap<>();
        try {
          ProfileCalculation calculation =
              new TransactionTemplate(transactionManager)
                  .execute(
                      status -> {
                        InvestorProfile profile =
                            profileRepository.findByIdForUpdate(candidate.getId()).orElseThrow();
                        return calculateProfile(profile, days, outcomes, from, incremental);
                      });
          if (calculation != null) {
            calculated += calculation.days();
            if (calculation.firstDate() != null
                && (firstCalculated == null || calculation.firstDate().isBefore(firstCalculated))) {
              firstCalculated = calculation.firstDate();
            }
          }
          metrics.profileSucceeded(outcomes);
          succeeded++;
          outcomes.forEach((reason, count) -> outcomeCounts.merge(reason, count, Integer::sum));
          log.atDebug()
              .addKeyValue("event", LogEvent.LEAGUE_PROFILE_CALCULATION_COMPLETED.code())
              .addKeyValue("profileId", candidate.getId())
              .addKeyValue("mode", mode)
              .addKeyValue("fromDate", calculation == null ? null : calculation.firstDate())
              .addKeyValue("processedDayCount", calculation == null ? 0 : calculation.days())
              .addKeyValue("outcomeCounts", outcomes)
              .log(LogEvent.LEAGUE_PROFILE_CALCULATION_COMPLETED.message());
        } catch (RuntimeException exception) {
          metrics.profileFailed();
          failed++;
          // 한 포트폴리오 실패는 해당 트랜잭션만 롤백하고 다른 참가자 집계를 계속한다.
          // 예외 메시지의 입력값 노출을 피하고 호출 위치만 기록한다.
          log.atError()
              .addKeyValue("event", LogEvent.LEAGUE_PROFILE_CALCULATION_FAILED.code())
              .addKeyValue("profileId", candidate.getId())
              .addKeyValue("mode", mode)
              .addKeyValue("fromDate", from)
              .addKeyValue("exception", exception.getClass().getSimpleName())
              .addKeyValue(
                  "causeType",
                  exception.getCause() == null
                      ? null
                      : exception.getCause().getClass().getSimpleName())
              .addKeyValue("stackTrace", stackTraceWithoutMessages(exception))
              .log(LogEvent.LEAGUE_PROFILE_CALCULATION_FAILED.message());
        }
      }
      if (failed > 0) {
        throw new IllegalStateException("투자 리그 참가자 집계 실패: " + failed + "건");
      }
      phase = "FINALIZE_WEEKS";
      if (incremental) {
        leagueService.finalizeWeeks(
            firstCalculated == null ? LocalDate.now(SEOUL) : firstCalculated);
      } else {
        leagueService.finalizeWeeks();
      }
      int missingPrices = outcomeCounts.getOrDefault("MISSING_PRICE", 0);
      int unsupportedAssets = outcomeCounts.getOrDefault("UNSUPPORTED_ASSET", 0);
      if (missingPrices > 0 || unsupportedAssets > 0) {
        log.atWarn()
            .addKeyValue("event", LogEvent.LEAGUE_CALCULATION_INCOMPLETE.code())
            .addKeyValue("mode", mode)
            .addKeyValue("fromDate", firstCalculated)
            .addKeyValue("missingPriceDayCount", missingPrices)
            .addKeyValue("unsupportedAssetDayCount", unsupportedAssets)
            .log(LogEvent.LEAGUE_CALCULATION_INCOMPLETE.message());
      }
      log.atInfo()
          .addKeyValue("event", LogEvent.LEAGUE_CALCULATION_COMPLETED.code())
          .addKeyValue("mode", mode)
          .addKeyValue("requestedFromDate", from)
          .addKeyValue("fromDate", firstCalculated)
          .addKeyValue("toDate", days.isEmpty() ? null : days.getLast().getBaseDate())
          .addKeyValue("targetProfileCount", target)
          .addKeyValue("succeededProfileCount", succeeded)
          .addKeyValue("processedDayCount", calculated)
          .addKeyValue("outcomeCounts", outcomeCounts)
          .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
          .log(LogEvent.LEAGUE_CALCULATION_COMPLETED.message());
      completed = true;
      return calculated;
    } catch (RuntimeException exception) {
      log.atError()
          .addKeyValue("event", LogEvent.LEAGUE_CALCULATION_FAILED.code())
          .addKeyValue("mode", mode)
          .addKeyValue("phase", phase)
          .addKeyValue("fromDate", from)
          .addKeyValue("targetProfileCount", target)
          .addKeyValue("succeededProfileCount", succeeded)
          .addKeyValue("failedProfileCount", failed)
          .addKeyValue("processedDayCount", calculated)
          .addKeyValue("exception", exception.getClass().getSimpleName())
          .addKeyValue(
              "causeType",
              exception.getCause() == null ? null : exception.getCause().getClass().getSimpleName())
          .addKeyValue("stackTrace", stackTraceWithoutMessages(exception))
          .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
          .log(LogEvent.LEAGUE_CALCULATION_FAILED.message());
      throw exception;
    } finally {
      metrics.finishRun(started, completed);
    }
  }

  private static String stackTraceWithoutMessages(Throwable exception) {
    StringBuilder value = new StringBuilder();
    Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    for (Throwable current = exception;
        current != null && seen.add(current);
        current = current.getCause()) {
      if (value.length() > 0) value.append(" <- caused by ");
      value.append(current.getClass().getName()).append(Arrays.toString(current.getStackTrace()));
    }
    return value.toString();
  }

  private ProfileCalculation calculateProfile(
      InvestorProfile profile,
      List<MarketTradingDay> days,
      Map<String, Integer> outcomes,
      LocalDate from,
      boolean incremental) {
    SharedPortfolio portfolio =
        portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
    if (portfolio == null || profile.getLeagueEnabledAt() == null)
      return new ProfileCalculation(0, null);
    if (!incremental && profile.getTrackingStartedOn() != null) {
      tradingDayRepository
          .findById(profile.getTrackingStartedOn())
          .filter(day -> !day.isTrading())
          .ifPresent(day -> profile.correctTrackingStart());
    }
    LocalDate starting = null;
    if (incremental) {
      starting =
          returnRepository
              .findFirstByPortfolioIdOrderByBaseDateDescCalculationVersionDesc(portfolio.getId())
              .map(PortfolioDailyReturn::getBaseDate)
              .orElse(null);
      if (starting != null && from != null && from.isBefore(starting)) starting = from;
    }
    Set<LeagueParticipation> baselined = new HashSet<>();
    Set<LeagueParticipation> checked = new HashSet<>();
    int count = 0;
    LocalDate firstCalculated = null;
    for (MarketTradingDay day : days) {
      if (starting != null && day.getBaseDate().isBefore(starting)) continue;
      if (day.closesAt().isBefore(profile.getLeagueEnabledAt())) continue;
      LeagueParticipation period =
          profile.getParticipations().stream()
              .filter(value -> value.contains(day.closesAt()))
              .findFirst()
              .orElse(null);
      if (period == null) continue;
      if (firstCalculated == null) firstCalculated = day.getBaseDate();
      if (starting != null && checked.add(period)) {
        LocalDate first = period.getStartedAt().atZone(SEOUL).toLocalDate();
        if (period
            .getStartedAt()
            .isAfter(first.atTime(LocalTime.of(15, 30)).atZone(SEOUL).toInstant())) {
          first = first.plusDays(1);
        }
        LocalDate prior = day.getBaseDate().minusDays(1);
        if (!first.isAfter(prior)
            && returnRepository.existsLatestEligibleByPortfolioIdAndBaseDateBetween(
                portfolio.getId(), first, prior)) baselined.add(period);
      }
      PortfolioDailyReturn value = calculate(portfolio, day, !baselined.contains(period));
      outcomes.merge(
          value.isEligible() ? "CALCULATED" : value.getIneligibleReason(), 1, Integer::sum);
      if (value.isEligible()) {
        if (baselined.add(period)) profile.startTracking(day.getBaseDate());
      }
      count++;
    }
    return new ProfileCalculation(count, firstCalculated);
  }

  private record ProfileCalculation(int days, LocalDate firstDate) {}

  private PortfolioDailyReturn calculate(
      SharedPortfolio portfolio, MarketTradingDay day, boolean baseline) {
    LocalDate baseDate = day.getBaseDate();
    PortfolioDailyReturn previousResult =
        returnRepository
            .findFirstByPortfolioIdAndBaseDateOrderByCalculationVersionDesc(
                portfolio.getId(), baseDate)
            .orElse(null);
    PortfolioDailyReturn result =
        new PortfolioDailyReturn(
            portfolio,
            baseDate,
            previousResult == null ? 1 : previousResult.getCalculationVersion() + 1);
    Instant calculatedAt = Instant.now();
    if (day.getCollectedAt() == null)
      return exclude(result, previousResult, "PRICE_COLLECTION_PENDING", calculatedAt);
    List<SharedHoldingHistory> holdings =
        SharedHoldingHistory.reconstructHoldings(
            historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                portfolio.getId(),
                baseline ? day.closesAt().plusNanos(1) : baseDate.atStartOfDay(SEOUL).toInstant()));
    if (holdings.isEmpty()) return exclude(result, previousResult, "NO_HOLDINGS", calculatedAt);
    MarketTradingDay previousDay =
        baseline
            ? null
            : tradingDayRepository
                .findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(baseDate)
                .orElse(null);
    if (!baseline && (previousDay == null || previousDay.getCollectedAt() == null))
      return exclude(result, previousResult, "PRICE_COLLECTION_PENDING", calculatedAt);
    BigDecimal start = BigDecimal.ZERO;
    BigDecimal end = BigDecimal.ZERO;
    BigDecimal largest = BigDecimal.ZERO;
    BigDecimal largestEnd = BigDecimal.ZERO;
    for (SharedHoldingHistory holding : holdings) {
      Asset asset = holding.getAsset();
      if (!asset.isListed()
          || (asset.getCategory() != AssetCategory.STOCK
              && asset.getCategory() != AssetCategory.ETF))
        return exclude(result, previousResult, "UNSUPPORTED_ASSET", calculatedAt);
      StockDailyPrice current = findPrice(asset.getId(), baseDate);
      StockDailyPrice previous =
          baseline ? current : findPrice(asset.getId(), previousDay.getBaseDate());
      if (previous == null || current == null)
        return exclude(result, previousResult, "MISSING_PRICE", calculatedAt);
      BigDecimal quantity = BigDecimal.valueOf(holding.getQuantity());
      BigDecimal position = previous.getClosePrice().multiply(quantity);
      start = start.add(position);
      end = end.add(current.getClosePrice().multiply(quantity));
      largest = largest.max(position);
      largestEnd = largestEnd.max(current.getClosePrice().multiply(quantity));
    }
    if (start.signum() <= 0) return exclude(result, previousResult, "NO_HOLDINGS", calculatedAt);
    BigDecimal weight = largest.multiply(HUNDRED).divide(start, 2, RoundingMode.HALF_UP);
    if (baseline)
      result.completeBaseline(end.setScale(2, RoundingMode.HALF_UP), weight, calculatedAt);
    else
      result.complete(
          end.subtract(start).multiply(HUNDRED).divide(start, 4, RoundingMode.HALF_UP),
          start.setScale(2, RoundingMode.HALF_UP),
          end.setScale(2, RoundingMode.HALF_UP),
          weight,
          largestEnd.multiply(HUNDRED).divide(end, 2, RoundingMode.HALF_UP),
          calculatedAt);
    return saveCalculation(result, previousResult);
  }

  private PortfolioDailyReturn exclude(
      PortfolioDailyReturn result,
      PortfolioDailyReturn previousResult,
      String reason,
      Instant calculatedAt) {
    result.exclude(reason, calculatedAt);
    return saveCalculation(result, previousResult);
  }

  private PortfolioDailyReturn saveCalculation(
      PortfolioDailyReturn result, PortfolioDailyReturn previous) {
    if (result.sameCalculation(previous)) return previous;
    returnRepository.save(result);
    return result;
  }

  private StockDailyPrice findPrice(Long assetId, LocalDate date) {
    return priceRepository.findByAssetIdAndBaseDate(assetId, date).orElse(null);
  }
}
