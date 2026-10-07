package org.grit.daynomy.league.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestmentReview;
import org.grit.daynomy.league.domain.InvestorFollow;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes.DailyReturnStatus;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.grit.daynomy.league.domain.PortfolioTransaction;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedHoldingHistory.ChangeType;
import org.grit.daynomy.league.domain.SharedPortfolio;
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
import org.grit.daynomy.league.repository.InvestmentReviewRepository;
import org.grit.daynomy.league.repository.InvestorFollowRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.PortfolioDailyReturnRepository;
import org.grit.daynomy.league.repository.PortfolioTransactionRepository;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private final PortfolioTransactionRepository transactionRepository;
  private final InvestmentReviewRepository reviewRepository;
  private final InvestorFollowRepository followRepository;
  private final MemberRepository memberRepository;
  private final StockDailyPriceRepository priceRepository;

  @Transactional(readOnly = true)
  public WeeksResponse weeks() {
    LocalDate today = LocalDate.now(SEOUL);
    LocalDate currentWeek = normalizeWeek(today);
    List<WeekResponse> weeks =
        IntStream.range(0, 8)
            .mapToObj(currentWeek::minusWeeks)
            .map(
                start ->
                    new WeekResponse(start, start.plusDays(6), today.isAfter(start.plusDays(6))))
            .toList();
    return new WeeksResponse(weeks);
  }

  @Transactional(readOnly = true)
  public RankingResponse rankings(
      Long viewerMemberId, LocalDate requestedWeek, LeagueType leagueType, int page, int size) {
    LocalDate weekStart = normalizeWeek(requestedWeek);
    LocalDate weekEnd = weekStart.plusDays(6);
    List<RankedProfile> rankedProfiles =
        profileRepository.findAllByProfilePublicTrueAndLeagueEnabledTrue().stream()
            .filter(
                profile ->
                    leagueType != LeagueType.BEGINNER
                        || profile.getExperienceLevel() == ExperienceLevel.BEGINNER)
            .map(profile -> metrics(profile, weekStart))
            .filter(ranked -> ranked.metric() != null && ranked.metric().weeklyDays() > 0)
            .sorted(comparator(leagueType))
            .toList();
    Set<Long> followedProfileIds = followedProfileIds(viewerMemberId);
    List<RankingEntryResponse> entries =
        IntStream.range(0, rankedProfiles.size())
            .mapToObj(
                index ->
                    rankingEntry(
                        index + 1,
                        rankedProfiles.get(index),
                        followedProfileIds.contains(rankedProfiles.get(index).profile().getId())))
            .toList();
    int from = Math.min(Math.max(0, page) * size, entries.size());
    int to = Math.min(from + size, entries.size());
    return new RankingResponse(
        weekStart,
        weekEnd,
        leagueType,
        LocalDate.now(SEOUL).isAfter(weekEnd),
        entries.size(),
        entries.subList(from, to),
        tradingDates(weekStart).stream().max(LocalDate::compareTo).orElse(null));
  }

  @Transactional(readOnly = true)
  public PublicInvestorResponse publicInvestor(Long viewerMemberId, String publicId) {
    InvestorProfile profile = getPublicProfile(publicId);
    LocalDate weekStart = normalizeWeek(null);
    RankedProfile ranked = metrics(profile, weekStart);
    Metric metric = ranked.metric();
    return new PublicInvestorResponse(
        profile.getPublicId(),
        profile.getDisplayName(),
        profile.getBio(),
        profile.getExperienceLevel(),
        profile.getRiskProfile(),
        performance(metric),
        profile.isAllocationPublic() ? allocation(profile.getMember().getId()) : null,
        history(profile, weekStart),
        ranked.decisionCount(),
        ranked.reviewCompletionRate(),
        isFollowed(viewerMemberId, profile.getId()),
        profile.isDetailPublic());
  }

  @Transactional(readOnly = true)
  public DailyHistoryResponse dailyHistory(String publicId, LocalDate requestedWeek) {
    InvestorProfile profile = getPublicProfile(publicId);
    LocalDate weekStart = normalizeWeek(requestedWeek);
    LocalDate currentWeek = normalizeWeek(null);
    if (weekStart.isAfter(currentWeek) || weekStart.isBefore(currentWeek.minusWeeks(7))) {
      throw new BusinessException(LeagueErrorCode.INVALID_LEAGUE_WEEK);
    }
    LocalDate eligibleFrom =
        profile.getLeagueEnabledAt() == null
            ? null
            : profile
                .getLeagueEnabledAt()
                .atZone(SEOUL)
                .toLocalDate()
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    SharedPortfolio portfolio =
        portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
    Map<LocalDate, PortfolioDailyReturn> byDate = new HashMap<>();
    if (portfolio != null) {
      returnRepository
          .findAllByPortfolioIdAndBaseDateBetweenAndCalculationVersionOrderByBaseDate(
              portfolio.getId(), weekStart, weekStart.plusDays(6), 1)
          .forEach(value -> byDate.put(value.getBaseDate(), value));
    }
    List<DailyReturnPointResponse> days = new ArrayList<>();
    List<PortfolioDailyReturn> calculated = new ArrayList<>();
    boolean complete = true;
    LocalDate asOfDate = null;
    for (LocalDate date : tradingDates(weekStart)) {
      PortfolioDailyReturn value = byDate.get(date);
      DailyReturnStatus status;
      String reason = null;
      if (value != null && value.isEligible()) {
        status = DailyReturnStatus.CALCULATED;
        calculated.add(value);
        asOfDate = date;
      } else if (value != null) {
        status = DailyReturnStatus.EXCLUDED;
        reason = value.getIneligibleReason();
      } else if (!profile.isLeagueEnabled()
          || eligibleFrom == null
          || date.isBefore(eligibleFrom)) {
        status = DailyReturnStatus.NOT_PARTICIPATING;
      } else {
        status = DailyReturnStatus.PENDING;
      }
      complete &= status == DailyReturnStatus.CALCULATED;
      days.add(
          new DailyReturnPointResponse(
              date,
              status == DailyReturnStatus.CALCULATED ? value.getDailyReturnRate() : null,
              complete ? chainedReturn(calculated) : null,
              status,
              reason));
    }
    BigDecimal weeklyReturnRate = complete && !days.isEmpty() ? chainedReturn(calculated) : null;
    return new DailyHistoryResponse(
        weekStart,
        weekStart.plusDays(6),
        asOfDate,
        eligibleFrom,
        LocalDate.now(SEOUL).isAfter(weekStart.plusDays(6)) && weeklyReturnRate != null,
        weeklyReturnRate,
        days);
  }

  private List<LocalDate> tradingDates(LocalDate weekStart) {
    LocalDate today = LocalDate.now(SEOUL);
    return priceRepository.findDistinctBaseDatesOrderByBaseDate().stream()
        .filter(
            date ->
                !date.isBefore(weekStart)
                    && !date.isAfter(weekStart.plusDays(6))
                    && !date.isAfter(today))
        .distinct()
        .sorted()
        .toList();
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
      return new InvestorDetailResponse(publicId, cutoff, List.of(), List.of());
    }
    List<PortfolioTransaction> transactions =
        transactionRepository.findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
            portfolio.getId(), cutoff);
    Map<Long, List<InvestmentReview>> reviews = reviewsByTransaction(transactions);
    DetailSnapshot snapshot = detailSnapshot(portfolio.getId(), cutoff);
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

  private DetailSnapshot detailSnapshot(Long portfolioId, LocalDate cutoff) {
    List<LocalDate> dates =
        priceRepository.findDistinctBaseDatesOrderByBaseDate().stream()
            .filter(date -> !date.isAfter(cutoff))
            .toList();
    LocalDate asOfDate = dates.isEmpty() ? cutoff : dates.getLast();
    Map<Long, BigDecimal> contributions = weeklyContributions(portfolioId, dates, asOfDate);
    List<SharedHolding> holdings = holdingRepository.findAllByPortfolioIdOrderById(portfolioId);
    Map<Long, BigDecimal> evaluations = new LinkedHashMap<>();
    BigDecimal total = BigDecimal.ZERO;
    for (SharedHolding holding : holdings) {
      StockDailyPrice price = findPrice(holding.getAsset().getId(), asOfDate);
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
                        pricesComplete && dates.size() > 1
                            ? contributions
                                .getOrDefault(holding.getAsset().getId(), BigDecimal.ZERO)
                                .setScale(2, RoundingMode.HALF_UP)
                            : null,
                        holding.getReason()))
            .toList();
    return new DetailSnapshot(asOfDate, responses);
  }

  private Map<Long, BigDecimal> weeklyContributions(
      Long portfolioId, List<LocalDate> availableDates, LocalDate asOfDate) {
    LocalDate weekStart = asOfDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    Map<Long, BigDecimal> contributions = new LinkedHashMap<>();
    for (int index = 1; index < availableDates.size(); index++) {
      LocalDate baseDate = availableDates.get(index);
      if (baseDate.isBefore(weekStart) || baseDate.isAfter(asOfDate)) {
        continue;
      }
      addDailyContributions(portfolioId, availableDates.get(index - 1), baseDate, contributions);
    }
    return contributions;
  }

  private void addDailyContributions(
      Long portfolioId,
      LocalDate previousDate,
      LocalDate baseDate,
      Map<Long, BigDecimal> contributions) {
    List<HoldingAtClose> holdings = reconstructHoldings(portfolioId, baseDate);
    Map<Long, PricePair> prices = new LinkedHashMap<>();
    BigDecimal total = BigDecimal.ZERO;
    for (HoldingAtClose holding : holdings) {
      StockDailyPrice previous = findPrice(holding.asset().getId(), previousDate);
      StockDailyPrice current = findPrice(holding.asset().getId(), baseDate);
      if (previous == null || current == null) {
        continue;
      }
      BigDecimal evaluation =
          previous.getClosePrice().multiply(BigDecimal.valueOf(holding.quantity()));
      prices.put(holding.asset().getId(), new PricePair(previous, current, evaluation));
      total = total.add(evaluation);
    }
    if (total.signum() == 0) {
      return;
    }
    for (Map.Entry<Long, PricePair> entry : prices.entrySet()) {
      PricePair pair = entry.getValue();
      BigDecimal weight = pair.evaluation().divide(total, 10, RoundingMode.HALF_UP);
      BigDecimal assetReturn =
          pair.current()
              .getClosePrice()
              .subtract(pair.previous().getClosePrice())
              .divide(pair.previous().getClosePrice(), 10, RoundingMode.HALF_UP);
      contributions.merge(
          entry.getKey(), weight.multiply(assetReturn).multiply(HUNDRED), BigDecimal::add);
    }
  }

  private List<HoldingAtClose> reconstructHoldings(Long portfolioId, LocalDate baseDate) {
    Map<Long, HoldingAtClose> holdings = new LinkedHashMap<>();
    List<SharedHoldingHistory> histories =
        holdingHistoryRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            portfolioId, baseDate.atStartOfDay(SEOUL).toInstant());
    for (SharedHoldingHistory history : histories) {
      Long assetId = history.getAsset().getId();
      if (history.getChangeType() == ChangeType.REMOVED) {
        holdings.remove(assetId);
      } else {
        holdings.put(
            assetId,
            new HoldingAtClose(
                history.getAsset(),
                history.getQuantity(),
                history.isHidden(),
                history.getReason()));
      }
    }
    return List.copyOf(holdings.values());
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
    RankingResponse ranking = rankings(memberId, weekStart, LeagueType.WEEKLY_RETURN, 0, 100);
    Map<String, RankingEntryResponse> byPublicId =
        ranking.rankings().stream()
            .collect(HashMap::new, (map, entry) -> map.put(entry.publicId(), entry), Map::putAll);
    Instant weekStartedAt = weekStart.atStartOfDay(SEOUL).toInstant();
    return new FollowSummaryListResponse(
        followRepository.findAllByFollowerIdOrderByCreatedAtDesc(memberId).stream()
            .filter(follow -> follow.getProfile().isProfilePublic())
            .map(
                follow -> {
                  InvestorProfile profile = follow.getProfile();
                  RankingEntryResponse entry = byPublicId.get(profile.getPublicId());
                  SharedPortfolio portfolio =
                      portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
                  int newReviews =
                      portfolio == null
                          ? 0
                          : Math.toIntExact(
                              reviewRepository
                                  .countByTransactionPortfolioIdAndCreatedAtGreaterThanEqual(
                                      portfolio.getId(), weekStartedAt));
                  return new FollowSummaryResponse(
                      profile.getPublicId(),
                      profile.getDisplayName(),
                      entry == null ? null : entry.rank(),
                      entry == null ? null : entry.weeklyReturnRate(),
                      newReviews);
                })
            .toList());
  }

  private RankedProfile metrics(InvestorProfile profile, LocalDate weekStart) {
    SharedPortfolio portfolio =
        portfolioRepository.findByMemberId(profile.getMember().getId()).orElse(null);
    if (portfolio == null) {
      return new RankedProfile(profile, null, 0, BigDecimal.ZERO);
    }
    LocalDate historyStart = weekStart.minusWeeks(7);
    List<PortfolioDailyReturn> returns =
        returnRepository.findAllByPortfolioIdAndBaseDateBetweenAndEligibleTrueOrderByBaseDate(
            portfolio.getId(), historyStart, weekStart.plusDays(6));
    List<PortfolioDailyReturn> weeklyReturns =
        returns.stream().filter(value -> !value.getBaseDate().isBefore(weekStart)).toList();
    long expectedDays = tradingDates(weekStart).size();
    // 가격 누락으로 제외된 하루를 빼고 유리한 날만 순위에 표시하지 않는다.
    Metric metric =
        expectedDays == weeklyReturns.size() ? calculateMetric(returns, weeklyReturns) : null;
    List<PortfolioTransaction> transactions =
        transactionRepository.findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
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
    int notes =
        Math.toIntExact(
            holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId()).stream()
                .filter(h -> !h.getReason().isBlank())
                .count());
    return new RankedProfile(profile, metric, transactions.size() + notes, completion);
  }

  private Metric calculateMetric(
      List<PortfolioDailyReturn> history, List<PortfolioDailyReturn> weeklyReturns) {
    if (weeklyReturns.isEmpty()) {
      return null;
    }
    BigDecimal weekly = chainedReturn(weeklyReturns);
    BigDecimal eightWeek = chainedReturn(history);
    BigDecimal maxDrawdown = maxDrawdown(history);
    BigDecimal volatility = volatility(history);
    BigDecimal maxWeight =
        weeklyReturns.stream()
            .map(PortfolioDailyReturn::getMaxHoldingWeight)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
    return new Metric(weekly, eightWeek, maxDrawdown, volatility, maxWeight, weeklyReturns.size());
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
    if (returns.size() < 2) {
      return BigDecimal.ZERO.setScale(2);
    }
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
        .thenComparing(ranked -> ranked.metric().maxDrawdownRate(), Comparator.reverseOrder())
        .thenComparing(ranked -> ranked.metric().maxHoldingWeight())
        .thenComparing(ranked -> ranked.profile().getPublicId());
  }

  private BigDecimal score(Metric metric, LeagueType leagueType) {
    return switch (leagueType) {
      case CONSISTENT ->
          metric
              .eightWeekReturnRate()
              .subtract(metric.maxDrawdownRate().abs())
              .subtract(metric.volatilityRate());
      case STABLE -> metric.weeklyReturnRate().subtract(metric.volatilityRate());
      case WEEKLY_RETURN, BEGINNER -> metric.weeklyReturnRate();
    };
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
        metric.eightWeekReturnRate(),
        metric.maxDrawdownRate(),
        metric.volatilityRate(),
        metric.maxHoldingWeight(),
        ranked.decisionCount(),
        ranked.reviewCompletionRate(),
        followed);
  }

  private PerformanceResponse performance(Metric metric) {
    if (metric == null) {
      return new PerformanceResponse(null, null, null, null, null);
    }
    return new PerformanceResponse(
        metric.weeklyReturnRate(),
        metric.eightWeekReturnRate(),
        metric.maxDrawdownRate(),
        metric.volatilityRate(),
        metric.maxHoldingWeight());
  }

  private AllocationResponse allocation(Long memberId) {
    SharedPortfolio portfolio = portfolioRepository.findByMemberId(memberId).orElse(null);
    if (portfolio == null) {
      return new AllocationResponse(BigDecimal.ZERO, BigDecimal.ZERO);
    }
    BigDecimal stock = BigDecimal.ZERO;
    BigDecimal etf = BigDecimal.ZERO;
    for (SharedHolding holding :
        holdingRepository.findAllByPortfolioIdOrderById(portfolio.getId())) {
      StockDailyPrice price =
          priceRepository
              .findFirstByAssetIdOrderByBaseDateDesc(holding.getAsset().getId())
              .orElse(null);
      if (price == null) {
        continue;
      }
      BigDecimal amount = price.getClosePrice().multiply(BigDecimal.valueOf(holding.getQuantity()));
      if (holding.getAsset().getCategory() == AssetCategory.STOCK) {
        stock = stock.add(amount);
      } else if (holding.getAsset().getCategory() == AssetCategory.ETF) {
        etf = etf.add(amount);
      }
    }
    BigDecimal total = stock.add(etf);
    if (total.signum() == 0) {
      return new AllocationResponse(BigDecimal.ZERO, BigDecimal.ZERO);
    }
    return new AllocationResponse(
        stock.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP),
        etf.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP));
  }

  private List<HistoryPointResponse> history(InvestorProfile profile, LocalDate currentWeek) {
    List<HistoryPointResponse> points = new ArrayList<>();
    for (int offset = 7; offset >= 0; offset--) {
      LocalDate weekStart = currentWeek.minusWeeks(offset);
      RankedProfile ranked = metrics(profile, weekStart);
      if (ranked.metric() != null) {
        points.add(
            new HistoryPointResponse(
                weekStart, ranked.metric().weeklyReturnRate(), ranked.metric().maxDrawdownRate()));
      }
    }
    return points;
  }

  private Map<Long, List<InvestmentReview>> reviewsByTransaction(
      List<PortfolioTransaction> transactions) {
    List<Long> ids = transactions.stream().map(PortfolioTransaction::getId).toList();
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
      BigDecimal eightWeekReturnRate,
      BigDecimal maxDrawdownRate,
      BigDecimal volatilityRate,
      BigDecimal maxHoldingWeight,
      int weeklyDays) {}

  private record HoldingAtClose(
      org.grit.daynomy.asset.domain.Asset asset, long quantity, boolean hidden, String reason) {}

  private record PricePair(
      StockDailyPrice previous, StockDailyPrice current, BigDecimal evaluation) {}

  private record DetailSnapshot(LocalDate asOfDate, List<PublicHoldingResponse> holdings) {}
}
