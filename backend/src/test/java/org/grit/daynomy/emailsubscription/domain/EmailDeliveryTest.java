package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.member.domain.Member;
import org.junit.jupiter.api.Test;

class EmailDeliveryTest {
  private final Instant nine = Instant.parse("2026-10-26T00:00:00Z");

  @Test
  void unsubscribeLinkCannotChangeAfterPreparationOrBetweenRetries() {
    var delivery = delivery();
    delivery.attachUnsubscribeLink("https://example.invalid/unsubscribe?token=first");
    assertThatThrownBy(
            () -> delivery.attachUnsubscribeLink("https://example.invalid/unsubscribe?token=other"))
        .isInstanceOf(IllegalStateException.class);
    delivery.claim(nine);
    delivery.failed(true, nine.plusSeconds(1));
    assertThatThrownBy(
            () -> delivery.attachUnsubscribeLink("https://example.invalid/unsubscribe?token=other"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void sendsAtNineAndRetriesAtTenAndThirtyMinutesWithTheSameId() {
    var delivery = delivery();
    var id = delivery.getId();
    assertThat(delivery.claim(nine.minusSeconds(1))).isFalse();
    assertThat(delivery.claim(nine)).isTrue();
    delivery.failed(true, nine.plusSeconds(1));
    assertThat(delivery.claim(nine.plusSeconds(599))).isFalse();
    assertThat(delivery.claim(nine.plusSeconds(600))).isTrue();
    delivery.failed(true, nine.plusSeconds(601));
    assertThat(delivery.claim(nine.plusSeconds(1800))).isTrue();
    delivery.failed(true, nine.plusSeconds(1801));
    assertThat(delivery.claim(nine.plusSeconds(1900))).isFalse();
    assertThat(delivery.getAttempts()).isEqualTo(3);
    assertThat(delivery.getId()).isEqualTo(id);
  }

  @Test
  void neverResendsAnUnknownOutcomeButCanRecordProviderConfirmation() {
    var delivery = delivery();
    delivery.claim(nine);
    delivery.unknown();
    assertThat(delivery.claim(nine.plusSeconds(600))).isFalse();
    assertThatThrownBy(() -> delivery.failed(true, nine.plusSeconds(600)))
        .isInstanceOf(IllegalStateException.class);
    delivery.accepted("provider-1", nine.plusSeconds(1));
    assertThat(delivery.getStatus()).isEqualTo(EmailDelivery.Status.ACCEPTED);
    delivery.accepted("provider-1", nine.plusSeconds(2));
    assertThat(delivery.getAcceptedAt()).isEqualTo(nine.plusSeconds(1));
    assertThat(delivery.claim(nine.plusSeconds(1800))).isFalse();
  }

  @Test
  void expiresAtTenAndDoesNotSendYesterdayQueue() {
    var delivery = delivery();
    assertThat(delivery.claim(nine.plusSeconds(3600))).isFalse();
    assertThat(delivery.getStatus()).isEqualTo(EmailDelivery.Status.EXPIRED);
    assertThat(delivery().claim(nine.plusSeconds(86400))).isFalse();
  }

  @Test
  void skipsMissedRetrySlotsRatherThanCatchingUpImmediately() {
    var delivery = delivery();
    delivery.claim(nine);
    delivery.failed(true, nine.plusSeconds(1200));
    assertThat(delivery.getNextAttemptAt()).isEqualTo(nine.plusSeconds(1800));
    delivery.claim(nine.plusSeconds(1800));
    delivery.failed(true, nine.plusSeconds(1801));
    assertThat(delivery.getStatus()).isEqualTo(EmailDelivery.Status.FAILED);
  }

  @Test
  void permanentFailureAndCancellationAreTerminal() {
    var delivery = delivery();
    delivery.claim(nine);
    delivery.failed(false, nine.plusSeconds(1));
    assertThat(delivery.claim(nine.plusSeconds(600))).isFalse();
    var cancelled = delivery();
    cancelled.cancel();
    assertThat(cancelled.claim(nine)).isFalse();
    assertThatThrownBy(() -> cancelled.accepted("id", nine))
        .isInstanceOf(IllegalStateException.class);
  }

  private EmailDelivery delivery() {
    var content = new CommonStockAnalysisContent(null, null, "소식", "", "", "근거", List.of());
    var digest =
        new EmailStockDigest(
            LocalDate.of(2026, 10, 23),
            LocalDate.of(2026, 10, 22),
            List.of(
                new EmailStockDigest.Stock(1L, 10L, "종목", BigDecimal.TEN, null, null, content)));
    return new EmailDelivery(
        Member.createGoogleMember("id", "test@example.invalid", "회원", null),
        nine.minusSeconds(7200),
        digest,
        LocalDate.of(2026, 10, 26));
  }
}
