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

class LeagueAggregationServiceTest {
  private final MarketTradingDayRepository repository = mock(MarketTradingDayRepository.class);
  private final LeagueAggregationService service =
      new LeagueAggregationService(
          repository, "0 30 14,18 * * MON-FRI", "0 20 17 * * MON-FRI", true, true);
  private final LocalDate monday = LocalDate.of(2026, 10, 19);
  private final ZoneId seoul = ZoneId.of("Asia/Seoul");
  private List<MarketTradingDay> calendar;

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
