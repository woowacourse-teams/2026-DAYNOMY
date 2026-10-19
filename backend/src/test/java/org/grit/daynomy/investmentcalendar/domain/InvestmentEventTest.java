package org.grit.daynomy.investmentcalendar.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvestmentEventTest {

  @Test
  void comparesActualValueWithPreviousValue() {
    InvestmentEvent event = event(new BigDecimal("2.50"), new BigDecimal("2.75"));

    assertThat(event.direction()).isEqualTo(InvestmentEventDirection.INCREASED);
  }

  @Test
  void returnsUnavailableWhenResultHasNotBeenReleased() {
    InvestmentEvent event = event(new BigDecimal("2.50"), null);

    assertThat(event.direction()).isEqualTo(InvestmentEventDirection.UNAVAILABLE);
  }

  private InvestmentEvent event(BigDecimal previousValue, BigDecimal actualValue) {
    return new InvestmentEvent(
        InvestmentEventType.KOREA_BASE_RATE,
        "한국 기준금리 결정",
        Instant.parse("2026-10-29T01:00:00Z"),
        previousValue,
        actualValue,
        "%",
        "한국은행",
        "https://www.bok.or.kr",
        "BOK-2026-10",
        null);
  }
}
