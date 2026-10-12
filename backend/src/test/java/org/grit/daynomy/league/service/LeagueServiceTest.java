package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestorFollow;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedHoldingHistory.ChangeType;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.domain.WeeklyLeaderboard;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentReviewRepository;
import org.grit.daynomy.league.repository.InvestorFollowRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.PortfolioDailyReturnRepository;
import org.grit.daynomy.league.repository.PortfolioTransactionRepository;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.league.repository.WeeklyLeaderboardRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeagueServiceTest {

  @Mock private InvestorProfileRepository profileRepository;
  @Mock private SharedPortfolioRepository portfolioRepository;
  @Mock private SharedHoldingRepository holdingRepository;
  @Mock private SharedHoldingHistoryRepository holdingHistoryRepository;
  @Mock private PortfolioDailyReturnRepository returnRepository;
  @Mock private PortfolioTransactionRepository transactionRepository;
  @Mock private InvestmentReviewRepository reviewRepository;
  @Mock private InvestorFollowRepository followRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private StockDailyPriceRepository priceRepository;
  @Mock private MarketTradingDayRepository tradingDayRepository;
  @Mock private WeeklyLeaderboardRepository leaderboardRepository;
  @Mock private LeagueCalculationMetrics calculationMetrics;
  @InjectMocks private LeagueService service;

  @Test
  void followComparisonUsesPreviousFinalRankAndNeverInventsMissingOrWithdrawnRanks() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY);
    dailyProfile(week.minusWeeks(2));
    var profile = profileRepository.findByPublicIdAndProfilePublicTrue("investor-1").orElseThrow();
    profile.changePublication(true, true, false, true, Instant.now());
    given(profileRepository.findById(1L)).willReturn(Optional.of(profile));
    given(followRepository.findAllByFollowerIdOrderByCreatedAtDesc(99L))
        .willReturn(List.of(new InvestorFollow(mock(Member.class), profile)));
    given(
            reviewRepository.countPublicReviews(
                3L, week.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant()))
        .willReturn(2L);
    for (int offset = 0; offset < 2; offset++) {
      LocalDate start = week.minusWeeks(offset);
      var entry =
          new WeeklyLeaderboard.Entry(
              offset == 0 ? 8 : 10,
              1L,
              new BigDecimal("2.5"),
              null,
              null,
              null,
              null,
              null,
              null,
              0,
              0,
              BigDecimal.ZERO,
              start);
      given(
              leaderboardRepository.findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(
                  start, LeagueTypes.LeagueType.WEEKLY_RETURN))
          .willReturn(
              Optional.of(
                  new WeeklyLeaderboard(
                      start,
                      LeagueTypes.LeagueType.WEEKLY_RETURN,
                      1,
                      start,
                      "INITIAL_FINALIZATION",
                      List.of(entry))));
    }
    var summary = service.followSummary(99L).investors().getFirst();
    assertThat(summary.rank()).isEqualTo(8);
    assertThat(summary.previousRank()).isEqualTo(10);
    assertThat(summary.rankChange()).isEqualTo(2);
    assertThat(summary.comparisonWeekStart()).isEqualTo(week.minusWeeks(1));
    assertThat(summary.newReviewCount()).isEqualTo(2);
    given(
            leaderboardRepository.findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(
                week.minusWeeks(1), LeagueTypes.LeagueType.WEEKLY_RETURN))
        .willReturn(Optional.empty());
    assertThat(service.followSummary(99L).investors().getFirst().rankChange()).isNull();
    profile.changePublication(true, false, false, false, Instant.now());
    summary = service.followSummary(99L).investors().getFirst();
    assertThat(summary.rank()).isNull();
    assertThat(summary.previousRank()).isNull();
    assertThat(summary.newReviewCount()).isZero();
    profile.changePublication(false, false, false, false, Instant.now());
    assertThat(service.followSummary(99L).investors()).isEmpty();
  }

  @Test
  void calendarCorrectionVersionsAnUnchangedRankWhenTheFinalTradingDateChanges() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY).minusWeeks(1);
    SharedPortfolio portfolio = dailyProfile(week.minusWeeks(1));
    var profile = profileRepository.findByPublicIdAndProfilePublicTrue("investor-1").orElseThrow();
    given(profileRepository.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));
    given(
            tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(
                week.minusWeeks(1), week.minusDays(1)))
        .willReturn(List.of());
    given(
            tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(
                week.plusWeeks(1), week.plusWeeks(1).plusDays(6)))
        .willReturn(List.of());
    calendar(week, List.of(week, week.plusDays(1)));
    var excluded = new PortfolioDailyReturn(portfolio, week);
    excluded.exclude("NO_HOLDINGS", Instant.now());
    var next = new PortfolioDailyReturn(portfolio, week.plusDays(1));
    next.exclude("NO_HOLDINGS", Instant.now());
    given(returnRepository.findLatestByPortfolioIdAndBaseDateBetween(3L, week, week.plusDays(6)))
        .willReturn(List.of(excluded, next));
    given(
            returnRepository.findLatestByPortfolioIdAndBaseDateBetween(
                3L, week.minusWeeks(1), week.plusDays(6)))
        .willReturn(List.of(excluded, next));
    given(
            tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(
                week.minusWeeks(1), week.plusDays(6)))
        .willReturn(List.of());
    given(
            leaderboardRepository.findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(
                week, LeagueTypes.LeagueType.WEEKLY_RETURN))
        .willReturn(
            Optional.of(
                new WeeklyLeaderboard(
                    week,
                    LeagueTypes.LeagueType.WEEKLY_RETURN,
                    1,
                    week.plusDays(2),
                    "INITIAL_FINALIZATION",
                    List.of())));
    service.finalizeWeeks();
    var saved = org.mockito.ArgumentCaptor.forClass(WeeklyLeaderboard.class);
    then(leaderboardRepository).should(org.mockito.Mockito.times(3)).save(saved.capture());
    assertThat(saved.getAllValues())
        .extracting(WeeklyLeaderboard::getLeagueType)
        .containsExactlyInAnyOrder(
            LeagueTypes.LeagueType.WEEKLY_RETURN,
            LeagueTypes.LeagueType.STABLE,
            LeagueTypes.LeagueType.BEGINNER);
    var weekly =
        saved.getAllValues().stream()
            .filter(value -> value.getLeagueType() == LeagueTypes.LeagueType.WEEKLY_RETURN)
            .findFirst()
            .orElseThrow();
    assertThat(weekly.getCalculationVersion()).isEqualTo(2);
    assertThat(weekly.getAsOfDate()).isEqualTo(week.plusDays(1));
    assertThat(weekly.getChangeReason()).isEqualTo("CALENDAR_RECALCULATION");
    then(calculationMetrics).should().finalizationState(7, 0, null);
  }

  @Test
  void dailyHistoryCompoundsReturnsAndResetsAtEachMondayWithoutWeekendPoints() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY).minusWeeks(2);
    SharedPortfolio portfolio = dailyProfile(week.minusWeeks(1));
    var monday = dailyReturn(portfolio, week, "10");
    var tuesday = dailyReturn(portfolio, week.plusDays(1), "-10");
    var nextMonday = dailyReturn(portfolio, week.plusWeeks(1), "2");
    calendar(week, List.of(week, week.plusDays(1)));
    calendar(week.plusWeeks(1), List.of(week.plusWeeks(1)));
    given(
            leaderboardRepository.findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(
                week, LeagueTypes.LeagueType.WEEKLY_RETURN))
        .willReturn(
            Optional.of(
                new WeeklyLeaderboard(
                    week,
                    LeagueTypes.LeagueType.WEEKLY_RETURN,
                    1,
                    week.plusDays(1),
                    "INITIAL_FINALIZATION",
                    List.of(
                        new WeeklyLeaderboard.Entry(
                            1,
                            1L,
                            new BigDecimal("-1"),
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            0,
                            0,
                            BigDecimal.ZERO,
                            week)))));
    given(returnRepository.findLatestByPortfolioIdAndBaseDateBetween(3L, week, week.plusDays(6)))
        .willReturn(List.of(monday, tuesday));
    given(
            returnRepository.findLatestByPortfolioIdAndBaseDateBetween(
                3L, week.plusWeeks(1), week.plusWeeks(1).plusDays(6)))
        .willReturn(List.of(nextMonday));

    var previous = service.dailyHistory("investor-1", week);
    var current = service.dailyHistory("investor-1", week.plusWeeks(1));

    assertThat(previous.days()).hasSize(2);
    assertThat(previous.days().getFirst().cumulativeReturnRate()).isEqualByComparingTo("10");
    assertThat(previous.weeklyReturnRate()).isEqualByComparingTo("-1");
    assertThat(previous.asOfDate()).isEqualTo(week.plusDays(1));
    assertThat(previous.confirmed()).isTrue();
    assertThat(current.weeklyReturnRate()).isEqualByComparingTo("2");
    assertThat(current.confirmed()).isFalse();
    assertThat(monday.getDailyReturnRate()).isEqualByComparingTo("10");
    given(returnRepository.findLatestByPortfolioIdAndBaseDateBetween(3L, week, week.plusDays(6)))
        .willReturn(List.of(dailyReturn(portfolio, week, "20"), tuesday));
    assertThat(service.dailyHistory("investor-1", week).confirmed()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void missingOrExcludedDayNeverBecomesZeroOrGetsSkippedInTheCumulativeRate(boolean excluded) {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY).minusWeeks(1);
    SharedPortfolio portfolio = dailyProfile(week.minusWeeks(1));
    var monday = dailyReturn(portfolio, week, "10");
    var wednesday = dailyReturn(portfolio, week.plusDays(2), "5");
    var missing = new PortfolioDailyReturn(portfolio, week.plusDays(1));
    missing.exclude("MISSING_PRICE", Instant.now());
    calendar(week, List.of(week, week.plusDays(1), week.plusDays(2)));
    given(returnRepository.findLatestByPortfolioIdAndBaseDateBetween(3L, week, week.plusDays(6)))
        .willReturn(excluded ? List.of(monday, missing, wednesday) : List.of(monday, wednesday));

    var result = service.dailyHistory("investor-1", week);

    assertThat(result.days()).hasSize(3);
    assertThat(result.days().get(1).dailyReturnRate()).isNull();
    assertThat(result.days().get(1).status())
        .isEqualTo(
            excluded
                ? LeagueTypes.DailyReturnStatus.EXCLUDED
                : LeagueTypes.DailyReturnStatus.PENDING);
    assertThat(result.days().get(2).dailyReturnRate()).isEqualByComparingTo("5");
    assertThat(result.days().get(2).cumulativeReturnRate()).isNull();
    assertThat(result.weeklyReturnRate()).isNull();
    assertThat(result.confirmed()).isFalse();
  }

  @Test
  void participationStartsNextMondayAndMissingPricesDoNotInventDates() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY).minusWeeks(1);
    dailyProfile(week);
    calendar(week, List.of(week));

    var result = service.dailyHistory("investor-1", week);

    assertThat(result.eligibleFrom()).isEqualTo(week.plusWeeks(1));
    assertThat(result.days().getFirst().status()).isEqualTo(LeagueTypes.DailyReturnStatus.PENDING);
    assertThat(result.weeklyReturnRate()).isNull();
    assertThat(result.asOfDate()).isNull();
    given(tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(week, week.plusDays(6)))
        .willReturn(List.of());
    assertThat(service.dailyHistory("investor-1", week).days()).isEmpty();
  }

  @Test
  void unknownCalendarKeepsEnrolledWeeksPendingInsteadOfShowingNonParticipation() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY);
    dailyProfile(week.minusWeeks(1));

    var daily = service.dailyHistory("investor-1", week);
    var history = service.publicInvestor(null, "investor-1").history().getLast();

    assertThat(daily.days()).isEmpty();
    assertThat(daily.collectionStatus()).isEqualTo(LeagueTypes.CollectionStatus.CALENDAR_PENDING);
    assertThat(history.status()).isEqualTo(LeagueTypes.DailyReturnStatus.PENDING);
    assertThat(history.collectionStatus()).isEqualTo(LeagueTypes.CollectionStatus.CALENDAR_PENDING);
    assertThat(history.weeklyReturnRate()).isNull();
  }

  @Test
  void onlyRecentEightWeeksAndPublicProfilesCanBeRead() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY);
    given(profileRepository.findByPublicIdAndProfilePublicTrue("investor-1"))
        .willReturn(Optional.of(mock(InvestorProfile.class)));
    for (LocalDate invalid : List.of(week.plusWeeks(1), week.minusWeeks(8))) {
      assertThatThrownBy(() -> service.dailyHistory("investor-1", invalid))
          .isInstanceOf(BusinessException.class)
          .extracting(exception -> ((BusinessException) exception).errorCode())
          .isEqualTo(LeagueErrorCode.INVALID_LEAGUE_WEEK);
    }
    assertThatThrownBy(() -> service.dailyHistory("private-investor", week))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(LeagueErrorCode.PROFILE_NOT_FOUND);
    assertThat(service.weeks().weeks()).hasSize(8);
    assertThat(service.weeks().weeks().getFirst().weekStart()).isEqualTo(week);
  }

  @Test
  void wholeCumulativeIncludesOlderWeeksAndMissingDaysAreNeverDroppedFromRisk(
      @Mock(answer = Answers.CALLS_REAL_METHODS) MockedStatic<LocalDate> dates) {
    LocalDate today = LocalDate.of(2026, 10, 8);
    dates.when(() -> LocalDate.now(ZoneId.of("Asia/Seoul"))).thenReturn(today);
    LocalDate currentWeek = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY);
    LocalDate started = currentWeek.minusWeeks(10);
    SharedPortfolio portfolio = dailyProfile(started.minusWeeks(1));
    InvestorProfile profile =
        profileRepository.findByPublicIdAndProfilePublicTrue("investor-1").orElseThrow();
    profile.startTracking(started);
    // dailyProfile set an earlier baseline for old tests; this case starts at the first supplied
    // close.
    org.springframework.test.util.ReflectionTestUtils.setField(
        profile, "trackingStartedOn", started);
    java.util.Set<LocalDate> holidays =
        java.util.Set.of(
            LocalDate.of(2026, 8, 17),
            LocalDate.of(2026, 9, 24),
            LocalDate.of(2026, 9, 25),
            LocalDate.of(2026, 10, 5),
            LocalDate.of(2026, 10, 9));
    given(
            tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(
                any(LocalDate.class), any(LocalDate.class)))
        .willAnswer(
            call -> {
              LocalDate from = call.getArgument(0);
              LocalDate to = call.getArgument(1);
              return from.datesUntil(to.plusDays(1))
                  .map(
                      date -> {
                        var day =
                            new MarketTradingDay(
                                date,
                                date.getDayOfWeek().getValue() < 6 && !holidays.contains(date));
                        if (day.isTrading() && !day.closesAt().isAfter(Instant.now()))
                          day.collected(Instant.now());
                        return day;
                      })
                  .toList();
            });
    List<PortfolioDailyReturn> values = new ArrayList<>();
    for (LocalDate date :
        started.datesUntil(LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1)).toList()) {
      if (date.getDayOfWeek().getValue() >= 6
          || holidays.contains(date)
          || new MarketTradingDay(date, true).closesAt().isAfter(Instant.now())) continue;
      values.add(dailyReturn(portfolio, date, values.isEmpty() ? "0" : "1"));
    }
    for (var value : values) {
      value.complete(
          value.getDailyReturnRate(),
          value.getStartingEvaluationAmount(),
          value.getEndingEvaluationAmount(),
          new BigDecimal("50"),
          new BigDecimal("50"),
          Instant.now());
    }
    var latest = values.getLast();
    latest.complete(
        latest.getDailyReturnRate(),
        latest.getStartingEvaluationAmount(),
        latest.getEndingEvaluationAmount(),
        new BigDecimal("50"),
        new BigDecimal("75"),
        Instant.now());
    given(
            returnRepository.findLatestByPortfolioIdAndBaseDateBetween(
                3L, started, currentWeek.plusDays(6)))
        .willReturn(values);
    given(profileRepository.findAllByProfilePublicTrueAndLeagueEnabledTrue())
        .willReturn(List.of(profile));
    var performance = service.publicInvestor(null, "investor-1").performance();
    assertThat(performance.currentHoldingWeight()).isEqualByComparingTo("75");
    assertThat(performance.maxHoldingWeight()).isEqualByComparingTo("75");
    assertThat(performance.cumulativeReturnRate()).isGreaterThan(performance.eightWeekReturnRate());
    assertThat(performance.volatilityRate()).isEqualByComparingTo("0.00");
    assertThat(performance.consecutiveRecordedWeeks()).isGreaterThan(8);
    values.get(values.size() - 8).exclude("MISSING_PRICE", Instant.now());
    var missing = service.publicInvestor(null, "investor-1").performance();
    assertThat(missing.cumulativeReturnRate()).isNull();
    assertThat(missing.eightWeekReturnRate()).isNull();
    assertThat(missing.maxDrawdownRate()).isNull();
    assertThat(missing.volatilityRate()).isNull();
  }

  private MarketTradingDay tradingDay(LocalDate date) {
    var day = new MarketTradingDay(date, true);
    day.collected(date.atTime(17, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant());
    return day;
  }

  private void calendar(LocalDate week, List<LocalDate> open) {
    List<MarketTradingDay> calendar = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      LocalDate date = week.plusDays(i);
      calendar.add(open.contains(date) ? tradingDay(date) : new MarketTradingDay(date, false));
    }
    given(tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(week, week.plusDays(6)))
        .willReturn(calendar);
  }

  private SharedPortfolio dailyProfile(LocalDate enabledOn) {
    Member owner = mock(Member.class);
    given(owner.getId()).willReturn(2L);
    InvestorProfile profile =
        InvestorProfile.create(
            owner,
            "공개 별명",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    org.springframework.test.util.ReflectionTestUtils.setField(profile, "id", 1L);
    profile.changePublication(
        true, true, false, false, enabledOn.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
    profile.startTracking(enabledOn);
    given(profileRepository.findByPublicIdAndProfilePublicTrue("investor-1"))
        .willReturn(Optional.of(profile));
    SharedPortfolio portfolio = mock(SharedPortfolio.class);
    given(portfolio.getId()).willReturn(3L);
    given(portfolioRepository.findByMemberId(2L)).willReturn(Optional.of(portfolio));
    return portfolio;
  }

  private PortfolioDailyReturn dailyReturn(SharedPortfolio portfolio, LocalDate date, String rate) {
    var value = new PortfolioDailyReturn(portfolio, date);
    value.complete(
        new BigDecimal(rate),
        new BigDecimal("100000"),
        new BigDecimal("110000"),
        new BigDecimal("100"),
        new BigDecimal("100"),
        Instant.now());
    return value;
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void publicDetailsCompoundContributionsAndHonorHiddenAssets(boolean hidden) {
    InvestorProfile profile = mock(InvestorProfile.class);
    Member owner = mock(Member.class);
    SharedPortfolio portfolio = mock(SharedPortfolio.class);
    Asset asset = mock(Asset.class);
    SharedHoldingHistory history = mock(SharedHoldingHistory.class);
    StockDailyPrice previousPrice = mock(StockDailyPrice.class);
    StockDailyPrice currentPrice = mock(StockDailyPrice.class);
    LocalDate previousDate = LocalDate.of(2026, 9, 21);
    LocalDate currentDate = LocalDate.of(2026, 9, 22);

    given(profileRepository.findByPublicIdAndProfilePublicTrue("investor-1"))
        .willReturn(Optional.of(profile));
    given(profile.isDetailPublic()).willReturn(true);
    given(profile.getMember()).willReturn(owner);
    given(owner.getId()).willReturn(2L);
    given(portfolioRepository.findByMemberId(2L)).willReturn(Optional.of(portfolio));
    given(portfolio.getId()).willReturn(3L);
    given(
            transactionRepository
                .findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
                    eq(3L), any(LocalDate.class)))
        .willReturn(List.of());
    LocalDate earlierDate = previousDate.minusDays(3);
    given(
            tradingDayRepository.findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(
                previousDate))
        .willReturn(Optional.of(tradingDay(earlierDate)));
    given(priceRepository.findByAssetIdAndBaseDate(4L, earlierDate))
        .willReturn(Optional.of(new StockDailyPrice(asset, earlierDate, new BigDecimal("100"))));
    var previousDay = tradingDay(previousDate);
    var currentDay = tradingDay(currentDate);
    given(
            tradingDayRepository
                .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                    any(LocalDate.class)))
        .willReturn(Optional.of(currentDay));
    calendar(previousDate, List.of(previousDate, currentDate));
    var baseline = dailyReturn(portfolio, previousDate, "10");
    var currentReturn = dailyReturn(portfolio, currentDate, "-10");
    given(returnRepository.findLatestByPortfolioIdAndBaseDateBetween(3L, previousDate, currentDate))
        .willReturn(List.of(baseline, currentReturn));
    given(
            tradingDayRepository.findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(
                currentDate))
        .willReturn(Optional.of(previousDay));
    given(
            holdingHistoryRepository
                .findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                    eq(3L), any(Instant.class)))
        .willReturn(List.of(history));
    given(history.getAsset()).willReturn(asset);
    given(history.getChangeType()).willReturn(ChangeType.ADDED);
    given(history.getQuantity()).willReturn(10L);
    given(asset.getId()).willReturn(4L);
    if (!hidden) {
      given(asset.getName()).willReturn("삼성전자");
      given(asset.getCategory()).willReturn(AssetCategory.STOCK);
    }
    SharedHolding visible = mock(SharedHolding.class);
    given(visible.isHidden()).willReturn(hidden);
    given(visible.getAsset()).willReturn(asset);
    given(visible.getQuantity()).willReturn(10L);
    if (!hidden) given(visible.getAveragePurchasePrice()).willReturn(new BigDecimal("100"));
    given(holdingRepository.findAllByPortfolioIdOrderById(3L)).willReturn(List.of(visible));
    given(priceRepository.findByAssetIdAndBaseDate(4L, previousDate))
        .willReturn(Optional.of(previousPrice));
    given(priceRepository.findByAssetIdAndBaseDate(4L, currentDate))
        .willReturn(Optional.of(currentPrice));
    given(previousPrice.getClosePrice()).willReturn(new BigDecimal("110"));
    given(currentPrice.getClosePrice()).willReturn(new BigDecimal("99"));

    var response = service.investorDetail("investor-1");

    assertThat(response.asOfDate()).isEqualTo(currentDate);
    if (hidden) {
      assertThat(response.holdings()).isEmpty();
    } else {
      assertThat(response.holdings())
          .singleElement()
          .satisfies(
              holding -> {
                assertThat(holding.assetName()).isEqualTo("삼성전자");
                assertThat(holding.weight()).isEqualByComparingTo("100.00");
                assertThat(holding.weeklyContributionRate()).isEqualByComparingTo("-1.00");
              });
    }
    then(transactionRepository)
        .should()
        .findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
            eq(3L), any(LocalDate.class));
  }
}
