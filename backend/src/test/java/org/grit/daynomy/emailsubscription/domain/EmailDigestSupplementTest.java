package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EmailDigestSupplementTest {
  @Test
  void sendsAssetOnlyMailAtExactTwoPercentWithoutRoundingTheThreshold() {
    assertThat(summary("102", "2", "2").shouldSend()).isTrue();
    assertThat(summary("98", "-2", "-2").shouldSend()).isTrue();
    assertThat(summary("101.995", "1.995", "2.00").shouldSend()).isFalse();
    assertThat(EmailDigestSupplement.empty().shouldSend()).isFalse();
  }

  @Test
  void usesTheRealPriceDateAndNoEmptySections() {
    assertThat(summary("102", "2", "2").body())
        .contains("2026-10-23 종가 기준", "가격 변동 손익: 2원")
        .doesNotContain("소식", "분석");
    assertThat(EmailDigestSupplement.empty().body()).isEmpty();
  }

  private EmailDigestSupplement summary(String total, String profit, String rate) {
    return new EmailDigestSupplement(
        new EmailDigestSupplement.Assets(
            LocalDate.of(2026, 10, 23),
            new BigDecimal(total),
            new BigDecimal(profit),
            new BigDecimal(rate)));
  }
}
