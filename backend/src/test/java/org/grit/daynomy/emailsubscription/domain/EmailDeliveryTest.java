package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.emailsubscription.EmailFixtures.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EmailDeliveryTest {
  @Test
  void sendsAtNineAndStoresTextAndHtmlOnceForRetries() {
    var d = delivery();
    d.attachUnsubscribeLink("https://example.invalid/unsubscribe?token=a");
    d.completeTemplate("회원", "https://daynomy.com");
    var text = d.getBody();
    var html = d.getHtmlBody();
    assertThat(d.claim(NOW.minusSeconds(1))).isFalse();
    assertThat(d.claim(NOW)).isTrue();
    d.failed(true, NOW.plusSeconds(1));
    assertThat(d.claim(NOW.plusSeconds(599))).isFalse();
    assertThat(d.claim(NOW.plusSeconds(600))).isTrue();
    assertThat(d.getBody()).isEqualTo(text);
    assertThat(d.getHtmlBody()).isEqualTo(html);
    d.accepted("message", NOW.plusSeconds(601));
    assertThat(d.getStatus()).isEqualTo(EmailDelivery.Status.ACCEPTED);
    assertThat(d.claim(NOW.plusSeconds(1800))).isFalse();
    assertThat(d.getAnalysisIds()).isEmpty();
  }

  @Test
  void retriesAtNineTenAndNineThirtyAndNeverAfterTen() {
    var d = delivery();
    d.claim(NOW);
    d.failed(true, NOW.plusSeconds(1));
    assertThat(d.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(600));
    d.claim(NOW.plusSeconds(600));
    d.failed(true, NOW.plusSeconds(601));
    assertThat(d.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1800));
    d.claim(NOW.plusSeconds(1800));
    d.failed(true, NOW.plusSeconds(1801));
    assertThat(d.getStatus()).isEqualTo(EmailDelivery.Status.FAILED);
    var late = delivery();
    assertThat(late.claim(NOW.plusSeconds(3600))).isFalse();
    assertThat(late.getStatus()).isEqualTo(EmailDelivery.Status.EXPIRED);
  }

  @Test
  void unknownAndCancelledResultsCannotBeSentAgain() {
    var d = delivery();
    d.claim(NOW);
    d.unknown();
    assertThat(d.claim(NOW.plusSeconds(600))).isFalse();
    var cancelled = delivery();
    cancelled.cancel();
    assertThat(cancelled.claim(NOW)).isFalse();
    assertThatThrownBy(() -> cancelled.accepted("id", NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void doesNotPrepareEmptyOrLegacyAiContentAndDetectsHistoricalAnalysisIds() {
    var empty = EmailStockDigest.current(null, null, Map.of(), List.of(), List.of());
    assertThatThrownBy(() -> new EmailDelivery(member(), NOW, empty, MAIL_DATE))
        .isInstanceOf(IllegalArgumentException.class);
    var d = delivery();
    ReflectionTestUtils.setField(d, "analysisIds", List.of(1L));
    assertThat(d.currentNotification()).isFalse();
  }

  @Test
  void linksRequireAValidFrontendOriginAndCannotBeRewrittenAfterDispatch() {
    var d = delivery();
    d.attachUnsubscribeLink("https://example.invalid/unsubscribe");
    assertThatThrownBy(() -> d.completeTemplate("회원", "https://example.invalid/path"))
        .isInstanceOf(IllegalArgumentException.class);
    d.completeTemplate("회원", "https://daynomy.com");
    d.claim(NOW);
    assertThatThrownBy(() -> d.completeTemplate("회원", "https://daynomy.com"))
        .isInstanceOf(IllegalStateException.class);
  }
}
