package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.emailsubscription.domain.EmailSubscription;
import org.grit.daynomy.emailsubscription.domain.EmailSubscriptionStatus;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmailUnsubscribeServiceTest {
  private final MemberRepository members = mock(MemberRepository.class);
  private final EmailSubscriptionRepository subscriptions = mock(EmailSubscriptionRepository.class);
  private final String secret = "test-only-secret-at-least-32-bytes";
  private final EmailUnsubscribeService service =
      new EmailUnsubscribeService(members, subscriptions, secret, "https://example.invalid", true);
  private final Instant at = Instant.parse("2026-10-26T00:00:00Z");
  private final Member member = mock(Member.class);
  private EmailSubscription subscription;

  @BeforeEach
  void setUp() {
    subscription = new EmailSubscription(member);
    subscription.subscribe(at);
    when(members.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
    when(subscriptions.findById(1L)).thenReturn(Optional.of(subscription));
  }

  @Test
  void signedLinkIsStableAndOpeningItDoesNotUnsubscribe() {
    String link = service.link(1L, at);
    assertThat(link).isEqualTo(service.link(1L, at));
    service.validate(token(link));
    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.SUBSCRIBED);
    verifyNoInteractions(members);
    assertThat(service.matches(link, 1L, at)).isTrue();
    assertThat(service.matches(link, 2L, at)).isFalse();
  }

  @Test
  void explicitUnsubscribeIsIdempotentAndLocksOnlyTheSignedMember() {
    var token = token(service.link(1L, at));
    service.unsubscribe(token);
    service.unsubscribe(token);
    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.UNSUBSCRIBED);
    verify(members, times(2)).findByIdForUpdate(1L);
  }

  @Test
  void tamperedMalformedAndOtherKeyTokensCannotReachMemberData() {
    var original = token(service.link(1L, at));
    for (String invalid :
        new String[] {
          "",
          "invalid",
          original + ".extra",
          "A" + original,
          original.substring(0, original.indexOf('.')) + ".AAAA"
        })
      assertThatThrownBy(() -> service.unsubscribe(invalid)).isInstanceOf(BusinessException.class);
    var other =
        new EmailUnsubscribeService(
            members,
            subscriptions,
            "other-secret-at-least-32-bytes-long",
            "https://example.invalid",
            true);
    assertThatThrownBy(() -> other.unsubscribe(original)).isInstanceOf(BusinessException.class);
    verifyNoInteractions(members);
  }

  @Test
  void previousSubscriptionLinkCannotUnsubscribeAfterResubscribe() {
    var previous = token(service.link(1L, at));
    subscription.unsubscribe();
    subscription.subscribe(at.plusSeconds(1));
    assertThatThrownBy(() -> service.unsubscribe(previous)).isInstanceOf(BusinessException.class);
    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.SUBSCRIBED);
    service.unsubscribe(token(service.link(1L, at.plusSeconds(1))));
    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.UNSUBSCRIBED);
  }

  @Test
  void withdrawnMemberNeedsNoPersonalDataResponseOrSubscriptionChange() {
    when(member.isWithdrawn()).thenReturn(true);
    service.unsubscribe(token(service.link(1L, at)));
    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.SUBSCRIBED);
    verifyNoInteractions(subscriptions);
  }

  @Test
  void enablingDeliveryRequiresSecretAndSafePublicOrigin() {
    assertThatThrownBy(
            () ->
                new EmailUnsubscribeService(
                    members, subscriptions, "short", "https://example.invalid", true))
        .isInstanceOf(IllegalArgumentException.class);
    for (String url :
        new String[] {
          "",
          "http://example.invalid",
          "https://example.invalid/path",
          "https://user:password@example.invalid",
          "https://example.invalid?token=private"
        })
      assertThatThrownBy(
              () -> new EmailUnsubscribeService(members, subscriptions, secret, url, true))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageNotContaining("password");
    assertThatCode(
            () ->
                new EmailUnsubscribeService(
                    members, subscriptions, secret, "http://localhost:8080", true))
        .doesNotThrowAnyException();
    assertThatCode(() -> new EmailUnsubscribeService(members, subscriptions, "", "", false))
        .doesNotThrowAnyException();
  }

  private String token(String link) {
    return link.substring(link.indexOf("?token=") + 7);
  }
}
