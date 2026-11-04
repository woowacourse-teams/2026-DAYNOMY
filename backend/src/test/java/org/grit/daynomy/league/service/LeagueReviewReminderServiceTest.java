package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.domain.*;
import org.grit.daynomy.league.domain.LeagueTypes.*;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.repository.*;
import org.grit.daynomy.member.domain.Member;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LeagueReviewReminderServiceTest {
  private static final Instant NOW = Instant.parse("2026-11-02T00:00:00Z");
  @Mock InvestorProfileRepository profiles;
  @Mock SharedPortfolioRepository portfolios;
  @Mock InvestmentDecisionRepository decisions;
  @Mock PortfolioDailyReturnRepository returns;
  @Mock LeagueReviewReminderRepository reminders;
  private LeagueReviewReminderService service;
  private InvestorProfile profile;
  private SharedPortfolio portfolio;
  private final UUID key = UUID.randomUUID();

  @BeforeEach
  void setup() {
    service =
        new LeagueReviewReminderService(
            profiles, portfolios, decisions, returns, reminders, Clock.fixed(NOW, ZoneOffset.UTC));
    var member = Member.createGoogleMember("test", "test@example.invalid", "test", null);
    profile =
        InvestorProfile.create(member, "test", "", ExperienceLevel.BEGINNER, RiskProfile.BALANCED);
    ReflectionTestUtils.setField(profile, "id", 10L);
    portfolio = SharedPortfolio.create(member);
    ReflectionTestUtils.setField(portfolio, "id", 20L);
  }

  private void enrolled(Instant started) {
    profile.changePublication(true, true, true, false, started);
    ReflectionTestUtils.setField(profile.getParticipations().getLast(), "id", 30L);
    when(profiles.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(profile));
  }

  private void performance(boolean ready) {
    when(portfolios.findByMemberId(1L)).thenReturn(Optional.of(portfolio));
    when(returns.existsLatestEligibleByPortfolioIdAndBaseDateBetween(eq(20L), any(), any()))
        .thenReturn(ready);
  }

  private InvestmentDecision decision(long id) {
    var asset = new Asset("종목", AssetCategory.STOCK, "TEST");
    ReflectionTestUtils.setField(asset, "id", 3L);
    var decision =
        new InvestmentDecision(
            portfolio,
            asset,
            "key" + id,
            TransactionType.HOLD,
            1,
            BigDecimal.TEN,
            BigDecimal.ZERO,
            LocalDate.of(2026, 10, 20),
            "reason",
            HoldingPeriod.UNDER_ONE_MONTH,
            "expected",
            "condition",
            BigDecimal.TEN,
            false);
    ReflectionTestUtils.setField(decision, "id", id);
    return decision;
  }

  @Test
  void noProfileOrDisabledLeagueNeverNotifies() {
    assertThat(service.claim(1L, key).transactions()).isEmpty();
    when(profiles.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(profile));
    assertThat(service.claim(1L, key).transactions()).isEmpty();
    verifyNoInteractions(portfolios, decisions, returns, reminders);
  }

  @Test
  void waitsSevenFullDaysFromCurrentParticipation() {
    enrolled(NOW.minus(Duration.ofDays(7)).plusNanos(1));
    assertThat(service.claim(1L, key).transactions()).isEmpty();
    verifyNoInteractions(portfolios, decisions, returns, reminders);
  }

  @Test
  void exactlySevenDaysCombinesOldUnreviewedRecordsAndReplaysWithoutExtendingCooldown() {
    enrolled(NOW.minus(Duration.ofDays(7)));
    performance(true);
    when(decisions.findUnreviewedCreatedBefore(
            1L, NOW.minus(Duration.ofDays(7)), PageRequest.of(0, 20)))
        .thenReturn(List.of(decision(7), decision(8)));
    var state = new LeagueReviewReminder(10L, 30L);
    when(reminders.findById(10L)).thenReturn(Optional.of(state));
    var result = service.claim(1L, key);
    assertThat(result.transactions()).extracting(record -> record.id()).containsExactly(7L, 8L);
    assertThat(result.requestKey()).isEqualTo(key);
    assertThat(result.nextNotificationAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
    assertThat(service.claim(1L, key)).isEqualTo(result);
    assertThat(service.claim(1L, UUID.randomUUID()).transactions()).isEmpty();
    assertThat(state.getLastNotifiedAt()).isEqualTo(NOW);
  }

  @Test
  void missingPerformanceAndNoPendingRecordsDoNotConsumeReminder() {
    enrolled(NOW.minus(Duration.ofDays(8)));
    performance(false);
    assertThat(service.claim(1L, key).transactions()).isEmpty();
    verifyNoInteractions(decisions);
    verify(reminders, never()).save(any());
    when(returns.existsLatestEligibleByPortfolioIdAndBaseDateBetween(eq(20L), any(), any()))
        .thenReturn(true);
    when(decisions.findUnreviewedCreatedBefore(anyLong(), any(), any(Pageable.class)))
        .thenReturn(List.of());
    assertThat(service.claim(1L, key).transactions()).isEmpty();
    verify(reminders, never()).save(any());
  }

  @Test
  void rejoiningStartsAnotherSevenDayWaitingPeriod() {
    enrolled(NOW.minus(Duration.ofDays(30)));
    profile.changePublication(true, false, true, false, NOW.minus(Duration.ofDays(10)));
    profile.changePublication(true, true, true, false, NOW.minus(Duration.ofDays(1)));
    assertThat(service.claim(1L, key).transactions()).isEmpty();
    verifyNoInteractions(portfolios, decisions, returns, reminders);
  }

  @Test
  void snoozeExtendsFromCloseTimeAndIsIdempotentAndRejectsOtherDevicesKey() {
    enrolled(NOW.minus(Duration.ofDays(10)));
    var state = new LeagueReviewReminder(10L, 30L);
    state.claim(key, NOW.minus(Duration.ofDays(1)));
    when(reminders.findById(10L)).thenReturn(Optional.of(state));
    assertThat(service.snooze(1L, key).nextNotificationAt())
        .isEqualTo(NOW.plus(Duration.ofDays(7)));
    assertThat(service.snooze(1L, key).nextNotificationAt())
        .isEqualTo(NOW.plus(Duration.ofDays(7)));
    assertThat(service.claim(1L, key).transactions()).isEmpty();
    assertThat(state.accepts(key, NOW.plus(Duration.ofDays(8)))).isFalse();
    assertThat(state.accepts(UUID.randomUUID(), NOW.plus(Duration.ofDays(7)))).isTrue();
    assertThatThrownBy(() -> service.snooze(1L, UUID.randomUUID()))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void snoozeJustBeforeExpiryStartsSevenDaysFromTheCurrentTime() {
    enrolled(NOW.minus(Duration.ofDays(10)));
    var state = new LeagueReviewReminder(10L, 30L);
    state.claim(key, NOW.minus(Duration.ofDays(7)).plusNanos(1));
    when(reminders.findById(10L)).thenReturn(Optional.of(state));

    assertThat(service.snooze(1L, key).nextNotificationAt())
        .isEqualTo(NOW.plus(Duration.ofDays(7)));
  }

  @ParameterizedTest
  @ValueSource(longs = {0, 1, 86_400})
  void rejectsFirstSnoozeAtOrAfterExpiryWithoutChangingTheSchedule(long secondsAfterExpiry) {
    enrolled(NOW.minus(Duration.ofDays(20)));
    var state = new LeagueReviewReminder(10L, 30L);
    state.claim(key, NOW.minus(Duration.ofDays(7)).minusSeconds(secondsAfterExpiry));
    var originalNext = state.getNextNotificationAt();
    when(reminders.findById(10L)).thenReturn(Optional.of(state));

    assertThatThrownBy(() -> service.snooze(1L, key))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(LeagueErrorCode.REVIEW_REMINDER_EXPIRED));
    assertThat(state.getNextNotificationAt()).isEqualTo(originalNext);
    assertThat(state.isSnoozed()).isFalse();
  }

  @Test
  void alreadySnoozedRequestReplaysEvenAfterItsDeadlineWithoutExtendingTheSchedule() {
    enrolled(NOW.minus(Duration.ofDays(20)));
    var state = new LeagueReviewReminder(10L, 30L);
    state.claim(key, NOW.minus(Duration.ofDays(9)));
    state.snooze(NOW.minus(Duration.ofDays(8)));
    var originalNext = state.getNextNotificationAt();
    when(reminders.findById(10L)).thenReturn(Optional.of(state));

    assertThat(originalNext).isBefore(NOW);
    assertThat(service.snooze(1L, key).nextNotificationAt()).isEqualTo(originalNext);
    assertThat(state.getNextNotificationAt()).isEqualTo(originalNext);
  }

  @Test
  void cooldownExpiresAtSevenDaysAndParticipationChangesDiscardOldCooldown() {
    enrolled(NOW.minus(Duration.ofDays(20)));
    performance(true);
    when(decisions.findUnreviewedCreatedBefore(anyLong(), any(), any(Pageable.class)))
        .thenReturn(List.of(decision(7)));
    var state = new LeagueReviewReminder(10L, 30L);
    state.claim(UUID.randomUUID(), NOW.minus(Duration.ofDays(7)));
    when(reminders.findById(10L)).thenReturn(Optional.of(state));
    assertThat(service.claim(1L, key).transactions()).hasSize(1);
    state.useParticipation(29L);
    state.claim(UUID.randomUUID(), NOW);
    assertThat(service.claim(1L, UUID.randomUUID()).transactions()).hasSize(1);
    assertThat(state.getParticipationId()).isEqualTo(30L);
  }
}
