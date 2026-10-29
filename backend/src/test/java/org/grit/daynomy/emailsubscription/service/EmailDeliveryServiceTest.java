package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.emailsubscription.EmailFixtures.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.grit.daynomy.emailsubscription.domain.*;
import org.grit.daynomy.emailsubscription.repository.*;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailDeliveryServiceTest {
  final MemberRepository members = mock(MemberRepository.class);
  final EmailSubscriptionRepository subscriptions = mock(EmailSubscriptionRepository.class);
  final EmailDeliveryRepository deliveries = mock(EmailDeliveryRepository.class);
  final EmailDigestService digests = mock(EmailDigestService.class);
  final EmailUnsubscribeService unsubscribe = mock(EmailUnsubscribeService.class);
  final EmailDeliveryService service =
      new EmailDeliveryService(members, subscriptions, deliveries, digests, unsubscribe);
  final org.grit.daynomy.member.domain.Member member = member();

  @BeforeEach
  void setup() {
    ReflectionTestUtils.setField(member, "id", 1L);
  }

  private void subscribed() {
    var sub = new EmailSubscription(member);
    sub.subscribe(NOW.minusSeconds(60));
    when(members.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
    when(subscriptions.findById(1L)).thenReturn(Optional.of(sub));
  }

  @Test
  void preparesOnlyNewDataAndKeepsOneJobPerMemberPerDay() {
    subscribed();
    when(digests.compose(1L, NOW))
        .thenReturn(
            Optional.of(
                new EmailDigestService.Prepared(digest(1L), EmailDigestSupplement.empty())));
    when(unsubscribe.link(anyLong(), any()))
        .thenReturn("https://example.invalid/unsubscribe?token=test");
    when(deliveries.save(any())).thenAnswer(i -> i.getArgument(0));
    var id = service.prepare(1L, NOW).orElseThrow();
    when(deliveries.findByMemberIdAndDeliveryDate(1L, MAIL_DATE))
        .thenReturn(Optional.of(delivery()));
    assertThat(service.prepare(1L, NOW)).isPresent();
    verify(digests, times(1)).compose(1L, NOW);
    assertThat(id).isNotNull();
  }

  @Test
  void doesNotPrepareWithoutNewDataOrOutsideTheWindow() {
    subscribed();
    when(digests.compose(1L, NOW)).thenReturn(Optional.empty());
    assertThat(service.prepare(1L, NOW)).isEmpty();
    assertThat(service.prepare(1L, NOW.minusSeconds(1))).isEmpty();
    assertThat(service.prepare(1L, NOW.plusSeconds(3600))).isEmpty();
    verify(deliveries, never()).save(any());
  }

  @Test
  void rechecksHoldingsAndBlocksLegacyAiMessages() {
    subscribed();
    var d = new EmailDelivery(member, NOW, digest(1L), MAIL_DATE);
    d.attachUnsubscribeLink("https://example.invalid/unsubscribe");
    d.completeTemplate("회원", "https://daynomy.com");
    when(deliveries.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
    when(unsubscribe.matches(anyString(), eq(1L), any())).thenReturn(true);
    when(digests.stillValid(anyLong(), any(), any(), any())).thenReturn(false);
    assertThat(service.claim(d.getId(), NOW)).isEmpty();
    assertThat(d.getStatus()).isEqualTo(EmailDelivery.Status.CANCELLED);
    var old = new EmailDelivery(member, NOW, digest(1L), MAIL_DATE);
    old.attachUnsubscribeLink("https://example.invalid/unsubscribe");
    ReflectionTestUtils.setField(old, "analysisIds", List.of(10L));
    when(deliveries.findByIdForUpdate(old.getId())).thenReturn(Optional.of(old));
    assertThat(service.claim(old.getId(), NOW)).isEmpty();
    assertThat(old.getStatus()).isEqualTo(EmailDelivery.Status.CANCELLED);
  }

  @Test
  void claimedPayloadUsesAccountEmailAndPersistedHtmlAndText() {
    subscribed();
    var d = new EmailDelivery(member, NOW, digest(1L), MAIL_DATE);
    d.attachUnsubscribeLink("https://example.invalid/unsubscribe");
    d.completeTemplate("회원", "https://daynomy.com");
    when(deliveries.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
    when(unsubscribe.matches(anyString(), eq(1L), any())).thenReturn(true);
    when(digests.stillValid(anyLong(), any(), any(), any())).thenReturn(true);
    var attempt = service.claim(d.getId(), NOW).orElseThrow();
    assertThat(attempt.recipient()).isEqualTo(member.getEmail());
    assertThat(attempt.htmlBody()).isEqualTo(d.getHtmlBody());
    assertThat(attempt.body()).isEqualTo(d.getBody());
  }
}
