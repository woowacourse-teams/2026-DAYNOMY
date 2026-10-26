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

  @Test
  void calendarOnlyTemplateIncludesNicknameRealLinksAndOnlyTheAvailableSection() {
    var supplement =
        new EmailDigestSupplement(
            null,
            List.of(
                new EmailDigestSupplement.Event(
                    "earnings",
                    "삼성전자 실적 발표",
                    nine.plusSeconds(86400),
                    "https://example.invalid/source",
                    false)));
    var mail =
        new EmailDelivery(
            Member.createGoogleMember("id", "test@example.invalid", "투자자", null),
            nine.minusSeconds(7200),
            null,
            supplement,
            LocalDate.of(2026, 10, 26));
    mail.attachUnsubscribeLink("https://api.example.invalid/unsubscribe?token=test");
    mail.completeTemplate("투자자", "https://daynomy.com");
    assertThat(mail.getBody())
        .contains(
            "안녕하세요, 투자자님.",
            "다가오는 투자 일정이에요",
            "삼성전자 실적 발표 — 내일",
            "발표 시각 미확정",
            "내 포트폴리오 자세히 보기: https://daynomy.com/",
            "알림 설정: https://daynomy.com/mypage",
            "수신 해지:")
        .doesNotContain("포트폴리오 분석이 달라졌어요", "보유 종목에 이런 소식이 있어요", "내 자산의 변화예요", "09:00");
    var savedBody = mail.getBody();
    mail.claim(nine);
    mail.failed(true, nine.plusSeconds(1));
    assertThatThrownBy(() -> mail.completeTemplate("다른 이름", "https://other.invalid"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(mail.getBody()).isEqualTo(savedBody);
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
