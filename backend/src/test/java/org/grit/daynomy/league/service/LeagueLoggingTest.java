package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.asset.service.StockPriceService;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.league.domain.InvestmentDecision;
import org.grit.daynomy.league.domain.InvestmentReview;
import org.grit.daynomy.league.domain.InvestorFollow;
import org.grit.daynomy.league.domain.InvestorProfile;
import org.grit.daynomy.league.domain.LeagueTypes.CollectionStatus;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.LeagueType;
import org.grit.daynomy.league.domain.LeagueTypes.RiskProfile;
import org.grit.daynomy.league.domain.LeagueTypes.TransactionType;
import org.grit.daynomy.league.domain.SharedHolding;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.grit.daynomy.league.dto.LeagueDto.DecisionRecordRequest;
import org.grit.daynomy.league.dto.LeagueDto.DecisionRequest;
import org.grit.daynomy.league.dto.LeagueDto.ProfileRequest;
import org.grit.daynomy.league.dto.LeagueDto.PublicationRequest;
import org.grit.daynomy.league.dto.LeagueDto.ReviewRequest;
import org.grit.daynomy.league.dto.SharedPortfolioDto.HoldingInput;
import org.grit.daynomy.league.dto.SharedPortfolioDto.ImportRequest;
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
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.member.service.MemberService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class LeagueLoggingTest {
  private static final LocalDate WEEK = LocalDate.of(2026, 9, 21);
  private static final String PRIVATE_NAME = "비공개별명";
  private static final String PRIVATE_TEXT = "비공개투자내용";
  private static final String PRIVATE_KEY = "private-request-key";

  @Mock private InvestorProfileRepository profiles;
  @Mock private SharedPortfolioRepository portfolios;
  @Mock private SharedHoldingRepository holdings;
  @Mock private SharedHoldingHistoryRepository histories;
  @Mock private PortfolioDailyReturnRepository returns;
  @Mock private InvestmentDecisionRepository decisions;
  @Mock private InvestmentReviewRepository reviews;
  @Mock private InvestorFollowRepository follows;
  @Mock private MemberRepository members;
  @Mock private MemberService memberService;
  @Mock private StockDailyPriceRepository prices;
  @Mock private StockPriceService stockPriceService;
  @Mock private MarketTradingDayRepository calendar;
  @Mock private WeeklyLeaderboardRepository leaderboards;
  @Mock private LeagueCalculationMetrics metrics;
  @Mock private EntityManager entityManager;
  @Mock private AssetRepository assets;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private LeagueService finalization;
  @Mock private LeagueAggregationService aggregationService;
  @Mock private LeagueEnrollmentService enrollmentService;

  @Spy private LeagueRankingCache rankingCache = new LeagueRankingCache(mock(TaskScheduler.class));
  @InjectMocks private InvestorProfileService profileService;
  @InjectMocks private InvestmentDecisionService decisionService;
  @InjectMocks private LeagueService leagueService;
  @InjectMocks private LeagueReturnService returnService;

  private final Logger logger = (Logger) LoggerFactory.getLogger("org.grit.daynomy.league");
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Level previousLevel;
  private SharedPortfolioService sharedService;

  @BeforeEach
  void captureLogs() {
    previousLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
    TransactionSynchronizationManager.initSynchronization();
    sharedService =
        new SharedPortfolioService(
            portfolios,
            holdings,
            histories,
            members,
            assets,
            stockPriceService,
            mock(org.grit.daynomy.asset.repository.StockDailyPriceRepository.class),
            mock(org.grit.daynomy.exchangerate.repository.UsdKrwRateRepository.class),
            calendar,
            profiles,
            decisions,
            decisionService,
            rankingCache,
            mock(LeagueAutoSyncService.class),
            mock(org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository.class));
  }

  @AfterEach
  void releaseLogs() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
    logger.detachAppender(appender);
    logger.setLevel(previousLevel);
    appender.stop();
  }

  @Test
  void savingAProfileWaitsForCommitAndDoesNotLogItsNameOrBio() {
    Member member = mock(Member.class);
    given(memberService.updateNickname(1L, PRIVATE_NAME)).willReturn(member);
    given(member.getNickname()).willReturn(PRIVATE_NAME);
    given(profiles.save(any(InvestorProfile.class)))
        .willAnswer(
            call -> {
              InvestorProfile saved = call.getArgument(0);
              ReflectionTestUtils.setField(saved, "id", 3L);
              return saved;
            });

    profileService.save(
        1L,
        new ProfileRequest(
            PRIVATE_NAME, ExperienceLevel.BEGINNER, RiskProfile.BALANCED, PRIVATE_TEXT));

    assertThat(appender.list).isEmpty();
    complete(true);
    assertThat(fields(event(LogEvent.LEAGUE_PROFILE_SAVED)))
        .containsEntry("memberId", 1L)
        .containsEntry("profileId", 3L)
        .containsEntry("created", true);
    assertPrivateTextAbsent();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void publicationLogsAppliedFlagsOnlyWhenTheTransactionCommits(boolean committed) {
    var profile = profile();
    given(members.findByIdForUpdate(1L)).willReturn(Optional.of(profile.getMember()));
    given(profiles.findByMemberIdForUpdate(1L)).willReturn(Optional.of(profile));

    profileService.publish(1L, new PublicationRequest(false, false, false, true));

    assertThat(appender.list).isEmpty();
    complete(committed);
    if (committed) {
      assertThat(fields(event(LogEvent.LEAGUE_PUBLICATION_CHANGED)))
          .containsEntry("previousProfilePublic", true)
          .containsEntry("profilePublic", false)
          .containsEntry("previousLeagueEnabled", true)
          .containsEntry("leagueEnabled", false)
          .containsEntry("previousDetailPublic", true)
          .containsEntry("detailPublic", false);
      assertPrivateTextAbsent();
    } else {
      assertThat(appender.list).isEmpty();
    }
  }

  @Test
  void importingLogsAddedUpdatedAndUnchangedCountsWithoutFinancialValues() {
    SharedPortfolio portfolio = portfolio();
    given(portfolio.getMember().getId()).willReturn(1L);
    var unchanged = new SharedHolding(portfolio, asset(3L), 2, new BigDecimal("100"));
    var updated = new SharedHolding(portfolio, asset(4L), 1, new BigDecimal("200"));
    List<SharedHolding> values = new ArrayList<>(List.of(unchanged, updated));
    given(holdings.findByPortfolioIdAndAssetId(2L, 3L)).willReturn(Optional.of(unchanged));
    given(holdings.findByPortfolioIdAndAssetId(2L, 4L)).willReturn(Optional.of(updated));
    given(assets.findById(5L)).willReturn(Optional.of(asset(5L)));
    given(holdings.save(any(SharedHolding.class)))
        .willAnswer(
            call -> {
              SharedHolding holding = call.getArgument(0);
              if (!values.contains(holding)) values.add(holding);
              return holding;
            });
    given(holdings.findAllByPortfolioIdOrderById(2L)).willReturn(values);

    sharedService.importHoldings(
        1L,
        new ImportRequest(
            List.of(
                new HoldingInput(3L, 2, new BigDecimal("100"), null),
                new HoldingInput(4L, 4, new BigDecimal("200"), null),
                new HoldingInput(5L, 7, new BigDecimal("333.33"), null)),
            true));

    assertThat(appender.list).isEmpty();
    complete(true);
    Map<String, Object> fields = fields(event(LogEvent.LEAGUE_HOLDINGS_IMPORTED));
    assertThat(fields)
        .containsEntry("requestedCount", 3)
        .containsEntry("addedCount", 1)
        .containsEntry("updatedCount", 1)
        .containsEntry("unchangedCount", 1)
        .containsEntry("holdingCount", 3);
    assertThat(fields.keySet())
        .doesNotContain("assetId", "quantity", "averagePurchasePrice", "evaluationAmount");
    assertThat(fields.toString()).doesNotContain("333.33");
  }

  @Test
  void aLaterImportFailureAlsoSuppressesTheNestedDecisionCompletionLog() {
    SharedPortfolio portfolio = portfolio();
    given(assets.findById(3L)).willReturn(Optional.of(asset(3L)));
    List<SharedHolding> created = new ArrayList<>();
    given(holdings.findByPortfolioIdAndAssetId(2L, 3L))
        .willAnswer(call -> created.stream().findFirst());
    given(holdings.save(any(SharedHolding.class)))
        .willAnswer(
            call -> {
              SharedHolding holding = call.getArgument(0);
              created.add(holding);
              return holding;
            });
    given(decisions.save(any(InvestmentDecision.class))).willAnswer(call -> call.getArgument(0));
    var input = new HoldingInput(3L, 2, BigDecimal.TEN, decisionRequest());

    assertThatThrownBy(
            () -> sharedService.importHoldings(1L, new ImportRequest(List.of(input, input), false)))
        .isInstanceOf(BusinessException.class);

    then(decisions).should().save(any(InvestmentDecision.class));
    assertThat(appender.list).isEmpty();
    complete(false);
    assertThat(appender.list).isEmpty();
  }

  @Test
  void anIdempotentDecisionIsDebugOnlyAndDoesNotExposeTheRequestKey() {
    SharedPortfolio portfolio = portfolio();
    var transaction = decision(portfolio);
    given(decisions.findByPortfolioIdAndRequestKey(2L, PRIVATE_KEY))
        .willReturn(Optional.of(transaction));

    decisionService.recordDecision(
        1L, new DecisionRecordRequest(PRIVATE_KEY, 3L, decisionRequest()));

    assertThat(event(LogEvent.LEAGUE_DECISION_REPLAYED).getLevel()).isEqualTo(Level.DEBUG);
    complete(true);
    assertThat(events(LogEvent.LEAGUE_DECISION_RECORDED)).isEmpty();
    assertPrivateTextAbsent();
  }

  @Test
  void reviewCreationAndRevisionOnlyLogInternalIdentifiers() {
    var transaction = decision(SharedPortfolio.create(mock(Member.class)));
    var review = new InvestmentReview(transaction, PRIVATE_TEXT, PRIVATE_TEXT, PRIVATE_TEXT);
    ReflectionTestUtils.setField(review, "id", 8L);
    given(decisions.findByIdAndPortfolioMemberId(7L, 1L)).willReturn(Optional.of(transaction));
    given(reviews.save(any(InvestmentReview.class))).willReturn(review);
    given(reviews.findByIdAndTransactionIdAndTransactionPortfolioMemberId(8L, 7L, 1L))
        .willReturn(Optional.of(review));
    var request = new ReviewRequest(PRIVATE_TEXT, PRIVATE_TEXT, PRIVATE_TEXT);

    decisionService.addReview(1L, 7L, request);
    decisionService.reviseReview(1L, 7L, 8L, request);

    assertThat(appender.list).isEmpty();
    complete(true);
    assertThat(fields(event(LogEvent.LEAGUE_REVIEW_CREATED)))
        .containsEntry("decisionId", 7L)
        .containsEntry("reviewId", 8L);
    assertThat(fields(event(LogEvent.LEAGUE_REVIEW_REVISED)))
        .containsEntry("decisionId", 7L)
        .containsEntry("reviewId", 8L);
    assertPrivateTextAbsent();
  }

  @Test
  void followAndUnfollowNoOpsAreNotReportedAsNewChanges() {
    var target = profile();
    given(target.getMember().getId()).willReturn(2L);
    Member follower = mock(Member.class);
    var existing = new InvestorFollow(follower, target);
    given(members.findById(1L)).willReturn(Optional.of(follower));
    given(profiles.findByPublicIdAndProfilePublicTrue("investor")).willReturn(Optional.of(target));
    given(follows.findByFollowerIdAndProfileId(1L, 3L))
        .willReturn(Optional.empty(), Optional.of(existing));
    given(follows.findByFollowerIdAndProfilePublicId(1L, "investor"))
        .willReturn(Optional.of(existing), Optional.empty());

    leagueService.follow(1L, "investor");
    leagueService.follow(1L, "investor");
    leagueService.unfollow(1L, "investor");
    leagueService.unfollow(1L, "investor");

    assertThat(appender.list).isEmpty();
    complete(true);
    var changes = events(LogEvent.LEAGUE_FOLLOW_CHANGED);
    assertThat(changes)
        .extracting(ILoggingEvent::getLevel)
        .containsExactly(Level.INFO, Level.DEBUG, Level.INFO, Level.DEBUG);
    assertThat(changes)
        .extracting(entry -> fields(entry).get("changed"))
        .containsExactly(true, false, true, false);
    then(follows).should().save(any(InvestorFollow.class));
    then(follows).should().delete(existing);
    assertPrivateTextAbsent();
  }

  @Test
  void emptyRankingAndHistoryLogsExplainCollectionStateWithoutLoggingReturns() {
    LocalDate week =
        LocalDate.now(ZoneId.of("Asia/Seoul")).with(java.time.DayOfWeek.MONDAY).minusWeeks(1);
    given(profiles.findByPublicIdAndProfilePublicTrue("investor"))
        .willReturn(Optional.of(profile()));

    leagueService.rankings(null, week, LeagueType.WEEKLY_RETURN, 0, 20);
    leagueService.dailyHistory("investor", week);

    var ranking = event(LogEvent.LEAGUE_RANKINGS_LOADED);
    assertThat(ranking.getLevel()).isEqualTo(Level.DEBUG);
    assertThat(fields(ranking))
        .containsEntry("weekStart", week)
        .containsEntry("confirmed", false)
        .containsEntry("collectionStatus", CollectionStatus.CALENDAR_PENDING)
        .containsEntry("totalCount", 0);
    assertThat(fields(event(LogEvent.LEAGUE_DAILY_HISTORY_LOADED)))
        .containsEntry("displayedDayCount", 7)
        .containsEntry("tradingDayCount", 0)
        .containsEntry("calculatedDayCount", 0)
        .doesNotContainKeys("weeklyReturnRate", "dailyReturnRate");
    assertPrivateTextAbsent();
  }

  @Test
  void calculationSummaryIncludesTheRangeAndCommittedOutcomeCounts() {
    var profile = profile();
    given(profile.getMember().getId()).willReturn(1L);
    var portfolio = SharedPortfolio.create(profile.getMember());
    ReflectionTestUtils.setField(portfolio, "id", 2L);
    given(portfolios.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    given(profiles.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));
    given(profiles.findByIdForUpdate(3L)).willReturn(Optional.of(profile));
    var day = new MarketTradingDay(WEEK, true);
    day.collected(WEEK.atTime(17, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant());
    given(
            calendar.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
                any(LocalDate.class)))
        .willReturn(List.of(day));

    assertThat(returnService.recalculateFrom(WEEK)).isEqualTo(1);

    assertThat(fields(event(LogEvent.LEAGUE_CALCULATION_COMPLETED)))
        .containsEntry("mode", "FROM_DATE")
        .containsEntry("requestedFromDate", WEEK)
        .containsEntry("fromDate", WEEK)
        .containsEntry("toDate", WEEK)
        .containsEntry("targetProfileCount", 1)
        .containsEntry("succeededProfileCount", 1)
        .containsEntry("processedDayCount", 1)
        .containsEntry("outcomeCounts", Map.of("NO_HOLDINGS", 1));
    then(transactionManager).should().commit(null);
    assertPrivateTextAbsent();
  }

  @Test
  void failuresKeepTheirContextAndRetryWithoutLeakingExceptionMessages() {
    var profile = profile();
    given(profiles.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));
    given(profiles.findByIdForUpdate(3L)).willThrow(new IllegalStateException(PRIVATE_TEXT));

    new LeagueReturnScheduler(returnService, mock(TaskScheduler.class)).calculateReturns();

    assertThat(events(LogEvent.LEAGUE_PROFILE_CALCULATION_FAILED)).hasSize(1);
    assertThat(events(LogEvent.LEAGUE_CALCULATION_FAILED)).hasSize(1);
    assertThat(events(LogEvent.LEAGUE_CALCULATION_COMPLETED)).isEmpty();
    assertThat(fields(event(LogEvent.LEAGUE_CALCULATION_FAILED)))
        .containsEntry("phase", "CALCULATE_PROFILES")
        .containsEntry("targetProfileCount", 1)
        .containsEntry("succeededProfileCount", 0)
        .containsEntry("failedProfileCount", 1)
        .containsEntry("exception", "IllegalStateException")
        .containsKey("stackTrace");
    assertThat(event(LogEvent.LEAGUE_CALCULATION_RETRY_PENDING).getLevel()).isEqualTo(Level.WARN);
    assertThat(fields(event(LogEvent.LEAGUE_CALCULATION_RETRY_PENDING)))
        .containsEntry("trigger", "STARTUP_OR_SCHEDULE")
        .containsEntry("pendingFullRecalculation", true);
    then(transactionManager).should().rollback(null);
    then(finalization).shouldHaveNoInteractions();
    assertPrivateTextAbsent();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failureLogsKeepCauseFramesWithoutMessagesAndStopAtCauseCycles(boolean profileFailure) {
    var cause = new IllegalArgumentException(PRIVATE_TEXT);
    cause.setStackTrace(
        new StackTraceElement[] {
          new StackTraceElement("PricingClient", "load", "PricingClient.java", 42)
        });
    var failure = new IllegalStateException(PRIVATE_KEY, cause);
    cause.initCause(failure);
    if (profileFailure) {
      given(profiles.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile()));
      given(profiles.findByIdForUpdate(3L))
          .willAnswer(
              call -> {
                throw failure;
              });
    } else {
      given(
              calendar.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
                  any(LocalDate.class)))
          .willAnswer(
              call -> {
                throw failure;
              });
    }

    assertThatThrownBy(returnService::recalculate).isInstanceOf(IllegalStateException.class);

    var failedEvent =
        profileFailure
            ? LogEvent.LEAGUE_PROFILE_CALCULATION_FAILED
            : LogEvent.LEAGUE_CALCULATION_FAILED;
    assertThat(fields(event(failedEvent)).get("stackTrace").toString())
        .contains(IllegalStateException.class.getName(), IllegalArgumentException.class.getName())
        .containsOnlyOnce("PricingClient.load(PricingClient.java:42)")
        .doesNotContain(PRIVATE_NAME, PRIVATE_TEXT, PRIVATE_KEY);
    assertPrivateTextAbsent();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void automaticRunsKeepAnExistingTraceOrCleanUpTheirGeneratedTrace(boolean existingTrace) {
    String previousTrace = MDC.get("traceId");
    if (existingTrace) MDC.put("traceId", "request-trace");
    else MDC.remove("traceId");
    try {
      var service = mock(LeagueReturnService.class);
      List<String> traces = new ArrayList<>();
      doAnswer(
              call -> {
                traces.add(MDC.get("traceId"));
                throw new IllegalStateException(PRIVATE_TEXT);
              })
          .when(service)
          .recalculate();

      new LeagueReturnScheduler(service, mock(TaskScheduler.class)).calculateReturns();

      assertThat(traces).hasSize(1).allSatisfy(trace -> assertThat(trace).isNotBlank());
      assertThat(MDC.get("traceId")).isEqualTo(existingTrace ? "request-trace" : null);
      if (existingTrace) assertThat(traces).containsExactly("request-trace");
      assertPrivateTextAbsent();
    } finally {
      if (previousTrace == null) MDC.remove("traceId");
      else MDC.put("traceId", previousTrace);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void weeklyFinalizationDistinguishesMissingCalendarAndPendingPrices(boolean missingCalendar) {
    var profile = profile();
    ReflectionTestUtils.setField(
        profile.getParticipations().getFirst(),
        "startedAt",
        WEEK.minusDays(3).atTime(15, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant());
    given(profiles.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));
    List<MarketTradingDay> days =
        WEEK.datesUntil(WEEK.plusDays(missingCalendar ? 6 : 7))
            .map(date -> new MarketTradingDay(date, date.getDayOfWeek().getValue() < 6))
            .toList();
    given(
            calendar.findAllByBaseDateBetweenOrderByBaseDate(
                any(LocalDate.class), any(LocalDate.class)))
        .willAnswer(call -> WEEK.equals(call.getArgument(0)) ? days : List.of());

    leagueService.finalizeWeeks();

    assertThat(appender.list).isEmpty();
    complete(true);
    var pending =
        events(LogEvent.LEAGUE_FINALIZATION_PENDING).stream()
            .filter(entry -> WEEK.equals(fields(entry).get("weekStart")))
            .findFirst()
            .orElseThrow();
    assertThat(fields(pending))
        .containsEntry("reason", missingCalendar ? "CALENDAR_PENDING" : "PRICE_COLLECTION_PENDING");
    assertThat(pending.getLevel()).isEqualTo(missingCalendar ? Level.WARN : Level.DEBUG);
    assertThat(events(LogEvent.LEAGUE_WEEK_FINALIZED)).isEmpty();
  }

  @Test
  void rolledBackWeeklySnapshotsNeverProduceFinalizationSuccessLogs() {
    var profile = profile();
    given(profile.getMember().getId()).willReturn(1L);
    given(profiles.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));
    List<MarketTradingDay> days =
        WEEK.datesUntil(WEEK.plusDays(7))
            .map(
                date -> {
                  var day = new MarketTradingDay(date, date.getDayOfWeek().getValue() < 6);
                  day.collected(date.atTime(17, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant());
                  return day;
                })
            .toList();
    given(
            calendar.findAllByBaseDateBetweenOrderByBaseDate(
                any(LocalDate.class), any(LocalDate.class)))
        .willAnswer(call -> WEEK.equals(call.getArgument(0)) ? days : List.of());

    leagueService.finalizeWeeks();

    then(leaderboards).should(times(3)).save(any());
    assertThat(appender.list).isEmpty();
    complete(false);
    assertThat(events(LogEvent.LEAGUE_WEEK_FINALIZED)).isEmpty();
    assertThat(events(LogEvent.LEAGUE_FINALIZATION_COMPLETED)).isEmpty();
    assertThat(events(LogEvent.LEAGUE_FINALIZATION_ROLLED_BACK)).hasSize(1);
  }

  private InvestorProfile profile() {
    var profile =
        InvestorProfile.create(
            mock(Member.class),
            PRIVATE_NAME,
            PRIVATE_TEXT,
            ExperienceLevel.BEGINNER,
            RiskProfile.BALANCED);
    ReflectionTestUtils.setField(profile, "id", 3L);
    profile.changePublication(
        true, true, true, true, WEEK.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
    return profile;
  }

  private SharedPortfolio portfolio() {
    Member member = mock(Member.class);
    var portfolio = SharedPortfolio.create(member);
    ReflectionTestUtils.setField(portfolio, "id", 2L);
    given(members.findByIdForUpdate(1L)).willReturn(Optional.of(member));
    given(portfolios.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    return portfolio;
  }

  private Asset asset(Long id) {
    var asset = Asset.listedStock("종목", id.toString(), StockMarket.KOSPI, "KR0000000000", WEEK);
    ReflectionTestUtils.setField(asset, "id", id);
    return asset;
  }

  private DecisionRequest decisionRequest() {
    return new DecisionRequest(PRIVATE_TEXT, null, PRIVATE_TEXT, PRIVATE_TEXT, null);
  }

  private InvestmentDecision decision(SharedPortfolio portfolio) {
    var decision =
        new InvestmentDecision(
            portfolio,
            asset(3L),
            PRIVATE_KEY,
            TransactionType.HOLD,
            2,
            new BigDecimal("123.45"),
            BigDecimal.ZERO,
            WEEK,
            PRIVATE_TEXT,
            null,
            PRIVATE_TEXT,
            PRIVATE_TEXT,
            null,
            true);
    ReflectionTestUtils.setField(decision, "id", 7L);
    return decision;
  }

  private void complete(boolean committed) {
    List<TransactionSynchronization> callbacks =
        TransactionSynchronizationManager.getSynchronizations();
    if (committed) callbacks.forEach(TransactionSynchronization::afterCommit);
    callbacks.forEach(
        callback ->
            callback.afterCompletion(
                committed
                    ? TransactionSynchronization.STATUS_COMMITTED
                    : TransactionSynchronization.STATUS_ROLLED_BACK));
    TransactionSynchronizationManager.clearSynchronization();
  }

  private List<ILoggingEvent> events(LogEvent event) {
    return appender.list.stream()
        .filter(entry -> event.code().equals(fields(entry).get("event")))
        .toList();
  }

  private ILoggingEvent event(LogEvent event) {
    return events(event).getFirst();
  }

  private Map<String, Object> fields(ILoggingEvent event) {
    Map<String, Object> values = new HashMap<>();
    if (event.getKeyValuePairs() != null)
      event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }

  private void assertPrivateTextAbsent() {
    assertThat(appender.list)
        .allSatisfy(
            event ->
                assertThat(event.getFormattedMessage() + fields(event))
                    .doesNotContain(PRIVATE_NAME, PRIVATE_TEXT, PRIVATE_KEY, "123.45"));
  }
}
