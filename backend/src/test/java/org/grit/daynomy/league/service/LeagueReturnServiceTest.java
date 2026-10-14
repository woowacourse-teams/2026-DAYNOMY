package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.league.domain.*;
import org.grit.daynomy.league.repository.*;
import org.grit.daynomy.member.domain.Member;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class LeagueReturnServiceTest {
  @Mock private InvestorProfileRepository profileRepository;
  @Mock private SharedPortfolioRepository portfolioRepository;
  @Mock private SharedHoldingHistoryRepository historyRepository;
  @Mock private StockDailyPriceRepository priceRepository;
  @Mock private PortfolioDailyReturnRepository returnRepository;
  @Mock private MarketTradingDayRepository tradingDayRepository;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private LeagueCalculationMetrics metrics;
  @Mock private LeagueService leagueService;
  @InjectMocks private LeagueReturnService service;

  @Test
  void finalizationFailureMarksTheWholeRunAsFailed() {
    willThrow(new IllegalStateException("finalization failed"))
        .given(leagueService)
        .finalizeWeeks();

    assertThatThrownBy(service::recalculate).isInstanceOf(IllegalStateException.class);

    then(metrics).should().finishRun(0L, false);
  }

  @Test
  void hiddenSharedHoldingIsIncludedAndOnlyStateBeforeTheDayIsUsedWithoutMinimumAmount() {
    LocalDate friday = LocalDate.of(2026, 9, 18);
    LocalDate monday = LocalDate.of(2026, 9, 21);
    SharedPortfolio portfolio = profile(1L, friday.minusDays(1));
    Asset asset = stock();
    var holding = new SharedHolding(portfolio, asset, 2, new BigDecimal("100"));
    holding.describe(true, "숨긴 판단");
    var history = new SharedHoldingHistory(holding, SharedHoldingHistory.ChangeType.ADDED);
    given(
            historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq(2L), any(Instant.class)))
        .willReturn(List.of(history));
    var previous = day(friday, true);
    var current = day(monday, true);
    given(
            tradingDayRepository.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
                any(LocalDate.class)))
        .willReturn(List.of(previous, current));
    given(tradingDayRepository.findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(monday))
        .willReturn(Optional.of(previous));
    price(asset, friday, "100");
    price(asset, monday, "110");

    assertThat(service.recalculate()).isEqualTo(2);
    then(leagueService).should().finalizeWeeks();
    then(metrics).should().finishRun(0L, true);
    then(metrics).should().profileSucceeded(Map.of("CALCULATED", 2));
    var results = ArgumentCaptor.forClass(PortfolioDailyReturn.class);
    then(returnRepository).should(org.mockito.Mockito.times(2)).save(results.capture());
    assertThat(results.getAllValues().getFirst().isTrackingBaseline()).isTrue();
    assertThat(results.getAllValues().getFirst().getDailyReturnRate()).isEqualByComparingTo("0");
    var result = results.getAllValues().getLast();
    assertThat(result.isEligible()).isTrue();
    assertThat(result.getDailyReturnRate()).isEqualByComparingTo("10.0000");
    assertThat(result.getStartingEvaluationAmount()).isEqualByComparingTo("200");
    then(historyRepository)
        .should()
        .findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            2L, Instant.parse("2026-09-20T15:00:00Z"));
  }

  @Test
  void partialEnrollmentWeekStartsAtFirstCloseAndDoesNotCountEarlierMovement() {
    LocalDate monday = LocalDate.of(2026, 9, 21);
    SharedPortfolio portfolio = profile(1L, monday);
    Asset asset = stock();
    var history =
        new SharedHoldingHistory(
            new SharedHolding(portfolio, asset, 1, new BigDecimal("100")),
            SharedHoldingHistory.ChangeType.ADDED);
    given(
            historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq(2L), any(Instant.class)))
        .willReturn(List.of(history));
    var first = day(monday, true);
    var next = day(monday.plusDays(1), true);
    given(
            tradingDayRepository.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
                any(LocalDate.class)))
        .willReturn(List.of(first, next));
    given(
            tradingDayRepository.findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(
                next.getBaseDate()))
        .willReturn(Optional.of(first));
    price(asset, monday, "120");
    price(asset, monday.plusDays(1), "132");

    assertThat(service.recalculate()).isEqualTo(2);
    var results = ArgumentCaptor.forClass(PortfolioDailyReturn.class);
    then(returnRepository).should(org.mockito.Mockito.times(2)).save(results.capture());
    assertThat(results.getAllValues().getFirst().getDailyReturnRate()).isEqualByComparingTo("0");
    assertThat(results.getAllValues().getLast().getDailyReturnRate()).isEqualByComparingTo("10");
    var profile = profileRepository.findByIdForUpdate(1L).orElseThrow();
    assertThat(profile.getTrackingStartedOn()).isEqualTo(monday);
    assertThat(profile.eligibleFrom()).isEqualTo(monday);
    then(historyRepository)
        .should()
        .findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
            2L, first.closesAt().plusNanos(1));
  }

  @Test
  void enrollmentAfterCloseStartsAtTheNextTradingClose() {
    LocalDate monday = LocalDate.of(2026, 9, 21);
    SharedPortfolio portfolio = profile(1L, monday);
    var profile = profileRepository.findByIdForUpdate(1L).orElseThrow();
    Instant enrolled = monday.atTime(16, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    ReflectionTestUtils.setField(profile, "leagueEnabledAt", enrolled);
    ReflectionTestUtils.setField(profile.getParticipations().getFirst(), "startedAt", enrolled);
    Asset asset = stock();
    var history =
        new SharedHoldingHistory(
            new SharedHolding(portfolio, asset, 1, new BigDecimal("100")),
            SharedHoldingHistory.ChangeType.ADDED);
    given(
            historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq(2L), any(Instant.class)))
        .willReturn(List.of(history));
    var tuesday = day(monday.plusDays(1), true);
    given(
            tradingDayRepository.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
                any(LocalDate.class)))
        .willReturn(List.of(day(monday, true), tuesday));
    price(asset, tuesday.getBaseDate(), "110");

    assertThat(service.recalculate()).isEqualTo(1);
    var result = ArgumentCaptor.forClass(PortfolioDailyReturn.class);
    then(returnRepository).should().save(result.capture());
    assertThat(result.getValue().getBaseDate()).isEqualTo(tuesday.getBaseDate());
    assertThat(result.getValue().getDailyReturnRate()).isZero();
    assertThat(profile.getTrackingStartedOn()).isEqualTo(tuesday.getBaseDate());
    assertThat(profile.eligibleFrom()).isEqualTo(tuesday.getBaseDate());
  }

  @Test
  void missingCollectionIsRecordedAndCannotBridgeToTheNextDay() {
    LocalDate monday = LocalDate.of(2026, 9, 21);
    SharedPortfolio portfolio = profile(1L, monday.minusDays(1));
    Asset asset = stock();
    var history =
        new SharedHoldingHistory(
            new SharedHolding(portfolio, asset, 1, new BigDecimal("100")),
            SharedHoldingHistory.ChangeType.ADDED);
    var first = day(monday, true);
    var missing = day(monday.plusDays(1), false);
    var next = day(monday.plusDays(2), true);
    given(
            tradingDayRepository.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
                any(LocalDate.class)))
        .willReturn(List.of(first, missing, next));
    given(
            historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq(2L), any(Instant.class)))
        .willReturn(List.of(history));
    given(
            tradingDayRepository.findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(
                next.getBaseDate()))
        .willReturn(Optional.of(missing));
    price(asset, monday, "100");

    assertThat(service.recalculate()).isEqualTo(3);
    var results = ArgumentCaptor.forClass(PortfolioDailyReturn.class);
    then(returnRepository).should(org.mockito.Mockito.times(3)).save(results.capture());
    assertThat(results.getAllValues().subList(1, 3))
        .allSatisfy(
            result -> {
              assertThat(result.isEligible()).isFalse();
              assertThat(result.getIneligibleReason()).isEqualTo("PRICE_COLLECTION_PENDING");
            });
  }

  @Test
  void failedParticipantRollsBackIndependentlyAndDoesNotStopOthers() {
    var failed = mock(InvestorProfile.class);
    given(failed.getId()).willReturn(9L);
    given(profileRepository.findByIdForUpdate(9L))
        .willThrow(new IllegalStateException("test failure"));
    SharedPortfolio portfolio = profile(1L, LocalDate.of(2026, 9, 20));
    var valid = profileRepository.findByIdForUpdate(1L).orElseThrow();
    given(profileRepository.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(failed, valid));
    given(
            tradingDayRepository.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
                any(LocalDate.class)))
        .willReturn(List.of(day(LocalDate.of(2026, 9, 21), true)));
    assertThatThrownBy(service::recalculate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("1건");
    then(metrics).should().resetProfiles(2);
    then(metrics).should().profileFailed();
    then(metrics).should().profileSucceeded(Map.of("NO_HOLDINGS", 1));
    then(returnRepository).should().save(any(PortfolioDailyReturn.class));
    then(leagueService).shouldHaveNoInteractions();
    then(metrics).should().finishRun(0L, false);
  }

  @Test
  void pendingRunCalculatesOnlyLatestDayAndKeepsPriorBaseline() {
    LocalDate monday = LocalDate.of(2026, 9, 21);
    LocalDate wednesday = monday.plusDays(2);
    SharedPortfolio portfolio = profile(1L, monday);
    Asset asset = stock();
    var history =
        new SharedHoldingHistory(
            new SharedHolding(portfolio, asset, 1, new BigDecimal("100")),
            SharedHoldingHistory.ChangeType.ADDED);
    given(
            tradingDayRepository.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
                any(LocalDate.class)))
        .willReturn(
            List.of(day(monday, true), day(monday.plusDays(1), true), day(wednesday, true)));
    given(returnRepository.findFirstByPortfolioIdOrderByBaseDateDescCalculationVersionDesc(2L))
        .willReturn(Optional.of(new PortfolioDailyReturn(portfolio, wednesday)));
    given(
            returnRepository.existsLatestEligibleByPortfolioIdAndBaseDateBetween(
                2L, monday, wednesday.minusDays(1)))
        .willReturn(true);
    given(
            historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq(2L), any(Instant.class)))
        .willReturn(List.of(history));
    given(
            tradingDayRepository.findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(
                wednesday))
        .willReturn(Optional.of(day(wednesday.minusDays(1), true)));
    price(asset, wednesday.minusDays(1), "100");
    price(asset, wednesday, "110");

    assertThat(service.recalculatePending()).isEqualTo(1);
    then(leagueService).should().finalizeWeeks(wednesday);
    var result = ArgumentCaptor.forClass(PortfolioDailyReturn.class);
    then(returnRepository).should().save(result.capture());
    assertThat(result.getValue().isTrackingBaseline()).isFalse();
    assertThat(result.getValue().getDailyReturnRate()).isEqualByComparingTo("10");
  }

  @Test
  void priceCorrectionRecalculatesFromTheChangedDate() {
    LocalDate monday = LocalDate.of(2026, 9, 21);
    SharedPortfolio portfolio = profile(1L, monday);
    org.mockito.Mockito.reset(tradingDayRepository);
    given(
            tradingDayRepository.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
                any(LocalDate.class)))
        .willReturn(
            List.of(
                day(monday, true), day(monday.plusDays(1), true), day(monday.plusDays(2), true)));
    given(returnRepository.findFirstByPortfolioIdOrderByBaseDateDescCalculationVersionDesc(2L))
        .willReturn(Optional.of(new PortfolioDailyReturn(portfolio, monday.plusDays(2))));

    assertThat(service.recalculateFrom(monday)).isEqualTo(3);
    then(leagueService).should().finalizeWeeks(monday);
    then(returnRepository)
        .should(org.mockito.Mockito.times(3))
        .save(any(PortfolioDailyReturn.class));
  }

  @Test
  void correctedHolidayMovesTrackingStartToNextEligibleTradingDay() {
    LocalDate monday = LocalDate.of(2026, 9, 21);
    LocalDate tuesday = monday.plusDays(1);
    SharedPortfolio portfolio = profile(1L, monday);
    InvestorProfile investor = profileRepository.findByIdForUpdate(1L).orElseThrow();
    investor.startTracking(monday);
    given(tradingDayRepository.findById(monday))
        .willReturn(Optional.of(new MarketTradingDay(monday, false)));
    given(
            tradingDayRepository.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
                any(LocalDate.class)))
        .willReturn(List.of(day(tuesday, true)));
    Asset asset = stock();
    given(
            historyRepository.findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
                eq(2L), any(Instant.class)))
        .willReturn(
            List.of(
                new SharedHoldingHistory(
                    new SharedHolding(portfolio, asset, 1, new BigDecimal("100")),
                    SharedHoldingHistory.ChangeType.ADDED)));
    price(asset, tuesday, "100");

    assertThat(service.recalculate()).isEqualTo(1);
    assertThat(investor.getTrackingStartedOn()).isEqualTo(tuesday);
  }

  private SharedPortfolio profile(Long id, LocalDate enrolled) {
    Member member = mock(Member.class);
    given(member.getId()).willReturn(1L);
    var profile =
        InvestorProfile.create(
            member,
            "공개 별명",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    ReflectionTestUtils.setField(profile, "id", id);
    profile.changePublication(
        true, true, false, false, enrolled.atStartOfDay(ZoneId.of("Asia/Seoul")).toInstant());
    given(profileRepository.findAllByLeagueEnabledAtIsNotNull()).willReturn(List.of(profile));
    given(profileRepository.findByIdForUpdate(id)).willReturn(Optional.of(profile));
    var portfolio = mock(SharedPortfolio.class);
    given(portfolio.getId()).willReturn(2L);
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(portfolio));
    return portfolio;
  }

  private Asset stock() {
    return Asset.listedStock(
        "종목", "005930", StockMarket.KOSPI, "KR7005930003", LocalDate.of(2026, 9, 1));
  }

  private MarketTradingDay day(LocalDate date, boolean collected) {
    var day = new MarketTradingDay(date, true);
    if (collected) day.collected(date.atTime(17, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant());
    return day;
  }

  private void price(Asset asset, LocalDate date, String price) {
    given(priceRepository.findByAssetIdAndBaseDate(asset.getId(), date))
        .willReturn(Optional.of(new StockDailyPrice(asset, date, new BigDecimal(price))));
  }
}
