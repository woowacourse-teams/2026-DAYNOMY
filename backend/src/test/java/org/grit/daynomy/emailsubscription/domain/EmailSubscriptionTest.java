package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.grit.daynomy.member.domain.Member;
import org.junit.jupiter.api.Test;

class EmailSubscriptionTest {

  private static final Instant FIRST = Instant.parse("2026-10-19T00:00:00Z");
  private static final Instant SECOND = FIRST.plusSeconds(60);

  @Test
  void duplicateSubscriptionPreservesTheOriginalStartTime() {
    var subscription = subscription();
    subscription.subscribe(FIRST);
    subscription.subscribe(SECOND);

    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.SUBSCRIBED);
    assertThat(subscription.getSubscribedAt()).isEqualTo(FIRST);
  }

  @Test
  void resubscriptionStartsANewPeriod() {
    var subscription = subscription();
    subscription.subscribe(FIRST);
    subscription.unsubscribe();
    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.UNSUBSCRIBED);

    subscription.subscribe(SECOND);

    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.SUBSCRIBED);
    assertThat(subscription.getSubscribedAt()).isEqualTo(SECOND);
  }

  @Test
  void aDismissedInvitationCanBeSubscribedLater() {
    var subscription = subscription();
    subscription.dismiss();
    subscription.dismiss();
    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.DECLINED);

    subscription.subscribe(FIRST);

    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.SUBSCRIBED);
    assertThat(subscription.getSubscribedAt()).isEqualTo(FIRST);
  }

  @Test
  void anOldInvitationCannotOverwriteSubscriptionOrUnsubscription() {
    var subscription = subscription();
    subscription.subscribe(FIRST);
    subscription.dismiss();
    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.SUBSCRIBED);

    subscription.unsubscribe();
    subscription.dismiss();
    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.UNSUBSCRIBED);
  }

  @Test
  void unsubscribeDoesNotDecideAnUnansweredInvitationOrChangeADecline() {
    var subscription = subscription();
    subscription.unsubscribe();
    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.UNDECIDED);

    subscription.dismiss();
    subscription.unsubscribe();
    assertThat(subscription.getStatus()).isEqualTo(EmailSubscriptionStatus.DECLINED);
    assertThat(subscription.getSubscribedAt()).isNull();
  }

  private EmailSubscription subscription() {
    return new EmailSubscription(
        Member.createGoogleMember("google-id", "member@example.invalid", "회원", null));
  }
}
