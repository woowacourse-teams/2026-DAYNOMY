package org.grit.daynomy.league.service;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.asset.service.StockPriceService;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.grit.daynomy.league.domain.InvestmentReview;
import org.grit.daynomy.league.domain.InvestorFollow;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes.CollectionStatus;
import org.grit.daynomy.league.domain.LeagueTypes.DailyReturnStatus;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.domain.WeeklyLeaderboard;
import org.grit.daynomy.league.dto.LeagueDto.AllocationResponse;
import org.grit.daynomy.league.dto.LeagueDto.DailyHistoryResponse;
import org.grit.daynomy.league.dto.LeagueDto.DailyReturnPointResponse;
import org.grit.daynomy.league.dto.LeagueDto.FollowSummaryListResponse;
import org.grit.daynomy.league.dto.LeagueDto.FollowSummaryResponse;
import org.grit.daynomy.league.dto.LeagueDto.HistoryPointResponse;
import org.grit.daynomy.league.dto.LeagueDto.InvestorDetailResponse;
import org.grit.daynomy.league.dto.LeagueDto.PerformanceResponse;
import org.grit.daynomy.league.dto.LeagueDto.PublicDecisionResponse;
import org.grit.daynomy.league.dto.LeagueDto.PublicHoldingResponse;
import org.grit.daynomy.league.dto.LeagueDto.PublicInvestorResponse;
import org.grit.daynomy.league.dto.LeagueDto.RankingEntryResponse;
import org.grit.daynomy.league.dto.LeagueDto.RankingResponse;
import org.grit.daynomy.league.dto.LeagueDto.ReviewResponse;
import org.grit.daynomy.league.dto.LeagueDto.WeekResponse;
import org.grit.daynomy.league.dto.LeagueDto.WeeksResponse;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.InvestmentReviewRepository;
import org.grit.daynomy.league.repository.InvestorFollowRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.PortfolioDailyReturnRepository;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.league.repository.WeeklyLeaderboardRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@RequiredArgsConstructor
@Service
public class LeagueService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final BigDecimal HUNDRED = new BigDecimal("100");
  private static final BigDecimal ONE = BigDecimal.ONE;

  private final InvestorProfileRepository profileRepository;
  private final SharedPortfolioRepository portfolioRepository;
  private final SharedHoldingRepository holdingRepository;
  private final SharedHoldingHistoryRepository holdingHistoryRepository;
  private final PortfolioDailyReturnRepository returnRepository;
  private final InvestmentDecisionRepository decisionRepository;
  private final InvestmentReviewRepository reviewRepository;
  private final InvestorFollowRepository followRepository;
  private final MemberRepository memberRepository;
  private final StockDailyPriceRepository priceRepository;
  private final StockPriceService stockPriceService;
  private final MarketTradingDayRepository tradingDayRepository;
  private final WeeklyLeaderboardRepository leaderboardRepository;
  private final LeagueCalculationMetrics calculationMetrics;
  private final EntityManager entityManager;
  private Map<LocalDate, WeekState> finalizationStates = new HashMap<>();
  private boolean finalizationStatesInitialized;
  private final ThreadLocal<Map<CalendarRange, List<MarketTradingDay>>> finalizationCalendarCache =
      new ThreadLocal<>();

  @Transactional(readOnly = true)
  public WeeksResponse weeks() {
    LocalDate today = LocalDate.now(SEOUL);
    LocalDate currentWeek = normalizeWeek(today);
    List<WeekResponse> weeks =
        IntStream.range(0, 3)
            .mapToObj(currentWeek::minusWeeks)
            .map(
                start ->
                    new WeekResponse(
                        start,
                        start.plusDays(6),
                        finalized(start) != null,
                        collectionStatus(start)))
            .toList();
    return new WeeksResponse(weeks);
  }

  @Transactional(readOnly = true)
  public RankingResponse rankings(
      Long viewerMemberId, LocalDate requestedWeek, LeagueType leagueType, int page, int size) {
    LocalDate weekStart = normalizeWeek(requestedWeek);
    LocalDate weekEnd = weekStart.plusDays(6);
    validateWeek(weekStart);
    WeeklyLeaderboard finalResult =
        leaderboardRepository
            .findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(weekStart, leagueType)
            .orElse(null);
    Set<Long> followed = followedProfileIds(viewerMemberId);
    List<RankingEntryResponse> entries;
    if (finalResult == null) {
      List<RankedProfile> profiles = rankedProfiles(weekStart, leagueType, true);
      List<Integer> ranks = ranks(profiles, leagueType);
      entries =
          IntStream.range(0, profiles.size())
              .mapToObj(
                  index ->
                      rankingEntry(
                          ranks.get(index),
                          profiles.get(index),
                          followed.contains(profiles.get(index).profile().getId())))
              .toList();
    } else {
      // 과거 확정값에도 현재 공개 동의를 적용하고 확정 순위 자체는 재배열하지 않는다.
      entries =
          finalResult.getEntries().stream()
              .map(
                  entry ->
                      profileRepository
                          .findById(entry.profileId())
                          .filter(profile -> profile.isProfilePublic() && profile.isLeagueEnabled())
                          .map(
                              profile ->
                                  snapshotEntry(profile, entry, followed.contains(profile.getId())))
                          .orElse(null))
              .filter(Objects::nonNull)
              .toList();
    }
    int from = (int) Math.min((long) Math.max(0, page) * size, entries.size());
    int to = Math.min(from + size, entries.size());
    return new RankingResponse(
        weekStart,
        weekEnd,
        leagueType,
        finalResult != null,
        entries.size(),
        entries.subList(from, to),
        finalResult == null ? asOfDate(weekStart) : finalResult.getAsOfDate(),
        finalResult == null ? null : finalResult.getCalculationVersion(),
        finalResult == null ? null : finalResult.getFinalizedAt(),
        finalResult == null ? null : finalResult.getChangeReason(),
        collectionStatus(weekStart));
  }

  private List<RankedProfile> rankedProfiles(LocalDate week, LeagueType type, boolean publicOnly) {
    return (publicOnly
            ? profileRepository.findAllByProfilePublicTrueAndLeagueEnabledTrue()
            : profileRepository.findAllByLeagueEnabledAtIsNotNull())
        .stream()
            .filter(
                profile ->
                    type != LeagueType.BEGINNER
                        || profile.getExperienceLevel() == ExperienceLevel.BEGINNER)
            .filter(profile -> participatesInWeek(profile, week))
            .map(profile -> metrics(profile, week))
            .filter(ranked -> ranked.metric() != null && ranked.metric().weeklyReturnRate() != null)
            .filter(
                ranked ->
                    type != LeagueType.STABLE
                        || (ranked.metric().volatilityRate() != null
                            && ranked.metric().threeWeekReturnRate() != null
                            && ranked.metric().maxDrawdownRate() != null))
            .sorted(comparator(type))
            .toList();
  }

  @Transactional
  public synchronized void finalizeWeeks() {
    finalizeWeeks(null);
  }

  @Transactional
  public synchronized void finalizeWeeks(LocalDate from) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (status != STATUS_COMMITTED) {
                synchronized (LeagueService.this) {
                  finalizationStatesInitialized = false;
                }
              }
            }
          });
    }
    finalizationCalendarCache.set(new HashMap<>());
    try {
      finalizeWeeksWithCache(from);
    } finally {
      finalizationCalendarCache.remove();
    }
  }

  private void finalizeWeeksWithCache(LocalDate from) {
    long started = System.nanoTime();
    List<InvestorProfile> profiles = profileRepository.findAllByLeagueEnabledAtIsNotNull();
    LocalDate first =
        profiles.stream()
            .map(InvestorProfile::getLeagueEnabledAt)
            .filter(Objects::nonNull)
            .map(at -> normalizeWeek(at.atZone(SEOUL).toLocalDate()))
            .min(LocalDate::compareTo)
            .orElse(null);
    if (first == null) {
      finalizationStates = new HashMap<>();
      finalizationStatesInitialized = true;
      calculationMetrics.finalizationState(0, 0, null);
      calculationMetrics.finalizationMeasured(started, 0, true);
      return;
    }
    boolean full = from == null || !finalizationStatesInitialized;
    LocalDate start = full ? first : normalizeWeek(from);
    if (start.isBefore(first)) start = first;
    Map<LocalDate, WeekState> states = full ? new HashMap<>() : new HashMap<>(finalizationStates);
    int scanned = 0;
    for (LocalDate week = start; !week.isAfter(normalizeWeek(null)); week = week.plusWeeks(1)) {
      scanned++;
      List<MarketTradingDay> calendar = calendar(week, week.plusDays(6));
      List<MarketTradingDay> trading =
          calendar.stream().filter(MarketTradingDay::isTrading).toList();
      LocalDate weekStart = week;
      boolean participating =
          profiles.stream().anyMatch(profile -> participatesInWeek(profile, weekStart));
      if (calendar.size() != 7) {
        if (participating) states.put(week, new WeekState(7 - calendar.size(), null));
        else states.remove(week);
        continue;
      }
      if (trading.isEmpty() || trading.getLast().closesAt().isAfter(Instant.now())) {
        states.remove(week);
        continue;
      }
      if (trading.stream().anyMatch(day -> day.getCollectedAt() == null)
          || !calculationsComplete(profiles, week)) {
        if (participating) states.put(week, new WeekState(0, trading.getLast().closesAt()));
        else states.remove(week);
        continue;
      }
      states.remove(week);
      List<RankedProfile> calculatedProfiles =
          profiles.stream()
              .filter(profile -> participatesInWeek(profile, weekStart))
              .map(profile -> metrics(profile, weekStart))
              .filter(
                  ranked -> ranked.metric() != null && ranked.metric().weeklyReturnRate() != null)
              .toList();
      for (LeagueType type : LeagueType.values()) {
        List<RankedProfile> ranked =
            calculatedProfiles.stream()
                .filter(
                    profile ->
                        type != LeagueType.BEGINNER
                            || profile.profile().getExperienceLevel() == ExperienceLevel.BEGINNER)
                .filter(
                    profile ->
                        type != LeagueType.STABLE
                            || (profile.metric().volatilityRate() != null
                                && profile.metric().threeWeekReturnRate() != null
                                && profile.metric().maxDrawdownRate() != null))
                .sorted(comparator(type))
                .toList();
        List<Integer> ranks = ranks(ranked, type);
        List<WeeklyLeaderboard.Entry> entries =
            IntStream.range(0, ranked.size())
                .mapToObj(
                    index -> {
                      RankedProfile value = ranked.get(index);
                      Metric m = value.metric();
                      return new WeeklyLeaderboard.Entry(
                          ranks.get(index),
                          value.profile().getId(),
                          m.weeklyReturnRate(),
                          null,
                          m.threeWeekReturnRate(),
                          null,
                          m.maxDrawdownRate(),
                          m.volatilityRate(),
                          m.maxHoldingWeight(),
                          m.currentHoldingWeight(),
                          m.consecutiveRecordedWeeks(),
                          value.decisionCount(),
                          value.reviewCompletionRate(),
                          value.profile().getTrackingStartedOn());
                    })
                .toList();
        WeeklyLeaderboard previous =
            leaderboardRepository
                .findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(week, type)
                .orElse(null);
        if (previous != null
            && previous.getEntries().equals(entries)
            && previous.getAsOfDate().equals(trading.getLast().getBaseDate())) continue;
        leaderboardRepository.save(
            new WeeklyLeaderboard(
                week,
                type,
                previous == null ? 1 : previous.getCalculationVersion() + 1,
                trading.getLast().getBaseDate(),
                previous == null
                    ? "INITIAL_FINALIZATION"
                    : !previous.getAsOfDate().equals(trading.getLast().getBaseDate())
                        ? "CALENDAR_RECALCULATION"
                        : "PRICE_RECALCULATION",
                entries));
      }
    }
    finalizationStates = states;
    finalizationStatesInitialized = true;
    int missingCalendarDays = states.values().stream().mapToInt(WeekState::missingDays).sum();
    int pendingWeeks =
        (int) states.values().stream().filter(state -> state.pendingClose() != null).count();
    Instant oldestPendingClose =
        states.values().stream()
            .map(WeekState::pendingClose)
            .filter(Objects::nonNull)
            .min(Instant::compareTo)
            .orElse(null);
    Instant expectedCollection = null;
    if (oldestPendingClose != null) {
      var nextDay =
          tradingDayRepository
              .findFirstByTradingTrueAndBaseDateGreaterThanOrderByBaseDateAsc(
                  oldestPendingClose.atZone(SEOUL).toLocalDate())
              .orElse(null);
      if (nextDay == null) missingCalendarDays++;
      else expectedCollection = nextDay.getBaseDate().atTime(13, 0).atZone(SEOUL).toInstant();
    }
    calculationMetrics.finalizationState(missingCalendarDays, pendingWeeks, expectedCollection);
    calculationMetrics.finalizationMeasured(started, scanned, full);
  }

  private record WeekState(int missingDays, Instant pendingClose) {}

  private record CalendarRange(LocalDate from, LocalDate to) {}

  private boolean calculationsComplete(List<InvestorProfile> profiles, LocalDate week) {
    for (InvestorProfile profile : profiles) {
      if (!participatesInWeek(profile, week)) continue;
      SharedPortfolio portfolio =
          portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
      if (portfolio == null) continue;
      Map<LocalDate, PortfolioDailyReturn> values =
          dailyValues(portfolio.getId(), week, week.plusDays(6));
      for (LocalDate date : tradingDates(week)) {
        if (!participates(profile, date)) continue;
        PortfolioDailyReturn value = values.get(date);
        if (value == null
            || (value.isEligible() && value.getDailyReturnRate() == null)
            || "MISSING_PRICE".equals(value.getIneligibleReason())
            || "PRICE_COLLECTION_PENDING".equals(value.getIneligibleReason())) return false;
      }
      values.values().forEach(entityManager::detach);
    }
    return true;
  }

  private WeeklyLeaderboard finalized(LocalDate week) {
    return leaderboardRepository
        .findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(
            week, LeagueType.WEEKLY_RETURN)
        .orElse(null);
  }

  private RankingEntryResponse snapshotEntry(
      InvestorProfile profile, WeeklyLeaderboard.Entry e, boolean followed) {
    return new RankingEntryResponse(
        e.rank(),
        profile.getPublicId(),
        profile.getDisplayName(),
        profile.getExperienceLevel(),
        profile.getRiskProfile(),
        e.weeklyReturnRate(),
        e.threeWeekReturnRate(),
        e.maxDrawdownRate(),
        e.volatilityRate(),
        e.maxHoldingWeight(),
        e.decisionCount(),
        e.reviewCompletionRate(),
        followed,
        profile.getMember().getProfileImageUrl(),
        e.trackingStartedOn(),
        e.currentHoldingWeight(),
        e.consecutiveRecordedWeeks());
  }

  @Transactional(readOnly = true)
  public PublicInvestorResponse publicInvestor(Long viewerMemberId, String publicId) {
    InvestorProfile profile = getPublicProfile(publicId);
    LocalDate weekStart = normalizeWeek(null);
    RankedProfile ranked = metrics(profile, weekStart);
    Metric metric = ranked.metric();
    List<HistoryPointResponse> history = history(profile, weekStart);
    return new PublicInvestorResponse(
        profile.getPublicId(),
        profile.getDisplayName(),
        profile.getBio(),
        profile.getExperienceLevel(),
        profile.getRiskProfile(),
        performance(profile, metric, history.getLast()),
        profile.isAllocationPublic() ? allocation(profile.getMember().getId()) : null,
        history,
        ranked.decisionCount(),
        ranked.reviewCompletionRate(),
        isFollowed(viewerMemberId, profile.getId()),
        profile.isDetailPublic(),
        profile.getMember().getProfileImageUrl());
  }

  @Transactional(readOnly = true)
  public DailyHistoryResponse dailyHistory(String publicId, LocalDate requestedWeek) {
    InvestorProfile profile = getPublicProfile(publicId);
    LocalDate weekStart = normalizeWeek(requestedWeek);
    validateWeek(weekStart);
    LocalDate weekEnd = weekStart.plusDays(6);
    LocalDate eligibleFrom = profile.eligibleFrom();
    LocalDate start = weeklyStart(profile, weekStart);
    SharedPortfolio portfolio =
        portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
    Map<LocalDate, PortfolioDailyReturn> values =
        portfolio == null ? Map.of() : dailyValues(portfolio.getId(), weekStart, weekEnd);
    List<DailyReturnPointResponse> days = new ArrayList<>();
    List<PortfolioDailyReturn> calculated = new ArrayList<>();
    boolean complete = true;
    boolean begun = false;
    LocalDate asOf = null;
    LocalDate calculationEnd = calculationEnd(weekStart);
    for (LocalDate date : tradingDates(weekStart)) {
      PortfolioDailyReturn value = values.get(date);
      DailyReturnStatus status;
      String reason = null;
      boolean afterCalculationEnd = calculationEnd == null || date.isAfter(calculationEnd);
      if (!participates(profile, date) || date.isBefore(start)) {
        status = DailyReturnStatus.NOT_PARTICIPATING;
        if (begun && !afterCalculationEnd) complete = false;
      } else if (afterCalculationEnd) {
        status =
            value != null && !value.isEligible()
                ? DailyReturnStatus.EXCLUDED
                : DailyReturnStatus.PENDING;
        if (status == DailyReturnStatus.EXCLUDED) reason = value.getIneligibleReason();
      } else if (value != null && value.isEligible()) {
        status = DailyReturnStatus.CALCULATED;
        if (value.isTrackingBaseline()) reason = "TRACKING_BASELINE";
        asOf = date;
        begun = true;
        calculated.add(value);
      } else if (value != null) {
        status = DailyReturnStatus.EXCLUDED;
        reason = value.getIneligibleReason();
        complete = false;
      } else {
        status = DailyReturnStatus.PENDING;
        complete = false;
      }
      days.add(
          new DailyReturnPointResponse(
              date,
              status == DailyReturnStatus.CALCULATED ? value.getDailyReturnRate() : null,
              complete && begun && status == DailyReturnStatus.CALCULATED
                  ? chainedReturn(calculated)
                  : null,
              status,
              reason));
    }
    BigDecimal weekly = complete && begun ? chainedReturn(calculated) : null;
    WeeklyLeaderboard result = finalized(weekStart);
    boolean confirmed =
        result != null
            && weekly != null
            && result.getEntries().stream()
                .anyMatch(
                    entry ->
                        entry.profileId().equals(profile.getId())
                            && entry.weeklyReturnRate().compareTo(weekly) == 0);
    return new DailyHistoryResponse(
        weekStart,
        weekEnd,
        asOf,
        eligibleFrom,
        confirmed,
        weekly,
        days,
        profile.getTrackingStartedOn(),
        collectionStatus(weekStart));
  }

  private void validateWeek(LocalDate week) {
    LocalDate current = normalizeWeek(null);
    if (week.isAfter(current) || week.isBefore(current.minusWeeks(2)))
      throw new BusinessException(LeagueErrorCode.INVALID_LEAGUE_WEEK);
  }

  private List<MarketTradingDay> calendar(LocalDate from, LocalDate to) {
    Map<CalendarRange, List<MarketTradingDay>> cache = finalizationCalendarCache.get();
    if (cache == null)
      return tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(from, to);
    return cache.computeIfAbsent(
        new CalendarRange(from, to),
        range ->
            tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(range.from(), range.to()));
  }

  private List<LocalDate> tradingDates(LocalDate weekStart) {
    return calendar(weekStart, weekStart.plusDays(6)).stream()
        .filter(MarketTradingDay::isTrading)
        .filter(day -> !day.closesAt().isAfter(Instant.now()))
        .map(MarketTradingDay::getBaseDate)
        .toList();
  }

  private LocalDate asOfDate(LocalDate week) {
    return calendar(week, week.plusDays(6)).stream()
        .filter(day -> day.isTrading() && day.getCollectedAt() != null)
        .map(MarketTradingDay::getBaseDate)
        .max(LocalDate::compareTo)
        .orElse(null);
  }

  private LocalDate calculationEnd(LocalDate week) {
    return week.equals(normalizeWeek(null)) ? asOfDate(week) : week.plusDays(6);
  }

  private boolean participates(InvestorProfile profile, LocalDate date) {
    Instant close = date.atTime(15, 30).atZone(SEOUL).toInstant();
    return profile.getParticipations().stream().anyMatch(period -> period.contains(close));
  }

  private CollectionStatus collectionStatus(LocalDate week) {
    List<MarketTradingDay> calendar = calendar(week, week.plusDays(6));
    if (calendar.size() != 7) return CollectionStatus.CALENDAR_PENDING;
    List<MarketTradingDay> days = calendar.stream().filter(MarketTradingDay::isTrading).toList();
    if (days.isEmpty()) return CollectionStatus.NO_TRADING_DAYS;
    if (days.stream().anyMatch(day -> day.closesAt().isAfter(Instant.now())))
      return CollectionStatus.IN_PROGRESS;
    return days.stream().anyMatch(day -> day.getCollectedAt() == null)
        ? CollectionStatus.COLLECTION_PENDING
        : CollectionStatus.COMPLETE;
  }

  private boolean participatesInWeek(InvestorProfile profile, LocalDate week) {
    return profile.getParticipations().stream()
        .anyMatch(
            period ->
                !period.eligibleFrom().isAfter(week.plusDays(6))
                    && (period.getEndedAt() == null
                        || period.getEndedAt().isAfter(week.atStartOfDay(SEOUL).toInstant())));
  }

  private LocalDate weeklyStart(InvestorProfile profile, LocalDate week) {
    LocalDate start =
        tradingDates(week).stream()
            .filter(date -> participates(profile, date))
            .findFirst()
            .orElse(week);
    return profile.getTrackingStartedOn() != null && profile.getTrackingStartedOn().isAfter(start)
        ? profile.getTrackingStartedOn()
        : start;
  }

  private Map<LocalDate, PortfolioDailyReturn> dailyValues(
      Long portfolioId, LocalDate from, LocalDate to) {
    return returnRepository
        .findLatestByPortfolioIdAndBaseDateBetween(portfolioId, from, to)
        .stream()
        .collect(HashMap::new, (map, value) -> map.put(value.getBaseDate(), value), Map::putAll);
  }

  @Transactional(readOnly = true)
  public InvestorDetailResponse investorDetail(String publicId) {
    InvestorProfile profile = getPublicProfile(publicId);
    if (!profile.isDetailPublic()) {
      throw new BusinessException(LeagueErrorCode.DETAIL_NOT_PUBLIC);
    }
    SharedPortfolio portfolio =
        portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
    LocalDate cutoff = LocalDate.now(SEOUL);
    if (portfolio == null) {
      return new InvestorDetailResponse(publicId, null, List.of(), List.of());
    }
    List<InvestmentDecision> transactions =
        decisionRepository.findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
            portfolio.getId(), cutoff);
    Map<Long, List<InvestmentReview>> reviews = reviewsByTransaction(transactions);
    DetailSnapshot snapshot =
        detailSnapshot(
            profile, portfolio.getId(), cutoff, InvestmentDecision.latestReasons(transactions));
    Set<Long> visibleAssets = visibleAssets(portfolio.getId());
    return new InvestorDetailResponse(
        publicId,
        snapshot.asOfDate(),
        snapshot.holdings(),
        transactions.stream()
            .filter(transaction -> visibleAssets.contains(transaction.getAsset().getId()))
            .map(
                transaction ->
                    new PublicDecisionResponse(
                        transaction.getId(),
                        transaction.getAsset().getName(),
                        transaction.getAsset().getCategory().name(),
                        transaction.getTransactionType(),
                        transaction.getTradedOn(),
                        transaction.getDecisionReason(),
                        transaction.getExpectedHoldingPeriod(),
                        transaction.getExpectedChange(),
                        transaction.getInvalidationCondition(),
                        transaction.getMaximumAcceptableLossRate(),
                        transaction.isWrittenAfterTrade(),
                        reviews.getOrDefault(transaction.getId(), List.of()).stream()
                            .map(ReviewResponse::from)
                            .toList()))
            .toList());
  }

  private DetailSnapshot detailSnapshot(
      InvestorProfile profile, Long portfolioId, LocalDate cutoff, Map<Long, String> reasons) {
    LocalDate asOfDate =
        tradingDayRepository
            .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                cutoff)
            .map(MarketTradingDay::getBaseDate)
            .orElse(null);
    Map<Long, BigDecimal> contributions =
        asOfDate == null ? null : weeklyContributions(profile, portfolioId, asOfDate);
    List<SharedHolding> holdings = holdingRepository.findAllByPortfolioIdOrderById(portfolioId);
    Map<Long, StockDailyPrice> prices =
        stockPriceService.pricesAt(
            holdings.stream().map(holding -> holding.getAsset().getId()).toList(), asOfDate);
    Map<Long, BigDecimal> evaluations = new LinkedHashMap<>();
    BigDecimal total = BigDecimal.ZERO;
    for (SharedHolding holding : holdings) {
      StockDailyPrice price = prices.get(holding.getAsset().getId());
      if (price == null) {
        continue;
      }
      BigDecimal evaluation =
          price.getClosePrice().multiply(BigDecimal.valueOf(holding.getQuantity()));
      evaluations.put(holding.getAsset().getId(), evaluation);
      total = total.add(evaluation);
    }
    BigDecimal totalEvaluation = total;
    boolean pricesComplete = evaluations.size() == holdings.size() && total.signum() > 0;
    List<PublicHoldingResponse> responses =
        holdings.stream()
            .filter(holding -> !holding.isHidden())
            .map(
                holding ->
                    new PublicHoldingResponse(
                        holding.getAsset().getName(),
                        holding.getAsset().getCategory().name(),
                        pricesComplete
                            ? evaluations
                                .get(holding.getAsset().getId())
                                .multiply(HUNDRED)
                                .divide(totalEvaluation, 2, RoundingMode.HALF_UP)
                            : null,
                        contributions != null
                            ? contributions
                                .getOrDefault(holding.getAsset().getId(), BigDecimal.ZERO)
                                .setScale(2, RoundingMode.HALF_UP)
                            : null,
                        evaluations.containsKey(holding.getAsset().getId())
                            ? prices
                                .get(holding.getAsset().getId())
                                .getClosePrice()
                                .subtract(holding.getAveragePurchasePrice())
                                .multiply(HUNDRED)
                                .divide(holding.getAveragePurchasePrice(), 2, RoundingMode.HALF_UP)
                            : null,
                        holding.reasonFrom(reasons)))
            .toList();
    return new DetailSnapshot(asOfDate, responses);
  }

  private Map<Long, BigDecimal> weeklyContributions(
      InvestorProfile profile, Long portfolioId, LocalDate asOfDate) {
    LocalDate week = normalizeWeek(asOfDate);
    LocalDate start = weeklyStart(profile, week);
    if (start.isAfter(asOfDate)) return null;
    Map<LocalDate, PortfolioDailyReturn> values = dailyValues(portfolioId, week, asOfDate);
    Map<Long, BigDecimal> contributions = new LinkedHashMap<>();
    BigDecimal index = ONE;
    for (LocalDate date : tradingDates(week)) {
      if (date.isAfter(asOfDate)) break;
      if (date.isBefore(start)) continue;
      PortfolioDailyReturn daily = values.get(date);
      if (daily == null || !daily.isEligible()) return null;
      if (daily.getDailyReturnRate() == null) return null;
      if (daily.isTrackingBaseline()) continue;
      MarketTradingDay previous =
          tradingDayRepository
              .findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(date)
              .orElse(null);
      if (previous == null
          || !addDailyContributions(
              portfolioId, previous.getBaseDate(), date, index, contributions)) return null;
      index =
          index.multiply(
              ONE.add(daily.getDailyReturnRate().divide(HUNDRED, 10, RoundingMode.HALF_UP)));
    }
    return contributions;
  }

  private boolean addDailyContributions(
      Long portfolioId,
      LocalDate previousDate,
      LocalDate baseDate,
      BigDecimal index,
      Map<Long, BigDecimal> contributions) {
    List<SharedHoldingHistory> holdings =
        SharedHoldingHistory.reconstructHoldings(
            holdingHistoryRepository
                .findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                    portfolioId, baseDate.atStartOfDay(SEOUL).toInstant()));
    Map<Long, PricePair> prices = new LinkedHashMap<>();
    BigDecimal total = BigDecimal.ZERO;
    for (SharedHoldingHistory holding : holdings) {
      StockDailyPrice previous = findPrice(holding.getAsset().getId(), previousDate);
      StockDailyPrice current = findPrice(holding.getAsset().getId(), baseDate);
      if (previous == null || current == null) return false;
      BigDecimal evaluation =
          previous.getClosePrice().multiply(BigDecimal.valueOf(holding.getQuantity()));
      prices.put(holding.getAsset().getId(), new PricePair(previous, current, evaluation));
      total = total.add(evaluation);
    }
    if (total.signum() <= 0) return false;
    for (Map.Entry<Long, PricePair> entry : prices.entrySet()) {
      PricePair pair = entry.getValue();
      BigDecimal weight = pair.evaluation().divide(total, 10, RoundingMode.HALF_UP);
      BigDecimal assetReturn =
          pair.current()
              .getClosePrice()
              .subtract(pair.previous().getClosePrice())
              .divide(pair.previous().getClosePrice(), 10, RoundingMode.HALF_UP);
      contributions.merge(
          entry.getKey(),
          index.multiply(weight).multiply(assetReturn).multiply(HUNDRED),
          BigDecimal::add);
    }
    return true;
  }

  private StockDailyPrice findPrice(Long assetId, LocalDate baseDate) {
    return priceRepository.findByAssetIdAndBaseDate(assetId, baseDate).orElse(null);
  }

  private Set<Long> visibleAssets(Long portfolioId) {
    return holdingRepository.findAllByPortfolioIdOrderById(portfolioId).stream()
        .filter(holding -> !holding.isHidden())
        .map(holding -> holding.getAsset().getId())
        .collect(java.util.stream.Collectors.toSet());
  }

  @Transactional
  public void follow(Long memberId, String publicId) {
    Member member =
        memberRepository
            .findById(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    InvestorProfile profile = getPublicProfile(publicId);
    if (profile.getMember().getId().equals(memberId)) {
      throw new BusinessException(LeagueErrorCode.SELF_FOLLOW_NOT_ALLOWED);
    }
    if (followRepository.findByFollowerIdAndProfileId(memberId, profile.getId()).isEmpty()) {
      followRepository.save(new InvestorFollow(member, profile));
    }
  }

  @Transactional
  public void unfollow(Long memberId, String publicId) {
    InvestorProfile profile = getPublicProfile(publicId);
    followRepository
        .findByFollowerIdAndProfileId(memberId, profile.getId())
        .ifPresent(followRepository::delete);
  }

  @Transactional(readOnly = true)
  public FollowSummaryListResponse followSummary(Long memberId) {
    LocalDate weekStart = normalizeWeek(null);
    RankingResponse ranking =
        rankings(memberId, weekStart, LeagueType.WEEKLY_RETURN, 0, Integer.MAX_VALUE);
    Map<String, RankingEntryResponse> byPublicId =
        ranking.rankings().stream()
            .collect(HashMap::new, (map, entry) -> map.put(entry.publicId(), entry), Map::putAll);
    WeeklyLeaderboard previous = finalized(weekStart.minusWeeks(1));
    Map<Long, Integer> previousRanks =
        previous == null
            ? Map.of()
            : previous.getEntries().stream()
                .collect(
                    java.util.stream.Collectors.toMap(
                        WeeklyLeaderboard.Entry::profileId, WeeklyLeaderboard.Entry::rank));
    Instant weekStartedAt = weekStart.atStartOfDay(SEOUL).toInstant();
    return new FollowSummaryListResponse(
        followRepository.findAllByFollowerIdOrderByCreatedAtDesc(memberId).stream()
            .filter(follow -> follow.getProfile().isProfilePublic())
            .map(
                follow -> {
                  InvestorProfile profile = follow.getProfile();
                  RankingEntryResponse entry = byPublicId.get(profile.getPublicId());
                  Integer previousRank =
                      profile.isLeagueEnabled() ? previousRanks.get(profile.getId()) : null;
                  SharedPortfolio portfolio =
                      portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
                  int newReviews =
                      portfolio == null || !profile.isDetailPublic()
                          ? 0
                          : Math.toIntExact(
                              reviewRepository.countPublicReviews(
                                  portfolio.getId(), weekStartedAt));
                  return new FollowSummaryResponse(
                      profile.getPublicId(),
                      profile.getDisplayName(),
                      entry == null ? null : entry.rank(),
                      entry == null ? null : entry.weeklyReturnRate(),
                      newReviews,
                      previousRank,
                      entry == null || previousRank == null ? null : previousRank - entry.rank(),
                      previous == null ? null : previous.getWeekStart());
                })
            .toList());
  }

  private RankedProfile metrics(InvestorProfile profile, LocalDate weekStart) {
    SharedPortfolio portfolio =
        portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
    if (portfolio == null) {
      return new RankedProfile(profile, null, 0, BigDecimal.ZERO);
    }
    LocalDate trackingStart = profile.getTrackingStartedOn();
    LocalDate from = trackingStart == null ? weekStart : trackingStart;
    List<PortfolioDailyReturn> returns =
        from.isAfter(weekStart.plusDays(6))
            ? List.of()
            : returnRepository.findLatestByPortfolioIdAndBaseDateBetween(
                portfolio.getId(), from, weekStart.plusDays(6));
    Metric metric = calculateMetric(profile, returns, weekStart);
    if (finalizationCalendarCache.get() != null) returns.forEach(entityManager::detach);
    List<InvestmentDecision> transactions =
        decisionRepository.findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
            portfolio.getId(), weekStart.plusDays(6));
    Map<Long, List<InvestmentReview>> reviews = reviewsByTransaction(transactions);
    long reviewed =
        transactions.stream()
            .filter(transaction -> !reviews.getOrDefault(transaction.getId(), List.of()).isEmpty())
            .count();
    BigDecimal completion =
        transactions.isEmpty()
            ? BigDecimal.ZERO.setScale(2)
            : BigDecimal.valueOf(reviewed)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(transactions.size()), 2, RoundingMode.HALF_UP);
    return new RankedProfile(profile, metric, transactions.size(), completion);
  }

  private Metric calculateMetric(
      InvestorProfile profile, List<PortfolioDailyReturn> values, LocalDate week) {
    LocalDate end = calculationEnd(week);
    if (end == null) return new Metric(null, null, null, null, null, 0, null, 0);
    values = values.stream().filter(value -> !value.getBaseDate().isAfter(end)).toList();
    Map<LocalDate, PortfolioDailyReturn> byDate =
        values.stream()
            .collect(
                HashMap::new, (map, value) -> map.put(value.getBaseDate(), value), Map::putAll);
    LocalDate start = weeklyStart(profile, week);
    List<PortfolioDailyReturn> weekly =
        values.stream().filter(value -> !value.getBaseDate().isBefore(start)).toList();
    BigDecimal weeklyRate =
        participatesInWeek(profile, week) && complete(profile, byDate, start, end)
            ? chainedReturn(weekly)
            : null;
    LocalDate started = profile.getTrackingStartedOn();
    LocalDate recentStart =
        started != null && started.isAfter(week.minusWeeks(2)) ? started : week.minusWeeks(2);
    List<PortfolioDailyReturn> recent =
        values.stream().filter(v -> !v.getBaseDate().isBefore(recentStart)).toList();
    boolean recentComplete = complete(profile, byDate, recentStart, end);
    BigDecimal threeWeek = recentComplete ? chainedReturn(recent) : null;
    BigDecimal drawdown = recentComplete && recent.size() >= 2 ? maxDrawdown(recent) : null;
    List<LocalDate> expected =
        calendar(
                started != null && started.isAfter(week.minusWeeks(7))
                    ? started
                    : week.minusWeeks(7),
                end)
            .stream()
            .filter(MarketTradingDay::isTrading)
            .filter(day -> !day.closesAt().isAfter(Instant.now()))
            .map(MarketTradingDay::getBaseDate)
            .toList();
    List<PortfolioDailyReturn> risk =
        expected.stream().skip(Math.max(0, expected.size() - 20)).map(byDate::get).toList();
    BigDecimal volatility =
        risk.size() == 20
                && risk.stream()
                    .allMatch(v -> v != null && v.isEligible() && !v.isTrackingBaseline())
            ? volatility(risk)
            : null;
    BigDecimal maxWeight =
        recentComplete
            ? recent.stream()
                .map(
                    value ->
                        value.getCurrentHoldingWeight() == null
                            ? value.getMaxHoldingWeight()
                            : value.getMaxHoldingWeight().max(value.getCurrentHoldingWeight()))
                .max(BigDecimal::compareTo)
                .orElse(null)
            : null;
    BigDecimal currentWeight =
        values.isEmpty() || !values.getLast().isEligible()
            ? null
            : values.getLast().getCurrentHoldingWeight();
    int consecutive = 0;
    for (LocalDate recordedWeek = week;
        started != null && !recordedWeek.isBefore(normalizeWeek(started));
        recordedWeek = recordedWeek.minusWeeks(1)) {
      if (!participatesInWeek(profile, recordedWeek)
          || !complete(
              profile,
              byDate,
              weeklyStart(profile, recordedWeek),
              recordedWeek.equals(week) ? end : recordedWeek.plusDays(6))) break;
      consecutive++;
    }
    return new Metric(
        weeklyRate,
        threeWeek,
        drawdown,
        volatility,
        maxWeight,
        weeklyRate == null ? 0 : weekly.size(),
        currentWeight,
        consecutive);
  }

  private boolean complete(
      InvestorProfile profile,
      Map<LocalDate, PortfolioDailyReturn> values,
      LocalDate from,
      LocalDate to) {
    LocalDate elapsedTo = to.isBefore(LocalDate.now(SEOUL)) ? to : LocalDate.now(SEOUL);
    if (from.isAfter(elapsedTo)) return false;
    List<MarketTradingDay> calendar = calendar(from, elapsedTo);
    if (calendar.size() != ChronoUnit.DAYS.between(from, elapsedTo) + 1) return false;
    List<MarketTradingDay> days =
        calendar.stream()
            .filter(MarketTradingDay::isTrading)
            .filter(day -> !day.closesAt().isAfter(Instant.now()))
            .toList();
    return !days.isEmpty()
        && days.stream()
            .allMatch(
                day ->
                    day.getCollectedAt() != null
                        && participates(profile, day.getBaseDate())
                        && values.containsKey(day.getBaseDate())
                        && values.get(day.getBaseDate()).isEligible());
  }

  private BigDecimal chainedReturn(List<PortfolioDailyReturn> returns) {
    BigDecimal index = ONE;
    for (PortfolioDailyReturn dailyReturn : returns) {
      index =
          index.multiply(
              ONE.add(dailyReturn.getDailyReturnRate().divide(HUNDRED, 10, RoundingMode.HALF_UP)));
    }
    return index.subtract(ONE).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal maxDrawdown(List<PortfolioDailyReturn> returns) {
    BigDecimal index = ONE;
    BigDecimal peak = ONE;
    BigDecimal drawdown = BigDecimal.ZERO;
    for (PortfolioDailyReturn dailyReturn : returns) {
      index =
          index.multiply(
              ONE.add(dailyReturn.getDailyReturnRate().divide(HUNDRED, 10, RoundingMode.HALF_UP)));
      peak = peak.max(index);
      BigDecimal current =
          index.subtract(peak).multiply(HUNDRED).divide(peak, 4, RoundingMode.HALF_UP);
      drawdown = drawdown.min(current);
    }
    return drawdown.setScale(2, RoundingMode.HALF_UP);
  }

  private BigDecimal volatility(List<PortfolioDailyReturn> returns) {
    double average =
        returns.stream()
            .map(PortfolioDailyReturn::getDailyReturnRate)
            .mapToDouble(BigDecimal::doubleValue)
            .average()
            .orElse(0);
    double variance =
        returns.stream()
                .map(PortfolioDailyReturn::getDailyReturnRate)
                .mapToDouble(value -> Math.pow(value.doubleValue() - average, 2))
                .sum()
            / returns.size();
    return BigDecimal.valueOf(Math.sqrt(variance)).setScale(2, RoundingMode.HALF_UP);
  }

  private Comparator<RankedProfile> comparator(LeagueType leagueType) {
    return Comparator.comparing(
            (RankedProfile ranked) -> score(ranked.metric(), leagueType), Comparator.reverseOrder())
        .thenComparing(
            ranked -> ranked.metric().maxDrawdownRate(),
            Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(
            ranked -> ranked.metric().maxHoldingWeight(),
            Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparing(ranked -> ranked.profile().getPublicId());
  }

  private BigDecimal score(Metric metric, LeagueType leagueType) {
    return switch (leagueType) {
      case STABLE -> metric.weeklyReturnRate().subtract(metric.volatilityRate());
      case WEEKLY_RETURN, BEGINNER -> metric.weeklyReturnRate();
    };
  }

  private List<Integer> ranks(List<RankedProfile> profiles, LeagueType leagueType) {
    List<Integer> ranks = new ArrayList<>(profiles.size());
    BigDecimal previousScore = null;
    int rank = 0;
    for (RankedProfile profile : profiles) {
      BigDecimal currentScore = score(profile.metric(), leagueType);
      if (previousScore == null || currentScore.compareTo(previousScore) != 0) rank++;
      ranks.add(rank);
      previousScore = currentScore;
    }
    return ranks;
  }

  private RankingEntryResponse rankingEntry(int rank, RankedProfile ranked, boolean followed) {
    InvestorProfile profile = ranked.profile();
    Metric metric = ranked.metric();
    return new RankingEntryResponse(
        rank,
        profile.getPublicId(),
        profile.getDisplayName(),
        profile.getExperienceLevel(),
        profile.getRiskProfile(),
        metric.weeklyReturnRate(),
        metric.threeWeekReturnRate(),
        metric.maxDrawdownRate(),
        metric.volatilityRate(),
        metric.maxHoldingWeight(),
        ranked.decisionCount(),
        ranked.reviewCompletionRate(),
        followed,
        profile.getMember().getProfileImageUrl(),
        profile.getTrackingStartedOn(),
        metric.currentHoldingWeight(),
        metric.consecutiveRecordedWeeks());
  }

  private PerformanceResponse performance(
      InvestorProfile profile, Metric metric, HistoryPointResponse current) {
    if (metric == null)
      return new PerformanceResponse(
          null,
          null,
          null,
          null,
          null,
          profile.getTrackingStartedOn(),
          profile.eligibleFrom(),
          null,
          0,
          null);
    return new PerformanceResponse(
        current.weeklyReturnRate(),
        metric.threeWeekReturnRate(),
        metric.maxDrawdownRate(),
        metric.volatilityRate(),
        metric.maxHoldingWeight(),
        profile.getTrackingStartedOn(),
        profile.eligibleFrom(),
        metric.currentHoldingWeight(),
        metric.consecutiveRecordedWeeks(),
        current.weeklyRank());
  }

  private AllocationResponse allocation(Long memberId) {
    SharedPortfolio portfolio = portfolioRepository.findByMemberId(memberId).orElse(null);
    if (portfolio == null) {
      return null;
    }
    BigDecimal stock = BigDecimal.ZERO;
    BigDecimal etf = BigDecimal.ZERO;
    List<MarketTradingDay> days =
        tradingDayRepository
            .findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(LocalDate.now(SEOUL))
            .stream()
            .filter(day -> day.getCollectedAt() != null)
            .toList();
    if (days.isEmpty()) return null;
    LocalDate asOf = days.getLast().getBaseDate();
    for (SharedHolding holding :
        holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId())) {
      StockDailyPrice price = findPrice(holding.getAsset().getId(), asOf);
      if (price == null) return null;
      BigDecimal amount = price.getClosePrice().multiply(BigDecimal.valueOf(holding.getQuantity()));
      if (holding.getAsset().getCategory() == AssetCategory.STOCK) {
        stock = stock.add(amount);
      } else if (holding.getAsset().getCategory() == AssetCategory.ETF) {
        etf = etf.add(amount);
      }
    }
    BigDecimal total = stock.add(etf);
    if (total.signum() == 0) {
      return null;
    }
    return new AllocationResponse(
        stock.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP),
        etf.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP));
  }

  private List<HistoryPointResponse> history(InvestorProfile profile, LocalDate currentWeek) {
    SharedPortfolio portfolio =
        portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
    LocalDate from =
        profile.getTrackingStartedOn() == null
            ? currentWeek.minusWeeks(2)
            : profile.getTrackingStartedOn();
    List<PortfolioDailyReturn> values =
        portfolio == null
            ? List.of()
            : returnRepository.findLatestByPortfolioIdAndBaseDateBetween(
                portfolio.getId(), from, currentWeek.plusDays(6));
    List<HistoryPointResponse> points = new ArrayList<>();
    for (int offset = 2; offset >= 0; offset--) {
      LocalDate week = currentWeek.minusWeeks(offset);
      Metric metric =
          calculateMetric(
              profile,
              values.stream()
                  .filter(value -> !value.getBaseDate().isAfter(week.plusDays(6)))
                  .toList(),
              week);
      WeeklyLeaderboard result = finalized(week);
      WeeklyLeaderboard.Entry saved =
          result == null
              ? null
              : result.getEntries().stream()
                  .filter(entry -> entry.profileId().equals(profile.getId()))
                  .findFirst()
                  .orElse(null);
      BigDecimal weekly = saved == null ? metric.weeklyReturnRate() : saved.weeklyReturnRate();
      Integer rank = null;
      if (profile.isLeagueEnabled()) {
        if (saved != null) rank = saved.rank();
        else if (result == null && weekly != null) {
          // ponytail: 미확정 주차는 기존 순위를 재계산한다. 참가자가 늘면 집계된 순위를 조회한다.
          List<RankedProfile> ranked = rankedProfiles(week, LeagueType.WEEKLY_RETURN, true);
          List<Integer> ranks = ranks(ranked, LeagueType.WEEKLY_RETURN);
          rank =
              IntStream.range(0, ranked.size())
                  .filter(
                      index ->
                          ranked.get(index).profile().getPublicId().equals(profile.getPublicId()))
                  .map(ranks::get)
                  .boxed()
                  .findFirst()
                  .orElse(null);
        }
      }
      DailyReturnStatus status =
          weekly != null
              ? DailyReturnStatus.CALCULATED
              : !participatesInWeek(profile, week)
                  ? DailyReturnStatus.NOT_PARTICIPATING
                  : DailyReturnStatus.PENDING;
      points.add(
          new HistoryPointResponse(
              week,
              weekly,
              saved == null ? metric.maxDrawdownRate() : saved.maxDrawdownRate(),
              status,
              saved != null,
              collectionStatus(week),
              rank));
    }
    return points;
  }

  private Map<Long, List<InvestmentReview>> reviewsByTransaction(
      List<InvestmentDecision> transactions) {
    List<Long> ids = transactions.stream().map(InvestmentDecision::getId).toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    Map<Long, List<InvestmentReview>> result = new HashMap<>();
    for (InvestmentReview review :
        reviewRepository.findAllByTransactionIdInOrderByCreatedAtAsc(ids)) {
      result
          .computeIfAbsent(review.getTransaction().getId(), ignored -> new ArrayList<>())
          .add(review);
    }
    return result;
  }

  private Set<Long> followedProfileIds(Long viewerMemberId) {
    if (viewerMemberId == null) {
      return Set.of();
    }
    Set<Long> ids = new HashSet<>();
    for (InvestorFollow follow :
        followRepository.findAllByFollowerIdOrderByCreatedAtDesc(viewerMemberId)) {
      ids.add(follow.getProfile().getId());
    }
    return ids;
  }

  private boolean isFollowed(Long viewerMemberId, Long profileId) {
    return viewerMemberId != null
        && followRepository.findByFollowerIdAndProfileId(viewerMemberId, profileId).isPresent();
  }

  private InvestorProfile getPublicProfile(String publicId) {
    return profileRepository
        .findByPublicIdAndProfilePublicTrue(publicId)
        .orElseThrow(() -> new BusinessException(LeagueErrorCode.PROFILE_NOT_FOUND));
  }

  private LocalDate normalizeWeek(LocalDate requestedWeek) {
    LocalDate date = requestedWeek == null ? LocalDate.now(SEOUL) : requestedWeek;
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  private record RankedProfile(
      InvestorProfile profile, Metric metric, int decisionCount, BigDecimal reviewCompletionRate) {}

  private record Metric(
      BigDecimal weeklyReturnRate,
      BigDecimal threeWeekReturnRate,
      BigDecimal maxDrawdownRate,
      BigDecimal volatilityRate,
      BigDecimal maxHoldingWeight,
      int weeklyDays,
      BigDecimal currentHoldingWeight,
      int consecutiveRecordedWeeks) {}

  private record PricePair(
      StockDailyPrice previous, StockDailyPrice current, BigDecimal evaluation) {}

  private record DetailSnapshot(LocalDate asOfDate, List<PublicHoldingResponse> holdings) {}
}
