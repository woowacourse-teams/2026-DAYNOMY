package org.grit.daynomy.portfolio.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PortfolioDelayedQuoteRepository.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PortfolioDelayedQuoteRepositoryTest {

  @Container
  static final PostgreSQLContainer database = new PostgreSQLContainer("postgres:16-alpine");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", database::getJdbcUrl);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.datasource.username", database::getUsername);
    registry.add("spring.datasource.password", database::getPassword);
  }

  @Autowired private PortfolioDelayedQuoteRepository quotes;

  @Test
  void processes2400WaitingSymbolsInFourBurstSlotsEvenWithNewArrivals() {
    Instant firstSlot = Instant.parse("2026-11-10T01:00:00Z");
    var original = IntStream.range(0, 2400).mapToObj(i -> "OLD%04d.KO".formatted(i)).toList();
    var active = new java.util.ArrayList<>(original);
    var attempted = new HashSet<String>();
    quotes.request(original, firstSlot.minusSeconds(10));

    for (int cycle = 0; cycle < 4; cycle++) {
      Instant slot = firstSlot.plusSeconds(300L * cycle);
      int generation = cycle;
      IntStream.range(0, 300)
          .mapToObj(i -> "AAA%d%03d.KO".formatted(generation, i))
          .forEach(active::add);
      quotes.request(active, slot);
      var selected = quotes.reserve("KR", slot, slot, 600, 50_000, 25_000);
      assertThat(selected).hasSize(600).allMatch(symbol -> symbol.startsWith("OLD"));
      attempted.addAll(selected);
      quotes.markAttempted(selected, slot.plusSeconds(1));
    }

    assertThat(attempted).containsExactlyInAnyOrderElementsOf(original);
  }

  @Test
  void limitsBurstToTheBudgetNotProtectedForFutureSlots() {
    Instant slot = Instant.parse("2026-11-11T00:00:00Z");
    var symbols = IntStream.range(0, 601).mapToObj(i -> "BURST%03d.KO".formatted(i)).toList();
    quotes.request(symbols, slot);

    assertThat(quotes.reserve("KR", slot, slot, 600, 50_000, 49_600))
        .containsExactlyElementsOf(symbols.subList(0, 400));
    assertThat(quotes.reserve("KR", slot, slot, 600, 50_000, 49_600)).isEmpty();

    Instant next = slot.plusSeconds(300);
    quotes.markAttempted(symbols.subList(0, 400), slot.plusSeconds(1));
    quotes.request(symbols, next);
    var nextBatch = quotes.reserve("KR", next, next, 600, 50_000, 49_300);
    assertThat(nextBatch).hasSize(300);
    assertThat(nextBatch.subList(0, 201)).containsExactlyElementsOf(symbols.subList(400, 601));
  }

  @Test
  void reservesAtMostOneBatchPerMarketAndSharesTheDailyBudget() {
    Instant now = Instant.parse("2026-11-09T00:00:00Z");
    quotes.request(List.of("005930.KO", "069500.KO", "AAPL.US", "QQQ.US"), now);

    assertThat(quotes.reserve("KR", now, now, 1, 2, 1)).containsExactly("005930.KO");
    assertThat(quotes.reserve("KR", now, now, 1, 2, 1)).isEmpty();
    assertThat(quotes.reserve("US", now, now, 300, 2, 0)).containsExactly("AAPL.US");
    Instant nextSlot = now.plusSeconds(300);
    assertThat(quotes.reserve("KR", nextSlot, nextSlot, 300, 2, 0)).isEmpty();

    quotes.save("005930.KO", new BigDecimal("70000"), now);
    quotes.save("005930.KO", new BigDecimal("60000"), now.minusSeconds(60));
    assertThat(quotes.request(List.of("005930.KO"), now.plusSeconds(60)))
        .containsExactly(
            new PortfolioDelayedQuoteRepository.CachedQuote(
                "005930.KO", new BigDecimal("70000"), now));
  }
}
