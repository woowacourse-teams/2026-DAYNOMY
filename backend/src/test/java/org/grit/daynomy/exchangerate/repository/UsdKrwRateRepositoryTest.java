package org.grit.daynomy.exchangerate.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsdKrwRateRepositoryTest {
  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
  }

  @Autowired private UsdKrwRateRepository rates;
  @Autowired private TestEntityManager em;

  @Test
  void upsertRefreshesTheQuoteAndIgnoresAnOlderFetch() {
    LocalDate requestedDate = LocalDate.of(2026, 10, 23);
    LocalDate provisionalBaseDate = requestedDate.minusDays(1);
    Instant firstFetch = Instant.parse("2026-10-23T05:00:00Z");
    Instant latestFetch = firstFetch.plusSeconds(3600);
    BigDecimal finalRate = new BigDecimal("1420.123456");

    rates.upsert(requestedDate, provisionalBaseDate, new BigDecimal("1410"), firstFetch);
    rates.upsert(requestedDate, requestedDate, finalRate, latestFetch);
    assertLatestQuote(requestedDate, finalRate, latestFetch);

    rates.upsert(
        requestedDate, provisionalBaseDate, new BigDecimal("1415"), firstFetch.plusSeconds(60));
    assertLatestQuote(requestedDate, finalRate, latestFetch);
  }

  private void assertLatestQuote(LocalDate requestedDate, BigDecimal rate, Instant fetchedAt) {
    em.clear();
    var saved = rates.findById(requestedDate).orElseThrow();
    assertThat(saved.getBaseDate()).isEqualTo(requestedDate);
    assertThat(saved.getRate()).isEqualByComparingTo(rate);
    assertThat(saved.getFetchedAt()).isEqualTo(fetchedAt);
  }
}
