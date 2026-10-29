package org.grit.daynomy.emailsubscription.service;

import static org.grit.daynomy.emailsubscription.EmailFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.grit.daynomy.emailsubscription.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailDeliverySchedulerTest {
  final EmailSubscriptionRepository subscriptions = mock(EmailSubscriptionRepository.class);
  final EmailDeliveryService deliveries = mock(EmailDeliveryService.class);
  final EmailDeliveryRepository jobs = mock(EmailDeliveryRepository.class);
  final EmailSendingService sending = mock(EmailSendingService.class);

  @Test
  void preparesEachSubscriberWithoutRequiringAnyAnalysisResult() {
    when(subscriptions.findEligibleMemberIds(eq(0L), any())).thenReturn(List.of(1L, 2L));
    scheduler(NOW).prepareDaily();
    verify(deliveries).prepare(1L, NOW);
    verify(deliveries).prepare(2L, NOW);
    verifyNoInteractions(sending);
  }

  @ParameterizedTest
  @ValueSource(strings = {"2026-10-25T23:59:59Z", "2026-10-26T01:00:00Z"})
  void doesNothingOutsideSendingWindow(String at) {
    var scheduler = scheduler(Instant.parse(at));
    scheduler.prepareDaily();
    scheduler.sendDue();
    verifyNoInteractions(subscriptions, deliveries, jobs, sending);
  }

  @Test
  void sendsOneDueJobAndContinuesAfterAnotherMembersPreparationFails() {
    when(subscriptions.findEligibleMemberIds(eq(0L), any())).thenReturn(List.of(1L, 2L));
    when(deliveries.prepare(1L, NOW)).thenThrow(new IllegalStateException());
    scheduler(NOW).prepareDaily();
    verify(deliveries).prepare(2L, NOW);
    var id = UUID.randomUUID();
    when(jobs.findDueIds(eq(MAIL_DATE), eq(NOW), anyList(), any())).thenReturn(List.of(id));
    scheduler(NOW).sendDue();
    verify(sending).send(id);
  }

  private EmailDeliveryScheduler scheduler(Instant at) {
    return new EmailDeliveryScheduler(
        subscriptions, deliveries, jobs, sending, Clock.fixed(at, ZoneOffset.UTC));
  }
}
