package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.emailsubscription.domain.EmailDelivery;
import org.grit.daynomy.emailsubscription.domain.EmailSubscription;
import org.grit.daynomy.emailsubscription.domain.EmailSubscriptionStatus;
import org.grit.daynomy.emailsubscription.external.ResendEmailClient;
import org.grit.daynomy.emailsubscription.repository.EmailDeliveryRepository;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.repository.InvestmentEventRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class EmailDeliveryFlowTest {
  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
    registry.add("email.delivery.unsubscribe-secret", () -> "test-only-secret-at-least-32-bytes");
    registry.add("email.delivery.public-base-url", () -> "http://localhost:8080");
  }

  @Autowired private EmailDeliveryService service;
  @Autowired private EmailDeliveryRepository deliveries;
  @Autowired private EmailSubscriptionRepository subscriptions;
  @Autowired private MemberRepository members;
  @Autowired private InvestmentEventRepository events;
  @Autowired private EmailUnsubscribeService unsubscribe;

  @Test
  void preparationSendingAcceptanceAndUnsubscribeUseCommittedStateWithoutDuplicateSending() {
    var now = Instant.parse("2026-10-26T00:00:00Z");
    var member = subscriber(now.minusSeconds(7200));
    upcomingEvent(now);
    var id = service.prepare(member.getId(), now.minusSeconds(7200), List.of(), now).orElseThrow();
    assertThat(service.prepare(member.getId(), now.minusSeconds(7200), List.of(), now))
        .contains(id);
    var client = mock(ResendEmailClient.class);
    when(client.send(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(deliveries.findById(id).orElseThrow().getStatus())
                  .isEqualTo(EmailDelivery.Status.SENDING);
              return new ResendEmailClient.Result(
                  ResendEmailClient.Status.ACCEPTED, "accepted-message");
            });
    sender(client, now).send(id);
    sender(client, now).send(id);
    var sent = deliveries.findById(id).orElseThrow();
    assertThat(sent.getStatus()).isEqualTo(EmailDelivery.Status.ACCEPTED);
    assertThat(sent.getAcceptedAt()).isEqualTo(now);
    assertThat(sent.getProviderMessageId()).isEqualTo("accepted-message");
    var attempt = ArgumentCaptor.forClass(EmailDeliveryService.Attempt.class);
    verify(client, times(1)).send(attempt.capture());
    assertThat(attempt.getValue().recipient()).isEqualTo(member.getEmail());
    assertThat(attempt.getValue().body()).contains("경제 일정", "구독 해지");
    var token = sent.getUnsubscribeUrl().split("\\?token=", 2)[1];
    unsubscribe.unsubscribe(token);
    assertThat(subscriptions.findById(member.getId()).orElseThrow().getStatus())
        .isEqualTo(EmailSubscriptionStatus.UNSUBSCRIBED);
    var tomorrow = now.plusSeconds(86400);
    assertThat(service.prepare(member.getId(), tomorrow.minusSeconds(7200), List.of(), tomorrow))
        .isEmpty();
  }

  @Test
  void retriesAtNineTenWithTheSamePayloadAndCancelsQueuedMailAfterUnsubscribe() {
    var now = Instant.parse("2026-10-25T00:00:00Z");
    var member = subscriber(now.minusSeconds(7200));
    upcomingEvent(now);
    var id = service.prepare(member.getId(), now.minusSeconds(7200), List.of(), now).orElseThrow();
    var client = mock(ResendEmailClient.class);
    when(client.send(any()))
        .thenReturn(
            new ResendEmailClient.Result(ResendEmailClient.Status.RETRYABLE_FAILURE, null),
            new ResendEmailClient.Result(ResendEmailClient.Status.ACCEPTED, "retry-accepted"));
    sender(client, now).send(id);
    assertThat(deliveries.findById(id).orElseThrow().getStatus())
        .isEqualTo(EmailDelivery.Status.RETRY_PENDING);
    sender(client, now.plusSeconds(300)).send(id);
    verify(client, times(1)).send(any());
    sender(client, now.plusSeconds(600)).send(id);
    var attempts = ArgumentCaptor.forClass(EmailDeliveryService.Attempt.class);
    verify(client, times(2)).send(attempts.capture());
    assertThat(attempts.getAllValues().getFirst()).isEqualTo(attempts.getAllValues().getLast());
    assertThat(deliveries.findById(id).orElseThrow().getAttempts()).isEqualTo(2);

    var other = subscriber(now.minusSeconds(7200));
    var queued =
        service.prepare(other.getId(), now.minusSeconds(7200), List.of(), now).orElseThrow();
    var link = deliveries.findById(queued).orElseThrow().getUnsubscribeUrl();
    unsubscribe.unsubscribe(link.split("\\?token=", 2)[1]);
    sender(client, now).send(queued);
    assertThat(deliveries.findById(queued).orElseThrow().getStatus())
        .isEqualTo(EmailDelivery.Status.CANCELLED);
    verify(client, times(2)).send(any());
  }

  private EmailSendingService sender(ResendEmailClient client, Instant at) {
    return new EmailSendingService(service, client, Clock.fixed(at, ZoneOffset.UTC));
  }

  private Member subscriber(Instant start) {
    var key = UUID.randomUUID().toString();
    var member =
        members.save(
            Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
    var subscription = new EmailSubscription(member);
    subscription.subscribe(start);
    subscriptions.save(subscription);
    return member;
  }

  private void upcomingEvent(Instant now) {
    events.save(
        new InvestmentEvent(
            InvestmentEventType.US_CPI,
            "경제 일정",
            now.plusSeconds(86400 + 7200),
            null,
            null,
            "%",
            "공식 발표",
            "https://example.invalid/releases",
            UUID.randomUUID().toString(),
            null));
  }
}
