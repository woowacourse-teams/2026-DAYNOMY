package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.league.domain.*;
import org.grit.daynomy.member.domain.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class LeagueAggregationServiceTest {
  private final MarketTradingDayRepository repository = mock(MarketTradingDayRepository.class);
  private final LeagueAggregationService service =
      new LeagueAggregationService(
          repository,
          "0 30 14,18 * * MON-FRI",
          "0 20 17,19 * * MON-SAT",
          "0 30 7 * * TUE-SAT",
          "0 0 7,16 * * MON-SAT",
          true,
          true,
          true);
  private final LocalDate monday = LocalDate.of(2026, 10, 19);
  private final ZoneId seoul = ZoneId.of("Asia/Seoul");
  private List<MarketTradingDay> calendar;

  @Test
  void usClosePendingUsesUsPreparationAndCalculationSchedules() {
    for (MarketTradingDay day : calendar) {
      day.reviseUsTrading(day.getBaseDate().getDayOfWeek().getValue() <= 5);
      if (day.isTrading()) day.collected(day.closesAt().plusSeconds(1));
    }
    var result = service.ranking(monday, null, at(monday.plusDays(1), 8, 0), true, true);
    assertThat(result.reason()).isEqualTo("PRICE_COLLECTION_PENDING");
    assertThat(result.expectedAt()).isEqualTo(at(monday.plusDays(1), 17, 20));
  }

  @Test
  void usOnlyRankingDoesNotWaitForDomesticPrices() {
    for (MarketTradingDay day : calendar) {
      day.reviseUsTrading(day.getBaseDate().getDayOfWeek().getValue() <= 5);
      if (Boolean.TRUE.equals(day.getUsTrading())) day.usCollected(day.usClosesAt().plusSeconds(1));
    }
    assertThat(service.ranking(monday, null, true, false)).isNull();
  }

  @BeforeEach
  void calendar() {
    calendar =
        IntStream.range(0, 7)
            .mapToObj(i -> new MarketTradingDay(monday.plusDays(i), i < 5))
            .toList();
    given(repository.findAllByBaseDateBetweenOrderByBaseDate(monday, monday.plusDays(6)))
        .willReturn(calendar);
    given(repository.findFirstByTradingTrueAndBaseDateGreaterThanOrderByBaseDateAsc(any()))
        .willAnswer(
            invocation -> {
              LocalDate date = invocation.getArgument(0);
              LocalDate next = date.plusDays(1);
              while (next.getDayOfWeek().getValue() > 5) next = next.plusDays(1);
              return Optional.of(new MarketTradingDay(next, true));
            });
  }

  @Test
  void firstBaselineAndFirstReturnHaveDifferentExpectedTimes() {
    var profile = profile(monday.plusDays(2));
    var result = service.estimate(monday, profile, Map.of(), at(monday.plusDays(2), 12, 0));
    assertThat(result.baseDate()).isEqualTo(monday.plusDays(2));
    assertThat(result.expectedAt()).isEqualTo(at(monday.plusDays(3), 14, 30));
    assertThat(result.firstReturnExpectedAt()).isEqualTo(at(monday.plusDays(4), 14, 30));
    assertThat(result.reason()).isEqualTo("MARKET_CLOSE_PENDING");
    assertThat(result.delayed()).isFalse();
  }

  @Test
  void delayedCollectionUsesNextActualCronAndExplainsDelay() {
    var result = service.estimate(monday, null, Map.of(), at(monday.plusDays(1), 15, 0));
    assertThat(result.expectedAt()).isEqualTo(at(monday.plusDays(1), 18, 30));
    assertThat(result.reason()).isEqualTo("PRICE_COLLECTION_DELAYED");
    assertThat(result.delayed()).isTrue();
  }

  @Test
  void holidayAfterFridayMovesBaselineAndFirstReturnToFollowingTradingDays() {
    var friday = monday.plusDays(4);
    var nextTuesday = monday.plusDays(8);
    given(repository.findFirstByTradingTrueAndBaseDateGreaterThanOrderByBaseDateAsc(friday))
        .willReturn(Optional.of(new MarketTradingDay(nextTuesday, true)));
    var result = service.estimate(monday, profile(friday), Map.of(), at(friday, 12, 0));
    assertThat(result.expectedAt()).isEqualTo(at(nextTuesday, 14, 30));
    assertThat(result.firstReturnExpectedAt()).isEqualTo(at(nextTuesday.plusDays(1), 14, 30));
  }

  @Test
  void historicalRecordsWithoutCollectedPricesDoNotPromiseAnExtraZeroBaseline() {
    var profile = profile(monday.plusDays(2));
    profile.getParticipations().getFirst().includeCurrentWeekFrom(calendar.getFirst().closesAt());
    var result = service.estimate(monday, profile, Map.of(), at(monday.plusDays(2), 12, 0));
    assertThat(result.expectedAt()).isNotNull();
    assertThat(result.firstReturnExpectedAt()).isNull();
  }

  @Test
  void missingSnapshotWaitsForRefreshAndCalculationWithoutInventingAZeroBaseline() {
    var profile = profile(monday.plusDays(2));
    profile.getParticipations().getFirst().includeCurrentWeekFrom(calendar.getFirst().closesAt());
    calendar.getFirst().collected(at(monday.plusDays(1), 14, 30));
    var missing = new PortfolioDailyReturn(SharedPortfolio.create(mock(Member.class)), monday);
    missing.exclude("SNAPSHOT_PENDING", at(monday.plusDays(2), 15, 0));

    var result =
        service.estimate(monday, profile, Map.of(monday, missing), at(monday.plusDays(2), 15, 0));

    assertThat(result.baseDate()).isEqualTo(monday);
    assertThat(result.reason()).isEqualTo("SNAPSHOT_PENDING");
    assertThat(result.expectedAt()).isEqualTo(at(monday.plusDays(3), 17, 20));
    assertThat(result.firstReturnExpectedAt()).isNull();
  }

  @ParameterizedTest
  @CsvSource({
    "false,true,true,3,SNAPSHOT_PENDING",
    "true,false,true,,SNAPSHOT_PENDING",
    "false,false,true,,COLLECTION_PAUSED",
    "true,true,false,,COLLECTION_PAUSED"
  })
  void snapshotWaitUsesEnabledRefreshSchedules(
      boolean priceEnabled,
      boolean snapshotEnabled,
      boolean calculationEnabled,
      Integer expectedDayOffset,
      String reason) {
    var configured =
        new LeagueAggregationService(
            repository,
            "0 30 14,18 * * MON-FRI",
            "0 20 17,19 * * MON-SAT",
            "0 30 7 * * TUE-SAT",
            "0 0 7,16 * * MON-SAT",
            priceEnabled,
            calculationEnabled,
            snapshotEnabled);
    var profile = profile(monday.plusDays(2));
    profile.getParticipations().getFirst().includeCurrentWeekFrom(calendar.getFirst().closesAt());
    calendar.getFirst().collected(at(monday.plusDays(1), 14, 30));
    var missing = new PortfolioDailyReturn(SharedPortfolio.create(mock(Member.class)), monday);
    missing.exclude("SNAPSHOT_PENDING", at(monday.plusDays(2), 12, 0));

    var result =
        configured.estimate(
            monday, profile, Map.of(monday, missing), at(monday.plusDays(2), 12, 0));

    assertThat(result.expectedAt())
        .isEqualTo(
            expectedDayOffset == null ? null : at(monday.plusDays(expectedDayOffset), 17, 20));
    assertThat(result.reason()).isEqualTo(reason);
  }

  @Test
  void missingPriceHasNoInventedRetryTime() {
    var profile = profile(monday);
    calendar.getFirst().collected(at(monday.plusDays(1), 14, 30));
    var missing = new PortfolioDailyReturn(SharedPortfolio.create(mock(Member.class)), monday);
    missing.exclude("MISSING_PRICE", Instant.now());
    var result =
        service.estimate(monday, profile, Map.of(monday, missing), at(monday.plusDays(1), 15, 0));
    assertThat(result.expectedAt()).isNull();
    assertThat(result.reason()).isEqualTo("MISSING_PRICE");
  }

  @Test
  void incompleteCalendarHasNoEstimatedTime() {
    given(repository.findAllByBaseDateBetweenOrderByBaseDate(monday, monday.plusDays(6)))
        .willReturn(List.of());
    var result = service.estimate(monday, null, Map.of(), at(monday, 12, 0));
    assertThat(result.expectedAt()).isNull();
    assertThat(result.reason()).isEqualTo("CALENDAR_PENDING");
  }

  @Test
  void existingParticipantDoesNotRestartAtZeroEveryWeek() {
    var profile = profile(monday.minusWeeks(1));
    profile.startTracking(monday.minusWeeks(1));
    var result = service.estimate(monday, profile, Map.of(), at(monday, 12, 0));
    assertThat(result.firstReturnExpectedAt()).isNull();
  }

  @Test
  void calculationAfterCollectionUsesCalculationSchedule() {
    var profile = profile(monday.minusWeeks(1));
    profile.startTracking(monday.minusWeeks(1));
    calendar.getFirst().collected(at(monday.plusDays(1), 14, 30));
    var result = service.estimate(monday, profile, Map.of(), at(monday.plusDays(1), 15, 0));
    assertThat(result.expectedAt()).isEqualTo(at(monday.plusDays(1), 17, 20));
    assertThat(result.reason()).isEqualTo("CALCULATION_PENDING");
  }

  @Test
  void rankingStillAnnouncesCalculationAfterAllPricesAreCollected() {
    calendar.stream()
        .filter(MarketTradingDay::isTrading)
        .forEach(day -> day.collected(at(monday.plusDays(7), 14, 30)));

    var result = service.ranking(monday, monday.plusDays(4), at(monday.plusDays(7), 15, 0));

    assertThat(result.baseDate()).isEqualTo(monday.plusDays(4));
    assertThat(result.expectedAt()).isEqualTo(at(monday.plusDays(7), 17, 20));
    assertThat(result.reason()).isEqualTo("CALCULATION_PENDING");
    assertThat(result.firstReturnExpectedAt()).isNull();
    assertThat(service.ranking(monday, null, at(monday.plusDays(7), 15, 0))).isNull();
  }

  @Test
  void rankingPrioritizesPendingCalculationOverLaterPriceCollection() {
    calendar.getFirst().collected(at(monday.plusDays(1), 14, 30));

    var result = service.ranking(monday, monday, at(monday.plusDays(1), 15, 0));

    assertThat(result.baseDate()).isEqualTo(monday);
    assertThat(result.expectedAt()).isEqualTo(at(monday.plusDays(1), 17, 20));
    assertThat(result.reason()).isEqualTo("CALCULATION_PENDING");
  }

  @Test
  void rankingKeepsEarlierUncollectedDayAndIncompleteCalendar() {
    var now = at(monday.plusDays(2), 15, 0);
    calendar.get(1).collected(now);
    var result = service.ranking(monday, monday.plusDays(1), now);
    assertThat(result.baseDate()).isEqualTo(monday);
    assertThat(result.reason()).isEqualTo("PRICE_COLLECTION_DELAYED");

    given(repository.findAllByBaseDateBetweenOrderByBaseDate(monday, monday.plusDays(6)))
        .willReturn(List.of());
    assertThat(service.ranking(monday, monday, now).reason()).isEqualTo("CALENDAR_PENDING");
  }

  @ParameterizedTest
  @CsvSource({"false,true", "true,false", "false,false"})
  void disabledSchedulesDoNotPromiseCollectionOrFirstReturn(
      boolean priceEnabled, boolean calculationEnabled) {
    var disabled =
        new LeagueAggregationService(
            repository,
            "0 30 14,18 * * MON-FRI",
            "0 20 17,19 * * MON-SAT",
            "0 30 7 * * TUE-SAT",
            "0 0 7,16 * * MON-SAT",
            priceEnabled,
            calculationEnabled,
            true);

    var result = disabled.estimate(monday, profile(monday), Map.of(), at(monday, 12, 0));

    assertThat(result.reason()).isEqualTo("COLLECTION_PAUSED");
    assertThat(result.expectedAt()).isNull();
    assertThat(result.firstReturnExpectedAt()).isNull();
  }

  @Test
  void collectedPricesCanStillBeCalculatedWhenPriceSyncIsDisabled() {
    var disabled =
        new LeagueAggregationService(
            repository,
            "0 30 14,18 * * MON-FRI",
            "0 20 17,19 * * MON-SAT",
            "0 30 7 * * TUE-SAT",
            "0 0 7,16 * * MON-SAT",
            false,
            true,
            true);
    calendar.getFirst().collected(at(monday.plusDays(1), 14, 30));

    var result =
        disabled.estimate(monday, profile(monday), Map.of(), at(monday.plusDays(1), 15, 0));

    assertThat(result.reason()).isEqualTo("CALCULATION_PENDING");
    assertThat(result.expectedAt()).isEqualTo(at(monday.plusDays(1), 17, 20));
    assertThat(result.firstReturnExpectedAt()).isNull();
  }

  @Test
  void disabledCalculationDoesNotPromiseRankingTimeAfterPriceCollection() {
    var disabled =
        new LeagueAggregationService(
            repository,
            "0 30 14,18 * * MON-FRI",
            "0 20 17,19 * * MON-SAT",
            "0 30 7 * * TUE-SAT",
            "0 0 7,16 * * MON-SAT",
            true,
            false,
            true);
    calendar.stream()
        .filter(MarketTradingDay::isTrading)
        .forEach(day -> day.collected(at(monday.plusDays(7), 14, 30)));

    var result = disabled.ranking(monday, monday, at(monday.plusDays(7), 15, 0));

    assertThat(result.reason()).isEqualTo("COLLECTION_PAUSED");
    assertThat(result.expectedAt()).isNull();
    assertThat(result.firstReturnExpectedAt()).isNull();
  }

  @Test
  void fridayUsCloseAnnouncesSaturdayInsteadOfWaitingUntilMonday() {
    for (MarketTradingDay day : calendar) {
      day.reviseUsTrading(day.getBaseDate().getDayOfWeek().getValue() <= 5);
      if (day.getBaseDate().isBefore(monday.plusDays(4)) && Boolean.TRUE.equals(day.getUsTrading()))
        day.usCollected(day.usClosesAt().plusSeconds(1));
    }
    var result = service.ranking(monday, null, at(monday.plusDays(5), 8, 0), true, false);
    assertThat(result.baseDate()).isEqualTo(monday.plusDays(4));
    assertThat(result.expectedAt()).isEqualTo(at(monday.plusDays(5), 17, 20));
    assertThat(result.delayed()).isFalse();
    var collecting = service.ranking(monday, null, at(monday.plusDays(5), 16, 30), true, false);
    assertThat(collecting.expectedAt()).isEqualTo(at(monday.plusDays(5), 17, 20));
    var delayed = service.ranking(monday, null, at(monday.plusDays(5), 18, 0), true, false);
    assertThat(delayed.expectedAt()).isEqualTo(at(monday.plusDays(7), 17, 20));
    assertThat(delayed.delayed()).isTrue();
  }

  @Test
  void mixedFirstBaselineAndFirstReturnWaitForBothMarkets() {
    for (MarketTradingDay day : calendar) day.reviseUsTrading(day.isTrading());
    given(
            repository.findAllByBaseDateBetweenOrderByBaseDate(
                monday.plusDays(1), monday.plusDays(14)))
        .willReturn(calendar.subList(1, calendar.size()));
    var result = service.estimate(monday, profile(monday), Map.of(), at(monday, 12, 0), true, true);
    assertThat(result.expectedAt()).isEqualTo(at(monday.plusDays(1), 17, 20));
    assertThat(result.firstReturnExpectedAt()).isEqualTo(at(monday.plusDays(2), 17, 20));
  }

  @Test
  void fxWaitAnnouncesTheUsRefreshSchedule() {
    for (MarketTradingDay day : calendar) {
      day.reviseUsTrading(day.isTrading());
      if (day.isTrading()) day.usCollected(day.usClosesAt().plusSeconds(1));
    }
    var missing = new PortfolioDailyReturn(SharedPortfolio.create(mock(Member.class)), monday);
    missing.exclude("EXCHANGE_RATE_PENDING", Instant.now());
    var result =
        service.estimate(
            monday,
            profile(monday.minusWeeks(1)),
            Map.of(monday, missing),
            at(monday.plusDays(1), 8, 0),
            true,
            false);
    assertThat(result.reason()).isEqualTo("EXCHANGE_RATE_PENDING");
    assertThat(result.expectedAt()).isEqualTo(at(monday.plusDays(1), 17, 20));
  }

  private InvestorProfile profile(LocalDate joined) {
    var profile =
        InvestorProfile.create(
            mock(Member.class),
            "투자자",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    profile.changePublication(true, true, true, true, at(joined, 10, 0));
    return profile;
  }

  private Instant at(LocalDate date, int hour, int minute) {
    return date.atTime(hour, minute).atZone(seoul).toInstant();
  }
}
