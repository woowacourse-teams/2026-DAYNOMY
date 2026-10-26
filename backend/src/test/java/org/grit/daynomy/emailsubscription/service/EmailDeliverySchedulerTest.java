package org.grit.daynomy.emailsubscription.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.grit.daynomy.emailsubscription.domain.ImportantStockSelection;
import org.grit.daynomy.emailsubscription.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailDeliverySchedulerTest {
  private final EmailSubscriptionRepository subscriptions = mock(EmailSubscriptionRepository.class);
  private final ImportantStockSelectionService selection =
      mock(ImportantStockSelectionService.class);
  private final EmailAnalysisExecutionRepository analyses =
      mock(EmailAnalysisExecutionRepository.class);
  private final EmailDeliveryService deliveries = mock(EmailDeliveryService.class);
  private final EmailDeliveryRepository jobs = mock(EmailDeliveryRepository.class);
  private final EmailSendingService sending = mock(EmailSendingService.class);
  private final Instant now = Instant.parse("2026-10-26T00:00:00Z");
  private final Instant asOf = now.minusSeconds(7200);
  private final LocalDate date = LocalDate.of(2026, 10, 26);

  @Test
  void preparesOnlySuccessfulResultsForEachMembersOwnSelectedStocks() {
    when(subscriptions.findEligibleMemberIds(eq(0L), any())).thenReturn(List.of(1L, 2L, 3L));
    when(selection.select(1L, date)).thenReturn(Optional.of(selected(10L, 20L)));
    when(selection.select(2L, date)).thenReturn(Optional.of(selected(30L)));
    when(selection.select(3L, date)).thenReturn(Optional.of(selected(40L)));
    when(analyses.findSuccessfulAnalysisIds(date, asOf, List.of(10L, 20L)))
        .thenReturn(List.of(100L));
    when(analyses.findSuccessfulAnalysisIds(date, asOf, List.of(30L))).thenReturn(List.of(300L));
    when(analyses.findSuccessfulAnalysisIds(date, asOf, List.of(40L))).thenReturn(List.of());
    scheduler(now).prepareDaily();
    verify(deliveries).prepare(1L, asOf, List.of(100L), now);
    verify(deliveries).prepare(2L, asOf, List.of(300L), now);
    verify(deliveries, never()).prepare(eq(3L), any(), anyList(), any());
    verify(subscriptions).findEligibleMemberIds(eq(3L), any());
    verifyNoInteractions(sending);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-10-25T23:59:59Z", "2026-10-26T01:00:00Z", "2026-10-26T15:00:00Z"})
  void neitherPreparesNorDispatchesOutsideNineToTen(String instant) {
    var scheduler = scheduler(Instant.parse(instant));
    scheduler.prepareDaily();
    scheduler.sendDue();
    verifyNoInteractions(subscriptions, selection, analyses, deliveries, jobs, sending);
  }

  @Test
  void processesOneDueJobAndUsesCurrentDaySoBacklogIsNotSent() {
    var id = UUID.randomUUID();
    when(jobs.findDueIds(eq(date), eq(now), anyList(), any())).thenReturn(List.of(id));
    scheduler(now).sendDue();
    verify(sending).send(id);
    verifyNoInteractions(subscriptions, selection, analyses, deliveries);
  }

  @Test
  void oneMembersFailureDoesNotStopOthers() {
    when(subscriptions.findEligibleMemberIds(eq(0L), any())).thenReturn(List.of(1L, 2L));
    when(selection.select(1L, date)).thenThrow(new IllegalStateException("failure"));
    when(selection.select(2L, date)).thenReturn(Optional.of(selected(30L)));
    when(analyses.findSuccessfulAnalysisIds(date, asOf, List.of(30L))).thenReturn(List.of(300L));
    scheduler(now).prepareDaily();
    verify(deliveries).prepare(2L, asOf, List.of(300L), now);
  }

  private EmailDeliveryScheduler scheduler(Instant instant) {
    return new EmailDeliveryScheduler(
        subscriptions,
        selection,
        analyses,
        deliveries,
        jobs,
        sending,
        Clock.fixed(instant, ZoneOffset.UTC));
  }

  private ImportantStockSelection selected(Long... ids) {
    return new ImportantStockSelection(
        date.minusDays(3),
        date.minusDays(4),
        true,
        java.util.Arrays.stream(ids)
            .map(
                id ->
                    new ImportantStockSelection.Stock(
                        id, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE))
            .toList());
  }
}
