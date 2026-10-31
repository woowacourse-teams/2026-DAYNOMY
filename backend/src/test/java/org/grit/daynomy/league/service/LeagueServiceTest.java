package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.asset.service.StockPriceService;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.InvestorFollow;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueParticipation;
import org.grit.daynomy.league.domain.LeagueTypes;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.grit.daynomy.league.domain.SharedHoldingHistory.ChangeType;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.domain.WeeklyLeaderboard;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.InvestmentDecisionCounts;
import org.grit.daynomy.league.repository.InvestmentDecisionRepository;
import org.grit.daynomy.league.repository.InvestmentReviewRepository;
import org.grit.daynomy.league.repository.InvestorFollowRepository;
import org.grit.daynomy.league.repository.InvestorProfileRepository;
import org.grit.daynomy.league.repository.PortfolioDailyReturnRepository;
import org.grit.daynomy.league.repository.RankingDailyReturn;
import org.grit.daynomy.league.repository.RankingInvestor;
import org.grit.daynomy.league.repository.SharedHoldingHistoryRepository;
import org.grit.daynomy.league.repository.SharedHoldingRepository;
import org.grit.daynomy.league.repository.SharedPortfolioRepository;
import org.grit.daynomy.league.repository.WeeklyLeaderboardRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class LeagueServiceTest {

  @Mock private InvestorProfileRepository profileRepository;
  @Mock private SharedPortfolioRepository portfolioRepository;
  @Mock private SharedHoldingRepository holdingRepository;
  @Mock private SharedHoldingHistoryRepository holdingHistoryRepository;
  @Mock private PortfolioDailyReturnRepository returnRepository;
  @Mock private InvestmentDecisionRepository decisionRepository;
  @Mock private InvestmentReviewRepository reviewRepository;
  @Mock private InvestorFollowRepository followRepository;
  @Mock private MemberRepository memberRepository;
  @Mock private StockDailyPriceRepository priceRepository;
  @Mock private StockPriceService stockPriceService;
  @Mock private MarketTradingDayRepository tradingDayRepository;
  @Mock private WeeklyLeaderboardRepository leaderboardRepository;
  @Mock private LeagueCalculationMetrics calculationMetrics;
  @Mock private EntityManager entityManager;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private LeagueAggregationService aggregationService;
  @Spy private LeagueRankingCache rankingCache = new LeagueRankingCache(mock(TaskScheduler.class));
  @InjectMocks private LeagueService service;

  @BeforeEach
  void transactions() {
    lenient()
        .when(transactionManager.getTransaction(any()))
        .thenAnswer(invocation -> new SimpleTransactionStatus());
  }

  @Test
  void finalizationReadsEachProfileOutsideTheAccumulatingWritePersistenceContext() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY).minusWeeks(1);
    var member = mock(Member.class);
    given(member.getId()).willReturn(2L);
    var profile =
        InvestorProfile.create(
            member,
            "확정 경계",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    profile.changePublication(
        true, true, false, false, week.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
    given(profileRepository.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));
    given(tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(week, week.plusDays(6)))
        .willReturn(
            week.datesUntil(week.plusDays(7))
                .map(
                    date -> {
                      var day = new MarketTradingDay(date, date.getDayOfWeek().getValue() < 6);
                      if (day.isTrading()) day.collected(Instant.now());
                      return day;
                    })
                .toList());

    service.finalizeWeeks(week);

    var definitions = org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);
    then(transactionManager).should(times(2)).getTransaction(definitions.capture());
    assertThat(definitions.getAllValues())
        .allSatisfy(
            definition -> {
              assertThat(definition.isReadOnly()).isTrue();
              assertThat(definition.getPropagationBehavior())
                  .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void publicWeightsIncludeHiddenHoldingsAndRemainNullWhenTheirPriceIsMissing(boolean missing) {
    var profile = mock(InvestorProfile.class);
    var owner = mock(Member.class);
    var portfolio = mock(SharedPortfolio.class);
    var visibleAsset = mock(Asset.class);
    var hiddenAsset = mock(Asset.class);
    given(profileRepository.findByPublicIdAndProfilePublicTrue("owner"))
        .willReturn(Optional.of(profile));
    given(profile.isDetailPublic()).willReturn(true);
    given(profile.getMember()).willReturn(owner);
    given(owner.getId()).willReturn(2L);
    given(portfolioRepository.findByMemberId(2L)).willReturn(Optional.of(portfolio));
    given(portfolio.getId()).willReturn(3L);
    given(visibleAsset.getId()).willReturn(4L);
    given(visibleAsset.getDisplayName()).willReturn("공개 종목");
    given(visibleAsset.getCategory()).willReturn(AssetCategory.STOCK);
    given(hiddenAsset.getId()).willReturn(5L);
    var visible = new SharedHolding(portfolio, visibleAsset, 1, new BigDecimal("100"));
    var hidden = new SharedHolding(portfolio, hiddenAsset, 1, new BigDecimal("300"));
    hidden.changeVisibility(true);
    given(holdingRepository.findAllByPortfolioIdOrderById(3L)).willReturn(List.of(visible, hidden));
    var date = LocalDate.of(2026, 10, 13);
    given(
            tradingDayRepository
                .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                    any(LocalDate.class)))
        .willReturn(Optional.of(tradingDay(date)));
    var first = new StockDailyPrice(visibleAsset, date, new BigDecimal("100"));
    given(stockPriceService.pricesAt(List.of(4L, 5L), date))
        .willReturn(
            missing
                ? Map.of(4L, first)
                : Map.of(
                    4L, first, 5L, new StockDailyPrice(hiddenAsset, date, new BigDecimal("300"))));
    var response = service.investorDetail("owner");
    assertThat(response.holdings())
        .singleElement()
        .satisfies(
            holding -> {
              assertThat(holding.assetName()).isEqualTo("공개 종목");
              if (missing) assertThat(holding.weight()).isNull();
              else assertThat(holding.weight()).isEqualByComparingTo("25.00");
            });
    then(stockPriceService).should().pricesAt(List.of(4L, 5L), date);
    then(priceRepository).shouldHaveNoInteractions();
  }

  @Test
  void oldFinalizedEntryKeepsItsEightWeekValueWithoutCallingItThreeWeeks() throws Exception {
    var entry =
        new ObjectMapper()
            .readValue(
                """
                {"rank":1,"profileId":2,"weeklyReturnRate":1.5,"eightWeekReturnRate":4.2}
                """,
                WeeklyLeaderboard.Entry.class);

    assertThat(entry.eightWeekReturnRate()).isEqualByComparingTo("4.2");
    assertThat(entry.threeWeekReturnRate()).isNull();
  }

  @Test
  void finalizationDelayWaitsForNextTradingDayPublicationAndMissingCalendarIsReported(
      @Mock(answer = Answers.CALLS_REAL_METHODS) MockedStatic<LocalDate> dates,
      @Mock(answer = Answers.CALLS_REAL_METHODS) MockedStatic<Instant> instants) {
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    LocalDate friday = LocalDate.of(2026, 9, 18);
    LocalDate week = friday.with(DayOfWeek.MONDAY);
    LocalDate tuesday = friday.plusDays(4);
    dates.when(() -> LocalDate.now(seoul)).thenReturn(friday);
    Instant now = friday.atTime(18, 0).atZone(seoul).toInstant();
    instants.when(Instant::now).thenReturn(now);
    InvestorProfile profile =
        InvestorProfile.create(
            mock(Member.class),
            "공개 별명",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    profile.changePublication(
        true, true, false, false, week.minusDays(3).atStartOfDay(seoul).toInstant());
    given(profileRepository.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));
    var calendar =
        week.datesUntil(week.plusDays(7))
            .map(date -> new MarketTradingDay(date, date.getDayOfWeek().getValue() < 6))
            .toList();
    given(
            tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(
                any(LocalDate.class), any(LocalDate.class)))
        .willAnswer(call -> week.equals(call.getArgument(0)) ? calendar : List.of());
    given(
            tradingDayRepository.findFirstByTradingTrueAndBaseDateGreaterThanOrderByBaseDateAsc(
                friday))
        .willReturn(Optional.of(new MarketTradingDay(tuesday, true)));

    service.finalizeWeeks();

    then(calculationMetrics)
        .should()
        .finalizationState(7, 1, tuesday.atTime(14, 0).atZone(seoul).toInstant());
    given(
            tradingDayRepository.findFirstByTradingTrueAndBaseDateGreaterThanOrderByBaseDateAsc(
                friday))
        .willReturn(Optional.empty());
    service.finalizeWeeks();
    then(calculationMetrics).should().finalizationState(8, 1, null);
  }

  @Test
  void incrementalFinalizationSkipsOlderWeeksAndKeepsTheirPendingMetrics() {
    LocalDate current = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY);
    LocalDate older = current.minusWeeks(1);
    InvestorProfile profile =
        InvestorProfile.create(
            mock(Member.class),
            "공개 별명",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    profile.changePublication(
        true,
        true,
        false,
        false,
        older.minusWeeks(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
    given(profileRepository.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));

    service.finalizeWeeks();
    clearInvocations(tradingDayRepository, calculationMetrics);
    service.finalizeWeeks(current);

    then(tradingDayRepository)
        .should(never())
        .findAllByBaseDateBetweenOrderByBaseDate(older, older.plusDays(6));
    then(tradingDayRepository)
        .should(times(1))
        .findAllByBaseDateBetweenOrderByBaseDate(current, current.plusDays(6));
    then(calculationMetrics).should().finalizationState(21, 0, null);
  }

  @Test
  void rolledBackFinalizationRescansOlderWeeksOnNextRun() {
    LocalDate current = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY);
    LocalDate older = current.minusWeeks(1);
    InvestorProfile profile =
        InvestorProfile.create(
            mock(Member.class),
            "공개 별명",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    profile.changePublication(
        true,
        true,
        false,
        false,
        older.minusWeeks(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
    given(profileRepository.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));

    TransactionSynchronizationManager.initSynchronization();
    try {
      service.finalizeWeeks();
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(
              synchronization ->
                  synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
    clearInvocations(tradingDayRepository);

    service.finalizeWeeks(current);

    then(tradingDayRepository)
        .should()
        .findAllByBaseDateBetweenOrderByBaseDate(older, older.plusDays(6));
  }

  @Test
  void followComparisonUsesPreviousFinalRankAndNeverInventsMissingOrWithdrawnRanks() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY);
    dailyProfile(week.minusWeeks(2));
    var profile = profileRepository.findByPublicIdAndProfilePublicTrue("investor-1").orElseThrow();
    profile.changePublication(true, true, false, true, Instant.now());
    rankingPageProfiles(List.of(profile));
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
  void pastFinalizedWeekIsNotRewrittenByCalendarCorrection() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY).minusWeeks(1);
    var profile =
        InvestorProfile.create(
            mock(Member.class),
            "기존 참가자",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    profile.changePublication(
        true,
        true,
        false,
        false,
        week.minusWeeks(1).atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
    given(profileRepository.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));
    var finalized =
        new WeeklyLeaderboard(
            week,
            LeagueTypes.LeagueType.WEEKLY_RETURN,
            1,
            week.plusDays(2),
            "INITIAL_FINALIZATION",
            List.of());
    lenient()
        .when(
            leaderboardRepository.findFirstByWeekStartAndLeagueTypeOrderByCalculationVersionDesc(
                week, LeagueTypes.LeagueType.WEEKLY_RETURN))
        .thenReturn(Optional.of(finalized));

    service.finalizeWeeks();

    then(leaderboardRepository).should(never()).save(any());
    assertThat(finalized.getCalculationVersion()).isEqualTo(1);
  }

  @Test
  void missingMondaySnapshotKeepsWeeklyReturnPendingEvenWhenTuesdayWasCalculated() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY).minusWeeks(1);
    SharedPortfolio portfolio = dailyProfile(week.plusDays(2));
    InvestorProfile profile =
        profileRepository.findByPublicIdAndProfilePublicTrue("investor-1").orElseThrow();
    profile
        .getParticipations()
        .getFirst()
        .includeCurrentWeekFrom(week.atTime(15, 30).atZone(ZoneId.of("Asia/Seoul")).toInstant());
    profile.startTracking(week.plusDays(1));
    var monday = new PortfolioDailyReturn(portfolio, week);
    monday.exclude("SNAPSHOT_PENDING", Instant.now());
    var tuesday = dailyReturn(portfolio, week.plusDays(1), "10");
    calendar(week, List.of(week, week.plusDays(1)));
    given(returnRepository.findLatestByPortfolioIdAndBaseDateBetween(3L, week, week.plusDays(6)))
        .willReturn(List.of(monday, tuesday));

    var response = service.dailyHistory("investor-1", week);

    assertThat(response.weeklyReturnRate()).isNull();
    assertThat(response.eligibleFrom()).isEqualTo(week);
    assertThat(response.days().getFirst().status())
        .isEqualTo(LeagueTypes.DailyReturnStatus.PENDING);
    assertThat(response.days().getFirst().reason()).isEqualTo("SNAPSHOT_PENDING");
    assertThat(response.days().getLast().cumulativeReturnRate()).isNull();
  }

  @Test
  void newParticipantEntersRankingsAsSoonAsAResultExists(
      @Mock(answer = Answers.CALLS_REAL_METHODS) MockedStatic<LocalDate> dates,
      @Mock(answer = Answers.CALLS_REAL_METHODS) MockedStatic<Instant> instants) {
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    LocalDate week = LocalDate.of(2026, 9, 21);
    LocalDate enrolled = week.plusDays(2);
    dates.when(() -> LocalDate.now(seoul)).thenReturn(enrolled);
    Instant enrolledClose = enrolled.atTime(18, 0).atZone(seoul).toInstant();
    instants.when(Instant::now).thenReturn(enrolledClose);
    SharedPortfolio portfolio = dailyProfile(enrolled);
    InvestorProfile profile =
        profileRepository.findByPublicIdAndProfilePublicTrue("investor-1").orElseThrow();
    given(profileRepository.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));
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
                        var day = new MarketTradingDay(date, date.getDayOfWeek().getValue() < 6);
                        if (day.isTrading() && !day.closesAt().isAfter(Instant.now()))
                          day.collected(Instant.now());
                        return day;
                      })
                  .toList();
            });
    List<PortfolioDailyReturn> values = new ArrayList<>();
    var baseline = new PortfolioDailyReturn(portfolio, enrolled);
    baseline.completeBaseline(new BigDecimal("100"), new BigDecimal("100"), Instant.now());
    values.add(baseline);
    given(
            returnRepository.findLatestByPortfolioIdAndBaseDateBetween(
                eq(3L), any(LocalDate.class), any(LocalDate.class)))
        .willAnswer(
            call ->
                values.stream()
                    .filter(
                        value ->
                            !value.getBaseDate().isBefore(call.getArgument(1, LocalDate.class)))
                    .filter(
                        value -> !value.getBaseDate().isAfter(call.getArgument(2, LocalDate.class)))
                    .toList());

    rankingInputs(List.of(profile), List.of(portfolio), java.util.Map.of(3L, values));
    var first = service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20);
    assertThat(first.confirmed()).isFalse();
    assertThat(first.asOfDate()).isEqualTo(enrolled);
    assertThat(first.rankings()).hasSize(1);
    assertThat(first.rankings().getFirst().weeklyReturnRate()).isZero();
    var daily = service.dailyHistory("investor-1", week);
    assertThat(daily.days()).hasSize(7);
    assertThat(daily.days().getFirst().status())
        .isEqualTo(LeagueTypes.DailyReturnStatus.NOT_PARTICIPATING);
    assertThat(daily.days().get(2).reason()).isEqualTo("TRACKING_BASELINE");
    assertThat(daily.weeklyReturnRate()).isZero();

    for (int offset = 1; offset <= 2; offset++) {
      LocalDate date = enrolled.plusDays(offset);
      dates.when(() -> LocalDate.now(seoul)).thenReturn(date);
      Instant close = date.atTime(18, 0).atZone(seoul).toInstant();
      instants.when(Instant::now).thenReturn(close);
      values.add(dailyReturn(portfolio, date, "10"));
      rankingCache.invalidateAfterCommit();
      String expected = offset == 1 ? "10" : "21";
      assertThat(
              service
                  .rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20)
                  .rankings()
                  .getFirst()
                  .weeklyReturnRate())
          .isEqualByComparingTo(expected);
      assertThat(service.dailyHistory("investor-1", week).weeklyReturnRate())
          .isEqualByComparingTo(expected);
    }
    service.finalizeWeeks();
    var saved = org.mockito.ArgumentCaptor.forClass(WeeklyLeaderboard.class);
    then(leaderboardRepository).should(org.mockito.Mockito.times(3)).save(saved.capture());
    var finalWeek =
        saved.getAllValues().stream()
            .filter(value -> value.getLeagueType() == LeagueTypes.LeagueType.WEEKLY_RETURN)
            .findFirst()
            .orElseThrow();
    assertThat(finalWeek.getEntries()).hasSize(1);
    assertThat(finalWeek.getEntries().getFirst().weeklyReturnRate()).isEqualByComparingTo("21");

    LocalDate nextMonday = week.plusWeeks(1);
    dates.when(() -> LocalDate.now(seoul)).thenReturn(nextMonday);
    Instant nextClose = nextMonday.atTime(18, 0).atZone(seoul).toInstant();
    instants.when(Instant::now).thenReturn(nextClose);
    values.add(dailyReturn(portfolio, nextMonday, "-5"));
    assertThat(
            service
                .rankings(null, nextMonday, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20)
                .rankings()
                .getFirst()
                .weeklyReturnRate())
        .isEqualByComparingTo("-5");
    assertThat(service.dailyHistory("investor-1", nextMonday).weeklyReturnRate())
        .isEqualByComparingTo("-5");
    assertThat(finalWeek.getEntries()).hasSize(1);
    assertThat(finalWeek.getEntries().getFirst().weeklyReturnRate()).isEqualByComparingTo("21");
  }

  @Test
  void equalWeeklyReturnsShareDenseRanksInLiveAndFinalizedResults() {
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    LocalDate week = LocalDate.now(seoul).with(DayOfWeek.MONDAY).minusWeeks(1);
    List<InvestorProfile> profiles = new ArrayList<>();
    java.util.Map<Long, List<PortfolioDailyReturn>> returns = new java.util.HashMap<>();
    List<SharedPortfolio> portfolios = new ArrayList<>();
    for (int index = 0; index < 4; index++) {
      long memberId = index + 1L;
      long portfolioId = index + 10L;
      Member owner = mock(Member.class);
      given(owner.getId()).willReturn(memberId);
      InvestorProfile profile =
          InvestorProfile.create(
              owner,
              "투자자" + index,
              "",
              LeagueTypes.ExperienceLevel.BEGINNER,
              LeagueTypes.RiskProfile.BALANCED);
      org.springframework.test.util.ReflectionTestUtils.setField(profile, "id", memberId);
      profile.changePublication(
          true, true, false, false, week.minusDays(3).atStartOfDay(seoul).toInstant());
      profile.startTracking(week);
      profiles.add(profile);
      SharedPortfolio portfolio = mock(SharedPortfolio.class);
      given(portfolio.getId()).willReturn(portfolioId);
      given(portfolioRepository.findByMemberId(memberId)).willReturn(Optional.of(portfolio));
      List<PortfolioDailyReturn> values = new ArrayList<>();
      var baseline = new PortfolioDailyReturn(portfolio, week);
      baseline.completeBaseline(new BigDecimal("100"), new BigDecimal("100"), Instant.now());
      values.add(baseline);
      for (int day = 1; day < 5; day++) {
        values.add(dailyReturn(portfolio, week.plusDays(day), index == 3 && day == 4 ? "-1" : "0"));
      }
      returns.put(portfolioId, values);
      portfolios.add(portfolio);
    }
    given(profileRepository.findAllByLeagueEnabledAtIsNotNull()).willReturn(profiles);
    given(
            returnRepository.findLatestByPortfolioIdAndBaseDateBetween(
                any(Long.class), any(LocalDate.class), any(LocalDate.class)))
        .willAnswer(
            call ->
                returns.get(call.getArgument(0, Long.class)).stream()
                    .filter(
                        value ->
                            !value.getBaseDate().isBefore(call.getArgument(1, LocalDate.class)))
                    .filter(
                        value -> !value.getBaseDate().isAfter(call.getArgument(2, LocalDate.class)))
                    .toList());
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
                        var day = new MarketTradingDay(date, date.getDayOfWeek().getValue() < 6);
                        if (day.isTrading() && !day.closesAt().isAfter(Instant.now()))
                          day.collected(Instant.now());
                        return day;
                      })
                  .toList();
            });

    rankingInputs(profiles, portfolios, returns);
    assertThat(service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20).rankings())
        .extracting(value -> value.rank())
        .containsExactly(1, 1, 1, 2);
    service.finalizeWeeks();
    var saved = org.mockito.ArgumentCaptor.forClass(WeeklyLeaderboard.class);
    then(leaderboardRepository).should(times(3)).save(saved.capture());
    assertThat(
            saved.getAllValues().stream()
                .filter(value -> value.getWeekStart().equals(week))
                .filter(value -> value.getLeagueType() == LeagueTypes.LeagueType.WEEKLY_RETURN)
                .findFirst()
                .orElseThrow()
                .getEntries())
        .extracting(WeeklyLeaderboard.Entry::rank)
        .containsExactly(1, 1, 1, 2);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 4, 11})
  void pendingClosesAndEarlierGapsRetainTheLastCompletedRankingDate(
      int daysAfterThursday,
      @Mock(answer = Answers.CALLS_REAL_METHODS) MockedStatic<LocalDate> dates,
      @Mock(answer = Answers.CALLS_REAL_METHODS) MockedStatic<Instant> instants) {
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    LocalDate week = LocalDate.of(2026, 9, 21);
    LocalDate wednesday = week.plusDays(2);
    LocalDate thursday = week.plusDays(3);
    LocalDate friday = week.plusDays(4);
    LocalDate today = thursday.plusDays(daysAfterThursday);
    dates.when(() -> LocalDate.now(seoul)).thenReturn(today);
    Instant close = today.atTime(18, 0).atZone(seoul).toInstant();
    instants.when(Instant::now).thenReturn(close);
    SharedPortfolio portfolio = dailyProfile(week.minusDays(3));
    InvestorProfile profile =
        profileRepository.findByPublicIdAndProfilePublicTrue("investor-1").orElseThrow();
    given(profileRepository.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));
    List<LocalDate> collected = new ArrayList<>(List.of(week, week.plusDays(1), wednesday));
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
                        var day = new MarketTradingDay(date, date.getDayOfWeek().getValue() < 6);
                        if (collected.contains(date)) day.collected(Instant.now());
                        return day;
                      })
                  .toList();
            });
    var baseline = new PortfolioDailyReturn(portfolio, week);
    baseline.completeBaseline(new BigDecimal("100"), new BigDecimal("100"), Instant.now());
    var pending = new PortfolioDailyReturn(portfolio, thursday);
    pending.exclude("PRICE_COLLECTION_PENDING", Instant.now());
    List<PortfolioDailyReturn> values =
        new ArrayList<>(
            List.of(
                baseline,
                dailyReturn(portfolio, week.plusDays(1), "10"),
                dailyReturn(portfolio, wednesday, "10"),
                pending));
    given(
            returnRepository.findLatestByPortfolioIdAndBaseDateBetween(
                eq(3L), any(LocalDate.class), any(LocalDate.class)))
        .willAnswer(
            call ->
                values.stream()
                    .filter(
                        value ->
                            !value.getBaseDate().isBefore(call.getArgument(1, LocalDate.class)))
                    .filter(
                        value -> !value.getBaseDate().isAfter(call.getArgument(2, LocalDate.class)))
                    .toList());

    rankingInputs(List.of(profile), List.of(portfolio), java.util.Map.of(3L, values));
    var ranking = service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20);
    assertThat(ranking.confirmed()).isFalse();
    assertThat(ranking.collectionStatus())
        .isEqualTo(
            daysAfterThursday == 0
                ? LeagueTypes.CollectionStatus.IN_PROGRESS
                : LeagueTypes.CollectionStatus.COLLECTION_PENDING);
    assertThat(ranking.asOfDate()).isEqualTo(wednesday);
    assertThat(ranking.rankings()).hasSize(1);
    assertThat(ranking.rankings().getFirst().weeklyReturnRate()).isEqualByComparingTo("21");
    var history = service.dailyHistory("investor-1", week);
    assertThat(history.confirmed()).isFalse();
    assertThat(history.collectionStatus()).isEqualTo(ranking.collectionStatus());
    assertThat(history.asOfDate()).isEqualTo(wednesday);
    assertThat(history.weeklyReturnRate()).isEqualByComparingTo("21");
    assertThat(history.days().get(3).status()).isEqualTo(LeagueTypes.DailyReturnStatus.EXCLUDED);

    service.finalizeWeeks();
    then(leaderboardRepository).should(never()).save(any());

    LocalDate nextDay = friday.plusDays(daysAfterThursday);
    dates.when(() -> LocalDate.now(seoul)).thenReturn(nextDay);
    Instant fridayClose = nextDay.atTime(18, 0).atZone(seoul).toInstant();
    instants.when(Instant::now).thenReturn(fridayClose);
    collected.add(friday);
    values.add(dailyReturn(portfolio, friday, "5"));
    rankingCache.invalidateAfterCommit();
    var retained = service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20);
    assertThat(retained.asOfDate()).isEqualTo(wednesday);
    assertThat(retained.rankings())
        .singleElement()
        .satisfies(row -> assertThat(row.weeklyReturnRate()).isEqualByComparingTo("21"));
    var personal = service.dailyHistory("investor-1", week);
    assertThat(personal.asOfDate()).isEqualTo(wednesday);
    assertThat(personal.weeklyReturnRate()).isEqualByComparingTo("21");
    assertThat(personal.days().get(4).cumulativeReturnRate()).isNull();
  }

  @Test
  void dailyHistoryIncludesSevenDaysAndCarriesCumulativeReturnsAcrossClosures() {
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

    assertThat(previous.days()).hasSize(7);
    assertThat(previous.days().subList(2, 7))
        .allSatisfy(
            day -> {
              assertThat(day.reason()).isEqualTo("MARKET_CLOSED");
              assertThat(day.dailyReturnRate()).isZero();
              assertThat(day.cumulativeReturnRate()).isEqualByComparingTo("-1");
            });
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

    assertThat(result.days()).hasSize(7);
    assertThat(result.days().get(1).dailyReturnRate()).isNull();
    assertThat(result.days().get(1).status())
        .isEqualTo(
            excluded
                ? LeagueTypes.DailyReturnStatus.EXCLUDED
                : LeagueTypes.DailyReturnStatus.PENDING);
    assertThat(result.days().get(2).dailyReturnRate()).isEqualByComparingTo("5");
    assertThat(result.days().get(2).cumulativeReturnRate()).isNull();
    assertThat(result.weeklyReturnRate()).isEqualByComparingTo("10");
    assertThat(result.asOfDate()).isEqualTo(week);
    assertThat(result.confirmed()).isFalse();
  }

  @Test
  void participationStartsOnEnrollmentAndMissingPricesDoNotInventDates(
      @Mock(answer = Answers.CALLS_REAL_METHODS) MockedStatic<LocalDate> dates,
      @Mock(answer = Answers.CALLS_REAL_METHODS) MockedStatic<Instant> instants) {
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    LocalDate today = LocalDate.of(2026, 10, 8);
    LocalDate week = today.with(DayOfWeek.MONDAY);
    dates.when(() -> LocalDate.now(seoul)).thenReturn(today);
    Instant now = today.atTime(18, 0).atZone(seoul).toInstant();
    instants.when(Instant::now).thenReturn(now);
    dailyProfile(week);
    calendar(week, List.of(week));

    var result = service.dailyHistory("investor-1", week);

    assertThat(result.eligibleFrom()).isEqualTo(week);
    assertThat(result.days().getFirst().status()).isEqualTo(LeagueTypes.DailyReturnStatus.PENDING);
    assertThat(result.weeklyReturnRate()).isNull();
    assertThat(result.asOfDate()).isNull();
    given(tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(week, week.plusDays(6)))
        .willReturn(List.of());
    assertThat(service.dailyHistory("investor-1", week).days())
        .hasSize(7)
        .filteredOn(day -> !"FUTURE_DATE".equals(day.reason()))
        .allSatisfy(day -> assertThat(day.reason()).isEqualTo("CALENDAR_PENDING"));
  }

  @Test
  void unknownCalendarKeepsEnrolledWeeksPendingInsteadOfShowingNonParticipation() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY);
    dailyProfile(week.minusWeeks(1));

    var daily = service.dailyHistory("investor-1", week);
    var history = service.publicInvestor(null, "investor-1").history().getLast();

    assertThat(daily.days()).hasSize(7);
    assertThat(daily.days().getFirst().reason()).isEqualTo("CALENDAR_PENDING");
    assertThat(daily.collectionStatus()).isEqualTo(LeagueTypes.CollectionStatus.CALENDAR_PENDING);
    assertThat(history.status()).isEqualTo(LeagueTypes.DailyReturnStatus.PENDING);
    assertThat(history.collectionStatus()).isEqualTo(LeagueTypes.CollectionStatus.CALENDAR_PENDING);
    assertThat(history.weeklyReturnRate()).isNull();
  }

  @Test
  void onlyRecentThreeWeeksAndPublicProfilesCanBeRead() {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY);
    given(profileRepository.findByPublicIdAndProfilePublicTrue("investor-1"))
        .willReturn(Optional.of(mock(InvestorProfile.class)));
    for (LocalDate invalid : List.of(week.plusWeeks(1), week.minusWeeks(3))) {
      assertThatThrownBy(() -> service.dailyHistory("investor-1", invalid))
          .isInstanceOf(BusinessException.class)
          .extracting(exception -> ((BusinessException) exception).errorCode())
          .isEqualTo(LeagueErrorCode.INVALID_LEAGUE_WEEK);
    }
    assertThatThrownBy(() -> service.dailyHistory("private-investor", week))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(LeagueErrorCode.PROFILE_NOT_FOUND);
    var weeks = service.weeks().weeks();
    assertThat(weeks)
        .extracting(value -> value.weekStart())
        .containsExactly(week, week.minusWeeks(1), week.minusWeeks(2));
    assertThat(weeks)
        .allSatisfy(value -> assertThat(value.weekEnd()).isEqualTo(value.weekStart().plusDays(6)));
  }

  @Test
  void missingDaysAreNeverDroppedFromRecentRiskMetrics(
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
    var olderLoss =
        values.stream()
            .filter(value -> value.getBaseDate().equals(currentWeek.minusWeeks(4)))
            .findFirst()
            .orElseThrow();
    olderLoss.complete(
        new BigDecimal("-50"),
        olderLoss.getStartingEvaluationAmount(),
        olderLoss.getEndingEvaluationAmount(),
        new BigDecimal("50"),
        new BigDecimal("50"),
        Instant.now());
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
    rankingInputs(List.of(profile), List.of(portfolio), java.util.Map.of(3L, values));
    var performance = service.publicInvestor(null, "investor-1").performance();
    Integer expectedRank =
        values.stream().anyMatch(value -> !value.getBaseDate().isBefore(currentWeek)) ? 1 : null;
    assertThat(performance.weeklyRank()).isEqualTo(expectedRank);
    assertThat(performance.currentHoldingWeight()).isEqualByComparingTo("75");
    assertThat(performance.maxHoldingWeight()).isEqualByComparingTo("75");
    assertThat(performance.threeWeekReturnRate()).isPositive();
    assertThat(performance.maxDrawdownRate()).isZero();
    assertThat(performance.volatilityRate()).isEqualByComparingTo("0.00");
    if (expectedRank == null) {
      assertThat(performance.consecutiveRecordedWeeks()).isZero();
    } else {
      assertThat(performance.consecutiveRecordedWeeks()).isGreaterThan(8);
    }
    values.get(values.size() - 8).exclude("MISSING_PRICE", Instant.now());
    var missing = service.publicInvestor(null, "investor-1").performance();
    assertThat(missing.threeWeekReturnRate()).isNull();
    assertThat(missing.maxDrawdownRate()).isNull();
    assertThat(missing.volatilityRate()).isNull();
  }

  @Test
  void rankingsLoadStatisticsOnlyForTheSelectedPageAndPreserveGlobalRanksAndEmptyPages() {
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    LocalDate week = LocalDate.now(seoul).with(DayOfWeek.MONDAY).minusWeeks(1);
    List<InvestorProfile> profiles = new ArrayList<>();
    List<SharedPortfolio> portfolios = new ArrayList<>();
    Map<Long, List<PortfolioDailyReturn>> returns = new java.util.HashMap<>();
    for (int index = 0; index < 3; index++) {
      Member owner = mock(Member.class);
      given(owner.getId()).willReturn(index + 1L);
      InvestorProfile profile =
          InvestorProfile.create(
              owner,
              "investor-" + index,
              "",
              LeagueTypes.ExperienceLevel.BEGINNER,
              LeagueTypes.RiskProfile.BALANCED);
      org.springframework.test.util.ReflectionTestUtils.setField(profile, "id", index + 1L);
      profile.changePublication(
          true, true, false, false, week.minusDays(3).atStartOfDay(seoul).toInstant());
      profile.startTracking(week);
      profiles.add(profile);
      SharedPortfolio portfolio = mock(SharedPortfolio.class);
      given(portfolio.getId()).willReturn(index + 10L);
      portfolios.add(portfolio);
      var baseline = new PortfolioDailyReturn(portfolio, week);
      baseline.completeBaseline(BigDecimal.TEN, BigDecimal.TEN, Instant.now());
      returns.put(portfolio.getId(), List.of(baseline));
    }
    var earlierParticipation =
        new LeagueParticipation(
            profiles.getFirst(), week.minusWeeks(2).atStartOfDay(seoul).toInstant());
    earlierParticipation.end(week.minusWeeks(1).atStartOfDay(seoul).toInstant());
    profiles.getFirst().getParticipations().add(earlierParticipation);
    rankingInputs(profiles, portfolios, returns);
    var days = calendar(week, List.of(week));
    given(
            tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(
                week.minusWeeks(7), week.plusDays(6)))
        .willReturn(days);
    var ordered =
        profiles.stream()
            .sorted(java.util.Comparator.comparing(InvestorProfile::getPublicId))
            .toList();
    Long selectedMember = ordered.get(1).getMember().getId();
    given(ordered.get(1).getMember().getProfileImageUrl())
        .willReturn("https://example.invalid/selected.png");
    var counts = mock(InvestmentDecisionCounts.class);
    given(counts.getMemberId()).willReturn(selectedMember);
    given(counts.getDecisionCount()).willReturn(3L);
    given(counts.getReviewedCount()).willReturn(1L);
    given(
            decisionRepository.findCountsByMemberIdInAndTradedOnLessThanEqual(
                List.of(selectedMember), week.plusDays(6)))
        .willReturn(List.of(counts));

    var response = service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 1, 1);

    assertThat(response.totalCount()).isEqualTo(3);
    assertThat(response.rankings()).hasSize(1);
    var entry = response.rankings().getFirst();
    assertThat(entry.publicId()).isEqualTo(ordered.get(1).getPublicId());
    assertThat(entry.profileImageUrl()).isEqualTo("https://example.invalid/selected.png");
    assertThat(entry.rank()).isEqualTo(1);
    assertThat(entry.decisionCount()).isEqualTo(3);
    assertThat(entry.reviewCompletionRate()).isEqualByComparingTo("33.33");
    then(decisionRepository)
        .should()
        .findCountsByMemberIdInAndTradedOnLessThanEqual(List.of(selectedMember), week.plusDays(6));

    var withoutDecisions =
        service
            .rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 2, 1)
            .rankings()
            .getFirst();
    assertThat(withoutDecisions.decisionCount()).isZero();
    assertThat(withoutDecisions.reviewCompletionRate()).isEqualByComparingTo("0.00");
    var beyondLastPage = service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 3, 1);
    assertThat(beyondLastPage.totalCount()).isEqualTo(3);
    assertThat(beyondLastPage.rankings()).isEmpty();
    then(decisionRepository)
        .should(times(2))
        .findCountsByMemberIdInAndTradedOnLessThanEqual(any(), eq(week.plusDays(6)));

    given(followRepository.findAllByFollowerIdOrderByCreatedAtDesc(99L))
        .willReturn(List.of(new InvestorFollow(mock(Member.class), ordered.get(1))));
    assertThat(
            service
                .rankings(99L, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 1, 1)
                .rankings()
                .getFirst()
                .followed())
        .isTrue();
    assertThat(
            service
                .rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 1, 1)
                .rankings()
                .getFirst()
                .followed())
        .isFalse();

    ordered.get(1).changePublication(false, false, false, false, Instant.now());
    ordered
        .getFirst()
        .changeProfile(
            "변경된 별명",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.CONSERVATIVE);
    var afterWithdrawal = service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20);
    assertThat(afterWithdrawal.totalCount()).isEqualTo(2);
    assertThat(afterWithdrawal.rankings())
        .extracting(value -> value.publicId())
        .doesNotContain(ordered.get(1).getPublicId());
    assertThat(afterWithdrawal.rankings().getFirst().displayName()).isEqualTo("변경된 별명");
    then(profileRepository).should(times(1)).findRankingInvestors();
  }

  private void rankingPageProfiles(List<InvestorProfile> profiles) {
    lenient()
        .when(profileRepository.findNonPublicRankingProfileIds())
        .thenAnswer(
            call ->
                profiles.stream()
                    .filter(profile -> !profile.isProfilePublic() || !profile.isLeagueEnabled())
                    .map(InvestorProfile::getId)
                    .toList());
    lenient()
        .when(profileRepository.findPublicRankingProfileIds())
        .thenAnswer(
            call ->
                profiles.stream()
                    .filter(InvestorProfile::isProfilePublic)
                    .filter(InvestorProfile::isLeagueEnabled)
                    .map(InvestorProfile::getId)
                    .toList());
    lenient()
        .when(profileRepository.findPublicRankingProfilesByIdIn(any()))
        .thenAnswer(
            call ->
                profiles.stream()
                    .filter(InvestorProfile::isProfilePublic)
                    .filter(InvestorProfile::isLeagueEnabled)
                    .filter(profile -> call.getArgument(0, List.class).contains(profile.getId()))
                    .map(
                        profile ->
                            new RankingInvestor(
                                profile.getId(),
                                profile.memberId(),
                                profile.getPublicId(),
                                profile.getDisplayName(),
                                profile.getExperienceLevel(),
                                profile.getRiskProfile(),
                                profile.getTrackingStartedOn(),
                                null,
                                null,
                                null,
                                profile.getMember().getProfileImageUrl()))
                    .toList());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "ABSENT",
        "NO_RETURNS",
        "PRICE_COLLECTION_PENDING",
        "SNAPSHOT_PENDING",
        "NO_HOLDINGS",
        "MISSING_PRICE",
        "COMPLETE"
      })
  void rankingsPassPendingCalculationDateFromBatchEvenForUnrankedParticipants(String state) {
    var seoul = ZoneId.of("Asia/Seoul");
    var week = LocalDate.now(seoul).with(DayOfWeek.MONDAY).minusWeeks(1);
    var owner = mock(Member.class);
    given(owner.getId()).willReturn(2L);
    var profile =
        InvestorProfile.create(
            owner,
            "투자자",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    org.springframework.test.util.ReflectionTestUtils.setField(profile, "id", 1L);
    profile.changePublication(
        true, true, false, false, week.minusDays(3).atStartOfDay(seoul).toInstant());
    profile.startTracking(week);
    var portfolio = mock(SharedPortfolio.class);
    given(portfolio.getId()).willReturn(3L);
    List<PortfolioDailyReturn> values = new ArrayList<>();
    for (int i = 0; i < 4; i++) values.add(dailyReturn(portfolio, week.plusDays(i), "1"));
    var friday = week.plusDays(4);
    if (state.equals("NO_RETURNS")) {
      values.clear();
    } else if (!state.equals("ABSENT")) {
      var last = dailyReturn(portfolio, friday, "1");
      if (!state.equals("COMPLETE")) last.exclude(state, Instant.now());
      values.add(last);
    }
    var days =
        calendar(week, List.of(week, week.plusDays(1), week.plusDays(2), week.plusDays(3), friday));
    given(
            tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(
                week.minusWeeks(7), week.plusDays(6)))
        .willReturn(days);
    rankingInputs(List.of(profile), List.of(portfolio), Map.of(3L, values));

    var result = service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20);

    boolean pending =
        state.equals("ABSENT")
            || state.equals("PRICE_COLLECTION_PENDING")
            || state.equals("SNAPSHOT_PENDING")
            || state.equals("MISSING_PRICE");
    LocalDate pendingDate = state.equals("NO_RETURNS") ? week : pending ? friday : null;
    then(aggregationService).should().ranking(week, pendingDate);
    assertThat(result.totalCount()).isEqualTo(state.equals("NO_RETURNS") ? 0 : 1);
    assertThat(result.asOfDate())
        .isEqualTo(
            state.equals("NO_RETURNS")
                ? null
                : state.equals("COMPLETE") ? friday : friday.minusDays(1));
    then(returnRepository)
        .should()
        .findLatestByMemberIdInAndBaseDateBetween(List.of(2L), week, week.plusDays(6));
    then(returnRepository)
        .should(never())
        .findLatestByPortfolioIdAndBaseDateBetween(any(), any(), any());
    then(portfolioRepository).should(never()).findByMemberId(any());
    service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20);
    then(aggregationService).should(times(2)).ranking(week, pendingDate);
    then(profileRepository).should(times(1)).findRankingInvestors();
    then(returnRepository)
        .should(times(1))
        .findLatestByMemberIdInAndBaseDateBetween(any(), any(), any());
    clearInvocations(aggregationService);
    profile.changePublication(false, false, false, false, Instant.now());
    assertThat(
            service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20).totalCount())
        .isZero();
    then(aggregationService).should().ranking(week, null);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unheldMarketCannotBlockRankOrHolidayCarry(boolean usOnly) {
    LocalDate week = LocalDate.now(ZoneId.of("Asia/Seoul")).with(DayOfWeek.MONDAY).minusWeeks(1);
    SharedPortfolio portfolio = dailyProfile(week.minusDays(3));
    var profile = profileRepository.findByPublicIdAndProfilePublicTrue("investor-1").orElseThrow();
    given(holdingRepository.findActiveUsLeagueMemberIds())
        .willReturn(usOnly ? List.of(2L) : List.of());
    lenient()
        .when(holdingRepository.findActiveDomesticLeagueMemberIds())
        .thenReturn(usOnly ? List.of() : List.of(2L));
    given(holdingRepository.hasActiveUsHolding(2L)).willReturn(usOnly);
    given(holdingRepository.hasOnlyUsHoldings(2L)).willReturn(usOnly);
    given(holdingRepository.existsActiveUsLeagueHolding()).willReturn(usOnly);
    if (usOnly) given(holdingRepository.existsActiveDomesticLeagueHolding()).willReturn(false);
    given(tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(any(), any()))
        .willAnswer(
            call -> {
              LocalDate from = call.getArgument(0);
              LocalDate to = call.getArgument(1);
              return from.datesUntil(to.plusDays(1))
                  .map(
                      date -> {
                        boolean weekday = date.getDayOfWeek().getValue() < 6;
                        boolean inWeek = !date.isBefore(week) && !date.isAfter(week.plusDays(6));
                        boolean heldOpen = inWeek ? date.equals(week) : weekday;
                        var day = new MarketTradingDay(date, usOnly ? weekday : heldOpen);
                        day.reviseUsTrading(usOnly ? heldOpen : weekday);
                        if (usOnly && Boolean.TRUE.equals(day.getUsTrading()))
                          day.usCollected(day.usClosesAt().plusSeconds(1));
                        if (!usOnly && day.isTrading())
                          day.collected(day.closesAt().plusSeconds(1));
                        return day;
                      })
                  .toList();
            });
    var value = dailyReturn(portfolio, week, "10");
    rankingInputs(List.of(profile), List.of(portfolio), Map.of(3L, List.of(value)));
    var rankings = service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20);
    assertThat(rankings.rankings()).hasSize(1);
    assertThat(rankings.rankings().getFirst().weeklyReturnRate()).isEqualByComparingTo("10");
    given(returnRepository.findLatestByPortfolioIdAndBaseDateBetween(3L, week, week.plusDays(6)))
        .willReturn(List.of(value));
    var history = service.dailyHistory("investor-1", week);
    assertThat(history.days()).hasSize(7);
    assertThat(history.weeklyReturnRate()).isEqualByComparingTo("10");
    assertThat(history.days().subList(1, 7))
        .allSatisfy(
            day -> {
              assertThat(day.reason()).isEqualTo("MARKET_CLOSED");
              assertThat(day.dailyReturnRate()).isZero();
              assertThat(day.cumulativeReturnRate()).isEqualByComparingTo("10");
            });
  }

  @Test
  void commonRankingDateDoesNotDelayPersonalReturnsAndMissingInvestorDoesNotBlockOthers() {
    var seoul = ZoneId.of("Asia/Seoul");
    var week = LocalDate.now(seoul).with(DayOfWeek.MONDAY).minusWeeks(1);
    var domesticPortfolio = dailyProfile(week.minusDays(3));
    var domestic = profileRepository.findByPublicIdAndProfilePublicTrue("investor-1").orElseThrow();
    List<InvestorProfile> profiles = new ArrayList<>(List.of(domestic));
    List<SharedPortfolio> portfolios = new ArrayList<>(List.of(domesticPortfolio));
    for (long id : List.of(4L, 7L)) {
      var member = mock(Member.class);
      given(member.getId()).willReturn(id);
      var profile =
          InvestorProfile.create(
              member,
              "투자자" + id,
              "",
              LeagueTypes.ExperienceLevel.BEGINNER,
              LeagueTypes.RiskProfile.BALANCED);
      org.springframework.test.util.ReflectionTestUtils.setField(profile, "id", id + 1);
      profile.changePublication(
          true, true, false, false, week.minusDays(3).atStartOfDay(seoul).toInstant());
      profile.startTracking(week);
      var portfolio = mock(SharedPortfolio.class);
      given(portfolio.getId()).willReturn(id + 2);
      profiles.add(profile);
      portfolios.add(portfolio);
    }
    given(holdingRepository.findActiveUsLeagueMemberIds()).willReturn(List.of(4L));
    given(holdingRepository.findActiveDomesticLeagueMemberIds()).willReturn(List.of(2L, 7L));
    var usReady = new java.util.concurrent.atomic.AtomicBoolean(false);
    given(tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(any(), any()))
        .willAnswer(
            call -> {
              LocalDate from = call.getArgument(0);
              LocalDate to = call.getArgument(1);
              return from.datesUntil(to.plusDays(1))
                  .map(
                      date -> {
                        boolean trading = date.getDayOfWeek().getValue() < 6;
                        var day = new MarketTradingDay(date, trading);
                        day.reviseUsTrading(trading);
                        if (date.equals(week) || date.equals(week.plusDays(1))) {
                          day.collected(day.closesAt().plusSeconds(1));
                          if (date.equals(week) || usReady.get())
                            day.usCollected(day.usClosesAt().plusSeconds(1));
                        }
                        return day;
                      })
                  .toList();
            });
    var missing = dailyReturn(portfolios.get(2), week.plusDays(1), "0");
    missing.exclude("MISSING_PRICE", Instant.now());
    var domesticReturns =
        List.of(
            dailyReturn(domesticPortfolio, week, "2"),
            dailyReturn(domesticPortfolio, week.plusDays(1), "3"));
    rankingInputs(
        profiles,
        portfolios,
        Map.of(
            3L, domesticReturns,
            6L,
                List.of(
                    dailyReturn(portfolios.get(1), week, "3"),
                    dailyReturn(portfolios.get(1), week.plusDays(1), "-2")),
            9L, List.of(dailyReturn(portfolios.get(2), week, "1"), missing)));
    given(returnRepository.findLatestByPortfolioIdAndBaseDateBetween(3L, week, week.plusDays(6)))
        .willReturn(domesticReturns);

    var monday = service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20);
    assertThat(monday.asOfDate()).isEqualTo(week);
    assertThat(monday.rankings())
        .extracting(row -> row.weeklyReturnRate())
        .containsExactly(new BigDecimal("3.00"), new BigDecimal("2.00"), new BigDecimal("1.00"));
    assertThat(service.dailyHistory("investor-1", week).weeklyReturnRate())
        .isEqualByComparingTo("5.06");

    usReady.set(true);
    var cached = service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20);
    assertThat(cached.asOfDate()).isEqualTo(week);
    assertThat(cached.rankings()).isEqualTo(monday.rankings());
    rankingCache.invalidateAfterCommit();
    var tuesday = service.rankings(null, week, LeagueTypes.LeagueType.WEEKLY_RETURN, 0, 20);
    assertThat(tuesday.asOfDate()).isEqualTo(week.plusDays(1));
    assertThat(tuesday.rankings())
        .extracting(row -> row.weeklyReturnRate())
        .containsExactly(new BigDecimal("5.06"), new BigDecimal("0.94"));
    assertThat(tuesday.rankings())
        .extracting(row -> row.publicId())
        .doesNotContain(profiles.get(2).getPublicId());
  }

  private void rankingInputs(
      List<InvestorProfile> profiles,
      List<SharedPortfolio> portfolios,
      java.util.Map<Long, List<PortfolioDailyReturn>> returns) {
    rankingPageProfiles(profiles);
    given(profileRepository.findRankingInvestors())
        .willAnswer(
            call ->
                profiles.stream()
                    .flatMap(
                        profile -> {
                          var periods = profile.getParticipations();
                          if (periods.isEmpty())
                            return java.util.stream.Stream.of(
                                new RankingInvestor(
                                    profile.getId(),
                                    profile.getMember().getId(),
                                    profile.getPublicId(),
                                    profile.getDisplayName(),
                                    profile.getExperienceLevel(),
                                    profile.getRiskProfile(),
                                    profile.getTrackingStartedOn(),
                                    null,
                                    null,
                                    null));
                          return periods.stream()
                              .map(
                                  period ->
                                      new RankingInvestor(
                                          profile.getId(),
                                          profile.getMember().getId(),
                                          profile.getPublicId(),
                                          profile.getDisplayName(),
                                          profile.getExperienceLevel(),
                                          profile.getRiskProfile(),
                                          profile.getTrackingStartedOn(),
                                          period.getStartedAt(),
                                          period.getEndedAt(),
                                          period.getPerformanceStartedAt()));
                        })
                    .toList());
    Map<Long, Long> membersByPortfolio = new java.util.HashMap<>();
    for (int i = 0; i < portfolios.size(); i++) {
      membersByPortfolio.put(portfolios.get(i).getId(), profiles.get(i).getMember().getId());
    }
    given(returnRepository.findLatestByMemberIdInAndBaseDateBetween(any(), any(), any()))
        .willAnswer(
            call ->
                returns.values().stream()
                    .flatMap(List::stream)
                    .filter(
                        value ->
                            !value.getBaseDate().isBefore(call.getArgument(1, LocalDate.class)))
                    .filter(
                        value -> !value.getBaseDate().isAfter(call.getArgument(2, LocalDate.class)))
                    .map(
                        value ->
                            new RankingDailyReturn(
                                membersByPortfolio.get(value.getPortfolio().getId()),
                                value.getBaseDate(),
                                value.getDailyReturnRate(),
                                value.getMaxHoldingWeight(),
                                value.getCurrentHoldingWeight(),
                                value.isEligible(),
                                value.isTrackingBaseline(),
                                value.getIneligibleReason()))
                    .toList());
  }

  private MarketTradingDay tradingDay(LocalDate date) {
    var day = new MarketTradingDay(date, true);
    day.collected(date.atTime(17, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant());
    return day;
  }

  private List<MarketTradingDay> calendar(LocalDate week, List<LocalDate> open) {
    List<MarketTradingDay> calendar = new ArrayList<>();
    for (int i = 0; i < 7; i++) {
      LocalDate date = week.plusDays(i);
      calendar.add(open.contains(date) ? tradingDay(date) : new MarketTradingDay(date, false));
    }
    given(tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(week, week.plusDays(6)))
        .willReturn(calendar);
    return calendar;
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
  @CsvSource({"false,false", "true,false", "false,true", "true,true"})
  void publicDetailsCompoundContributionsAndHonorHiddenAssets(
      boolean hidden, boolean effectiveHistory) {
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
            decisionRepository
                .findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
                    eq(3L), any(LocalDate.class)))
        .willReturn(List.of());
    LocalDate earlierDate = previousDate.minusDays(3);
    given(
            tradingDayRepository.findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(
                previousDate))
        .willReturn(Optional.of(tradingDay(earlierDate)));
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
    given(holdingHistoryRepository.findEffectiveBefore(eq(3L), any(Instant.class)))
        .willReturn(List.of(history));
    given(history.getAsset()).willReturn(asset);
    Instant historyTime = earlierDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant();
    if (effectiveHistory) given(history.getEffectiveAt()).willReturn(historyTime);
    else given(history.getCreatedAt()).willReturn(historyTime);
    given(history.getChangeType()).willReturn(ChangeType.ADDED);
    given(history.getQuantity()).willReturn(10L);
    given(asset.getId()).willReturn(4L);
    if (!hidden) {
      given(asset.getDisplayName()).willReturn("삼성전자");
      given(asset.getCategory()).willReturn(AssetCategory.STOCK);
    }
    SharedHolding visible = mock(SharedHolding.class);
    given(visible.isHidden()).willReturn(hidden);
    given(visible.getAsset()).willReturn(asset);
    given(visible.getQuantity()).willReturn(10L);
    if (!hidden) given(visible.getAveragePurchasePrice()).willReturn(new BigDecimal("100"));
    given(holdingRepository.findAllByPortfolioIdOrderById(3L)).willReturn(List.of(visible));
    given(previousPrice.getAsset()).willReturn(asset);
    given(previousPrice.getBaseDate()).willReturn(previousDate);
    given(currentPrice.getAsset()).willReturn(asset);
    given(currentPrice.getBaseDate()).willReturn(currentDate);
    given(
            priceRepository.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                List.of(4L), earlierDate, currentDate))
        .willReturn(
            List.of(
                new StockDailyPrice(asset, earlierDate, new BigDecimal("100")),
                previousPrice,
                currentPrice));
    given(previousPrice.getClosePrice()).willReturn(new BigDecimal("110"));
    given(currentPrice.getClosePrice()).willReturn(new BigDecimal("99"));

    given(stockPriceService.pricesAt(List.of(4L), currentDate))
        .willReturn(Map.of(4L, currentPrice));
    var response = service.investorDetail("investor-1");

    assertThat(response.asOfDate()).isEqualTo(currentDate);
    then(stockPriceService).should().pricesAt(List.of(4L), currentDate);
    then(priceRepository)
        .should()
        .findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
            List.of(4L), earlierDate, currentDate);
    then(priceRepository).shouldHaveNoMoreInteractions();
    then(holdingHistoryRepository)
        .should()
        .findEffectiveBefore(3L, currentDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
    then(holdingHistoryRepository).shouldHaveNoMoreInteractions();
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
    then(decisionRepository)
        .should()
        .findAllByPortfolioIdAndTradedOnLessThanEqualOrderByTradedOnDescIdDesc(
            eq(3L), any(LocalDate.class));
  }
}
