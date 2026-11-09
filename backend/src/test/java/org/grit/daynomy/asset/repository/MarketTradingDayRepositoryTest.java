package org.grit.daynomy.asset.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.MarketTradingDay;
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
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MarketTradingDayRepositoryTest {
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

  @Autowired private TestEntityManager entityManager;
  @Autowired private MarketTradingDayRepository repository;

  @Test
  void repeatedBulkCollectionPreservesFirstCompletionAndMarksNewDates() {
    LocalDate monday = LocalDate.of(2036, 10, 19);
    LocalDate tuesday = monday.plusDays(1);
    entityManager.persist(new MarketTradingDay(monday, true));
    entityManager.persist(new MarketTradingDay(tuesday, true));
    entityManager.flush();
    Instant first = Instant.parse("2036-10-20T07:00:00Z");
    Instant retry = Instant.parse("2036-10-20T22:00:00Z");

    repository.markUsCollectedDates(List.of(monday), first);
    repository.markUsCollectedDates(List.of(monday, tuesday), retry);
    entityManager.clear();

    var existing = repository.findById(monday).orElseThrow();
    var newlyCollected = repository.findById(tuesday).orElseThrow();
    assertThat(existing.getUsCollectedAt()).isEqualTo(first);
    assertThat(existing.getUsTrading()).isTrue();
    assertThat(newlyCollected.getUsCollectedAt()).isEqualTo(retry);
    assertThat(newlyCollected.getUsTrading()).isTrue();
  }
}
