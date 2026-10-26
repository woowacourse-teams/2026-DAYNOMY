package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.league.domain.*;
import org.grit.daynomy.league.repository.*;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.*;
import org.grit.daynomy.portfolio.repository.*;
import org.grit.daynomy.portfolio.service.PortfolioLedgerCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LeagueEnrollmentServiceTest {
  @Mock PortfolioRepository originalRepository;
  @Mock PortfolioHoldingHistoryRepository originalHistoryRepository;
  @Mock SharedPortfolioRepository portfolioRepository;
  @Mock SharedHoldingRepository holdingRepository;
  @Mock SharedHoldingHistoryRepository historyRepository;
  @Mock MarketTradingDayRepository tradingDayRepository;
  @Spy PortfolioLedgerCalculator ledgerCalculator = new PortfolioLedgerCalculator();
  @InjectMocks LeagueEnrollmentService service;
  private final LocalDate monday = LocalDate.of(2026, 10, 19);
  private final ZoneId seoul = ZoneId.of("Asia/Seoul");

  @Test
  void freezesThisWeeksSelectedHoldingsAndUsesEarlierHoldingsOnlyAsOpeningBalance() {
    var fixture = fixture();
    var before = history(fixture.original(), fixture.asset(), monday.minusDays(3), 1);
    var first = history(fixture.original(), fixture.asset(), monday, 2);
    var second = history(fixture.original(), fixture.asset(), monday.plusDays(1), 4);
    given(originalHistoryRepository.findOrderedWithAssetsByPortfolioId(2L))
        .willReturn(List.of(before, first, second));

    service.includeCurrentWeek(fixture.profile());

    assertThat(fixture.profile().eligibleFrom()).isEqualTo(monday);
    assertThat(fixture.profile().getLeagueEnabledAt()).isEqualTo(at(monday.plusDays(2), 10));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<SharedHoldingHistory>> copies = ArgumentCaptor.forClass(Iterable.class);
    then(historyRepository).should().saveAll(copies.capture());
    assertThat(copies.getValue())
        .extracting(SharedHoldingHistory::getQuantity)
        .containsExactly(1L, 2L, 4L);
    assertThat(copies.getValue())
        .allSatisfy(
            copy ->
                assertThat(copy.getEffectiveAt()).isAfterOrEqualTo(at(monday, 0).minusMillis(1)));
    ReflectionTestUtils.setField(first, "quantity", 100L);
    assertThat(copies.getValue())
        .extracting(SharedHoldingHistory::getQuantity)
        .containsExactly(1L, 2L, 4L);
  }

  @Test
  void recordAddedAfterMondayCloseUsesItsActualMondayTradeDate() {
    var fixture = fixture();
    var first = history(fixture.original(), fixture.asset(), monday, 2);
    ReflectionTestUtils.setField(first, "createdAt", at(monday, 16));
    ReflectionTestUtils.setField(first, "updatedAt", at(monday, 16));
    given(originalHistoryRepository.findOrderedWithAssetsByPortfolioId(2L))
        .willReturn(List.of(first));

    service.includeCurrentWeek(fixture.profile());

    assertThat(fixture.profile().eligibleFrom()).isEqualTo(monday);
  }

  @Test
  void lateBackdatedOrEditedRecordsAreFrozenFromTheirActualTradeDate() {
    var fixture = fixture();
    var first = history(fixture.original(), fixture.asset(), monday, 2);
    ReflectionTestUtils.setField(first, "updatedAt", at(monday.plusDays(2), 9));
    given(originalHistoryRepository.findOrderedWithAssetsByPortfolioId(2L))
        .willReturn(List.of(first));

    service.includeCurrentWeek(fixture.profile());

    then(historyRepository).should().saveAll(any());
    assertThat(fixture.profile().eligibleFrom()).isEqualTo(monday);
  }

  @Test
  void missingSnapshotsDoNotMoveTheStartPastDaysWithActualHoldings() {
    var fixture = fixture();
    var first = history(fixture.original(), fixture.asset(), monday.minusDays(6), 4);
    ReflectionTestUtils.setField(first, "createdAt", at(monday.plusDays(1), 10));
    ReflectionTestUtils.setField(first, "updatedAt", at(monday.plusDays(1), 10));
    given(originalHistoryRepository.findOrderedWithAssetsByPortfolioId(2L))
        .willReturn(List.of(first));

    service.includeCurrentWeek(fixture.profile());

    assertThat(fixture.profile().eligibleFrom()).isEqualTo(monday);
    then(historyRepository).should().saveAll(any());
  }

  @Test
  void holdingsThatBeginTuesdayDoNotInventMondayPerformanceOrCopyPrivateAssets() {
    var fixture = fixture();
    var privateAsset = new Asset("비공개 종목", AssetCategory.STOCK, "PRIVATE");
    ReflectionTestUtils.setField(privateAsset, "id", 5L);
    given(originalHistoryRepository.findOrderedWithAssetsByPortfolioId(2L))
        .willReturn(
            List.of(
                history(fixture.original(), privateAsset, monday.minusDays(3), 100),
                history(fixture.original(), fixture.asset(), monday.plusDays(1), 4)));

    service.includeCurrentWeek(fixture.profile());

    assertThat(fixture.profile().eligibleFrom()).isEqualTo(monday.plusDays(1));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Iterable<SharedHoldingHistory>> copies = ArgumentCaptor.forClass(Iterable.class);
    then(historyRepository).should().saveAll(copies.capture());
    assertThat(copies.getValue()).extracting(copy -> copy.getAsset().getId()).containsOnly(4L);
  }

  @Test
  void reenrollmentNeverBackfillsTheSameWeekAgain() {
    var member = mock(Member.class);
    var profile =
        InvestorProfile.create(
            member,
            "투자자",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    profile.changePublication(true, true, true, true, at(monday, 10));
    profile.changePublication(false, false, false, false, at(monday.plusDays(1), 10));
    profile.changePublication(true, true, true, true, at(monday.plusDays(2), 10));

    service.includeCurrentWeek(profile);

    then(originalRepository).shouldHaveNoInteractions();
    then(historyRepository).shouldHaveNoInteractions();
    assertThat(profile.eligibleFrom()).isEqualTo(monday.plusDays(2));
  }

  private Fixture fixture() {
    var member = mock(Member.class);
    given(member.getId()).willReturn(1L);
    var profile =
        InvestorProfile.create(
            member,
            "투자자",
            "",
            LeagueTypes.ExperienceLevel.BEGINNER,
            LeagueTypes.RiskProfile.BALANCED);
    profile.changePublication(true, true, true, true, at(monday.plusDays(2), 10));
    var original = Portfolio.create(member);
    ReflectionTestUtils.setField(original, "id", 2L);
    var shared = SharedPortfolio.create(member);
    ReflectionTestUtils.setField(shared, "id", 3L);
    var asset = new Asset("종목", AssetCategory.STOCK, "TEST");
    ReflectionTestUtils.setField(asset, "id", 4L);
    given(originalRepository.findByMemberId(1L)).willReturn(Optional.of(original));
    given(portfolioRepository.findByMemberId(1L)).willReturn(Optional.of(shared));
    given(holdingRepository.findAllByPortfolioIdOrderById(3L))
        .willReturn(List.of(new SharedHolding(shared, asset, 4, BigDecimal.TEN)));
    given(tradingDayRepository.findAllByBaseDateBetweenOrderByBaseDate(monday, monday.plusDays(2)))
        .willReturn(
            List.of(
                new MarketTradingDay(monday, true),
                new MarketTradingDay(monday.plusDays(1), true),
                new MarketTradingDay(monday.plusDays(2), true)));
    return new Fixture(profile, original, asset);
  }

  private PortfolioHoldingHistory history(
      Portfolio portfolio, Asset asset, LocalDate day, long quantity) {
    var history =
        new PortfolioHoldingHistory(
            portfolio,
            asset,
            PortfolioHoldingChangeType.ADDED,
            quantity,
            BigDecimal.TEN,
            day,
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO);
    ReflectionTestUtils.setField(history, "createdAt", at(day, 10));
    ReflectionTestUtils.setField(history, "updatedAt", at(day, 10));
    return history;
  }

  private Instant at(LocalDate day, int hour) {
    return day.atTime(hour, 0).atZone(seoul).toInstant();
  }

  private record Fixture(InvestorProfile profile, Portfolio original, Asset asset) {}
}
