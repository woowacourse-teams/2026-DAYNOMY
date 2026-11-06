package org.grit.daynomy.investmentcalendar.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.grit.daynomy.investmentcalendar.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InvestmentEventRepositoryTest {
  @Autowired InvestmentEventRepository events;
  private static final Instant FROM = Instant.parse("2026-09-30T15:00:00Z");
  private static final Instant TO = Instant.parse("2026-10-31T15:00:00Z");

  @Test
  void includesMonthStartExcludesNextMonthAndSortsByAnnouncement() {
    save(InvestmentEventType.US_CPI, TO, BigDecimal.ONE);
    var later = save(InvestmentEventType.US_CPI, FROM.plusSeconds(3600), BigDecimal.ONE);
    save(InvestmentEventType.US_CPI, FROM.minusSeconds(1), BigDecimal.ONE);
    var first = save(InvestmentEventType.US_CPI, FROM, BigDecimal.ONE);
    assertThat(
            events.findAllByAnnouncedAtGreaterThanEqualAndAnnouncedAtLessThanOrderByAnnouncedAt(
                FROM, TO))
        .extracting(InvestmentEvent::getId)
        .containsExactly(first.getId(), later.getId());
  }

  @Test
  void historicalSamplesRequireSameTypeActualValueAndStrictlyEarlierAnnouncement() {
    var later = save(InvestmentEventType.US_CPI, FROM.plusSeconds(1), BigDecimal.ONE);
    var first = save(InvestmentEventType.US_CPI, FROM, BigDecimal.ONE);
    save(InvestmentEventType.US_CPI, TO, BigDecimal.ONE);
    save(InvestmentEventType.US_CPI, FROM, null);
    save(InvestmentEventType.KOREA_BASE_RATE, FROM, BigDecimal.ONE);
    assertThat(
            events.findAllByTypeAndActualValueIsNotNullAndAnnouncedAtLessThanOrderByAnnouncedAt(
                InvestmentEventType.US_CPI, TO))
        .extracting(InvestmentEvent::getId)
        .containsExactly(first.getId(), later.getId());
  }

  private InvestmentEvent save(InvestmentEventType type, Instant at, BigDecimal actual) {
    return events.saveAndFlush(
        new InvestmentEvent(
            type,
            "발표",
            at,
            BigDecimal.ONE,
            actual,
            "%",
            "출처",
            "https://example.invalid/event",
            UUID.randomUUID().toString(),
            null));
  }
}
