package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.emailsubscription.domain.*;
import org.grit.daynomy.emailsubscription.repository.*;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailDeliveryServiceTest {
  private final MemberRepository members = mock(MemberRepository.class);
  private final EmailSubscriptionRepository subscriptions = mock(EmailSubscriptionRepository.class);
  private final EmailDeliveryRepository deliveries = mock(EmailDeliveryRepository.class);
  private final EmailStockDigestService digests = mock(EmailStockDigestService.class);
  private final EmailDeliveryService service =
      new EmailDeliveryService(members, subscriptions, deliveries, digests);
  private final Instant now = Instant.parse("2026-10-26T00:00:00Z");
  private final Instant asOf = now.minusSeconds(7200);
  private final Member member = mock(Member.class);
  private EmailSubscription subscription;

  @BeforeEach
  void setUp() {
    when(member.getId()).thenReturn(1L);
    when(member.getEmail()).thenReturn("google@example.invalid");
    when(members.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
    subscription = new EmailSubscription(member);
    subscription.subscribe(asOf);
    when(subscriptions.findById(1L)).thenReturn(Optional.of(subscription));
  }

  @Test
  void createsOnlyOneJobPerMemberDate() {
    var delivery = delivery();
    when(deliveries.findByMemberIdAndDeliveryDate(1L, LocalDate.of(2026, 10, 26)))
        .thenReturn(Optional.of(delivery));
    assertThat(service.prepare(1L, asOf, List.of(1L), now)).contains(delivery.getId());
    verifyNoInteractions(digests);
    verify(deliveries, never()).save(any());
  }

  @Test
  void excludesAlreadyAcceptedOrUncertainAnalysisIds() {
    when(deliveries.findByMemberIdAndDeliveryDate(anyLong(), any())).thenReturn(Optional.empty());
    when(deliveries.findCoveredAnalysisIds(1L, List.of(1L, 2L))).thenReturn(List.of(1L));
    when(digests.compose(1L, asOf, List.of(2L))).thenReturn(Optional.empty());
    assertThat(service.prepare(1L, asOf, List.of(1L, 2L), now)).isEmpty();
    verify(digests).compose(1L, asOf, List.of(2L));
  }

  @Test
  void claimsOnlyCurrentContentAndUsesServerGoogleEmail() {
    var delivery = delivery();
    when(deliveries.findByIdForUpdate(delivery.getId())).thenReturn(Optional.of(delivery));
    when(digests.compose(1L, asOf, List.of(1L))).thenReturn(Optional.of(digest("근거")));
    var attempt = service.claim(delivery.getId(), now).orElseThrow();
    assertThat(attempt.recipient()).isEqualTo("google@example.invalid");
    assertThat(attempt.deliveryId()).isEqualTo(delivery.getId());
    assertThat(service.claim(delivery.getId(), now.plusSeconds(1))).isEmpty();
  }

  @Test
  void cancelsUnsubscribedAndStaleBodiesBeforeSending() {
    var delivery = delivery();
    when(deliveries.findByIdForUpdate(delivery.getId())).thenReturn(Optional.of(delivery));
    subscription.unsubscribe();
    assertThat(service.claim(delivery.getId(), now)).isEmpty();
    assertThat(delivery.getStatus()).isEqualTo(EmailDelivery.Status.CANCELLED);
    verifyNoInteractions(digests);
    var stale = delivery();
    subscription.subscribe(now);
    when(deliveries.findByIdForUpdate(stale.getId())).thenReturn(Optional.of(stale));
    when(digests.compose(1L, asOf, List.of(1L))).thenReturn(Optional.of(digest("변경된 근거")));
    assertThat(service.claim(stale.getId(), now)).isEmpty();
    assertThat(stale.getStatus()).isEqualTo(EmailDelivery.Status.CANCELLED);
  }

  @Test
  void refusesOldDayPreparationAndPreparationAfterTen() {
    assertThat(service.prepare(1L, asOf.minusSeconds(86400), List.of(1L), now)).isEmpty();
    assertThat(service.prepare(1L, asOf, List.of(1L), now.plusSeconds(3600))).isEmpty();
    verifyNoInteractions(deliveries, digests);
  }

  private EmailDelivery delivery() {
    return new EmailDelivery(member, asOf, digest("근거"), LocalDate.of(2026, 10, 26));
  }

  private EmailStockDigest digest(String reason) {
    var content = new CommonStockAnalysisContent(null, null, "소식", "", "", reason, List.of());
    return new EmailStockDigest(
        LocalDate.of(2026, 10, 23),
        LocalDate.of(2026, 10, 22),
        List.of(new EmailStockDigest.Stock(1L, 10L, "종목", BigDecimal.TEN, null, null, content)));
  }
}
