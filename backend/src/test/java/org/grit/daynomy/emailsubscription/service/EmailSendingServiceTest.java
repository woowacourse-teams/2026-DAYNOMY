package org.grit.daynomy.emailsubscription.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.grit.daynomy.emailsubscription.external.ResendEmailClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EmailSendingServiceTest {
  private final EmailDeliveryService deliveries = mock(EmailDeliveryService.class);
  private final ResendEmailClient client = mock(ResendEmailClient.class);
  private final Instant now = Instant.parse("2026-10-26T00:00:00Z");
  private final EmailSendingService service =
      new EmailSendingService(deliveries, client, Clock.fixed(now, ZoneOffset.UTC));
  private final UUID id = UUID.randomUUID();
  private final EmailDeliveryService.Attempt attempt =
      new EmailDeliveryService.Attempt(
          id,
          "google@example.invalid",
          "제목",
          "내용",
          "<p>내용</p>",
          "https://example.invalid/unsubscribe?token=test");

  @Test
  void skipsExternalRequestWhenAlreadySendingUnsubscribedOrNotDue() {
    when(deliveries.claim(id, now)).thenReturn(Optional.empty());
    service.send(id);
    verifyNoInteractions(client);
  }

  @ParameterizedTest
  @EnumSource(ResendEmailClient.Status.class)
  void claimsBeforeExternalRequestAndRecordsOnlyConfirmedResult(ResendEmailClient.Status status) {
    when(deliveries.claim(id, now)).thenReturn(Optional.of(attempt));
    when(client.send(attempt))
        .thenReturn(
            new ResendEmailClient.Result(
                status, status == ResendEmailClient.Status.ACCEPTED ? "provider-id" : null));
    service.send(id);
    var ordered = inOrder(deliveries, client);
    ordered.verify(deliveries).claim(id, now);
    ordered.verify(client).send(attempt);
    switch (status) {
      case ACCEPTED -> ordered.verify(deliveries).accepted(id, "provider-id", now);
      case RETRYABLE_FAILURE -> ordered.verify(deliveries).failed(id, true, now);
      case PERMANENT_FAILURE -> ordered.verify(deliveries).failed(id, false, now);
      case UNKNOWN -> ordered.verify(deliveries).unknown(id);
    }
    ordered.verifyNoMoreInteractions();
  }

  @Test
  void unexpectedClientFailureCannotMarkMailAsUnacceptedOrRetryIt() {
    when(deliveries.claim(id, now)).thenReturn(Optional.of(attempt));
    when(client.send(attempt)).thenThrow(new IllegalStateException("private-content"));
    service.send(id);
    verify(deliveries).unknown(id);
    verify(deliveries, never()).failed(any(), anyBoolean(), any());
  }

  @Test
  void persistenceFailureAfterAcceptanceDoesNotTriggerResendOrMarkAsFailed() {
    when(deliveries.claim(id, now)).thenReturn(Optional.of(attempt));
    when(client.send(attempt))
        .thenReturn(new ResendEmailClient.Result(ResendEmailClient.Status.ACCEPTED, "provider-id"));
    doThrow(new IllegalStateException("database-failure"))
        .when(deliveries)
        .accepted(id, "provider-id", now);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.send(id))
        .isInstanceOf(IllegalStateException.class);
    verify(client, times(1)).send(attempt);
    verify(deliveries, never()).failed(any(), anyBoolean(), any());
    verify(deliveries, never()).unknown(any());
  }

  @Test
  void doesNotStartHttpRequestIfDatabaseWaitPassesTenOClock() {
    var clock = mock(Clock.class);
    var cutoff = Instant.parse("2026-10-26T01:00:00Z");
    when(clock.instant()).thenReturn(now, cutoff);
    when(deliveries.claim(id, now)).thenReturn(Optional.of(attempt));
    new EmailSendingService(deliveries, client, clock).send(id);
    verify(deliveries).failed(id, false, cutoff);
    verifyNoInteractions(client);
  }
}
