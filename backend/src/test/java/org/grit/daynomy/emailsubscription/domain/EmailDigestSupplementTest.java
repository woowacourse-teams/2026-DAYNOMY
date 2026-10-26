package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmailDigestSupplementTest {
  @Test
  void assetOnlyMailUsesExactTwoPercentBoundaryInsteadOfRoundedDisplayRate() {
    assertThat(new EmailDigestSupplement(assets("102", "2", "2"), List.of()).shouldSend()).isTrue();
    assertThat(new EmailDigestSupplement(assets("98", "-2", "-2"), List.of()).shouldSend())
        .isTrue();
    assertThat(
            new EmailDigestSupplement(assets("101.995", "1.995", "2.00"), List.of()).shouldSend())
        .isFalse();
    assertThat(EmailDigestSupplement.empty().shouldSend()).isFalse();
  }

  @Test
  void calendarOnlyMailDisplaysKoreanTimeAndRechecksExactlyPreparedEvents() {
    var event =
        new EmailDigestSupplement.Event(
            "1@time",
            "기준금리 발표",
            Instant.parse("2026-10-27T01:00:00Z"),
            "https://example.invalid/source");
    var digest = new EmailDigestSupplement(null, List.of(event));
    assertThat(digest.shouldSend()).isTrue();
    assertThat(digest.body()).contains("2026-10-27 10:00", "한국 시간");
    assertThat(digest.stillValid(EmailDigestSupplement.empty())).isFalse();
    assertThat(digest.stillValid(digest)).isTrue();
  }

  @Test
  void unconfirmedEarningsTimeIsNotDisplayedAsAnExactAnnouncementTime() {
    var event =
        new EmailDigestSupplement.Event(
            "earnings",
            "실적 발표",
            Instant.parse("2026-10-27T00:00:00Z"),
            "https://example.invalid/source",
            false);
    assertThat(new EmailDigestSupplement(null, List.of(event)).body())
        .contains("2026-10-27", "발표 시각 미확정")
        .doesNotContain("09:00");
  }

  private EmailDigestSupplement.Assets assets(String value, String profit, String rate) {
    return new EmailDigestSupplement.Assets(
        LocalDate.of(2026, 10, 23),
        new BigDecimal(value),
        new BigDecimal(profit),
        new BigDecimal(rate));
  }
}
