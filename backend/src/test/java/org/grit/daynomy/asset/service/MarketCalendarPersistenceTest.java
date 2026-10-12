package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.external.krx.KrxCalendarClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MarketCalendarPersistenceTest {
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

  @Autowired private MarketTradingDayRepository repository;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbc;
  private final KrxCalendarClient client = mock(KrxCalendarClient.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private MarketCalendarSyncService service;

  @BeforeEach
  void setUp() {
    service = new MarketCalendarSyncService(client, repository, transactionManager, events);
  }

  @Test
  void persistsAnEntireYearAndRevisesHolidaysWithoutResettingUnchangedCollection() {
    when(client.publishedYears()).thenReturn(Set.of(2027));
    LocalDate holiday = LocalDate.of(2027, 1, 1);
    LocalDate date = LocalDate.of(2027, 1, 4);
    LocalDate today = LocalDate.of(2026, 12, 28);
    when(client.holidays(2027)).thenReturn(Set.of(holiday));
    service.synchronize(today);
    assertThat(
            repository.findAllByBaseDateBetweenOrderByBaseDate(
                holiday, holiday.plusYears(1).minusDays(1)))
        .hasSize(365);
    Instant collected = Instant.parse("2027-01-04T08:00:00Z");
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> repository.findByIdForUpdate(date).orElseThrow().collected(collected));
    service.synchronize(today);
    assertThat(repository.findById(date).orElseThrow().getCollectedAt()).isEqualTo(collected);
    verify(events).publishEvent(new MarketCalendarSyncResult(365));
    verifyNoMoreInteractions(events);

    when(client.holidays(2027)).thenReturn(Set.of(holiday, date));
    service.synchronize(today);
    assertThat(repository.findById(date).orElseThrow().isTrading()).isFalse();
    assertThat(repository.findById(date).orElseThrow().getCollectedAt()).isNull();
    when(client.holidays(2027)).thenReturn(Set.of(holiday));
    service.synchronize(today);
    assertThat(repository.findById(date).orElseThrow().isTrading()).isTrue();
    assertThat(repository.findById(date).orElseThrow().getCollectedAt()).isNull();
    verify(events, times(2)).publishEvent(new MarketCalendarSyncResult(1));
  }

  @Test
  void rollsBackTheWholeYearOnDatabaseFailureAndRetriesSuccessfully() {
    when(client.publishedYears()).thenReturn(Set.of(2028));
    when(client.holidays(2028)).thenReturn(Set.of(LocalDate.of(2028, 1, 1)));
    jdbc.execute(
        "ALTER TABLE market_trading_days ADD CONSTRAINT calendar_test_failure CHECK (base_date <> DATE '2028-12-31')");
    try {
      service.synchronize(LocalDate.of(2027, 12, 28));
      assertThat(
              repository.findAllByBaseDateBetweenOrderByBaseDate(
                  LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31)))
          .isEmpty();
      verifyNoInteractions(events);
    } finally {
      jdbc.execute("ALTER TABLE market_trading_days DROP CONSTRAINT calendar_test_failure");
    }
    service.synchronize(LocalDate.of(2027, 12, 28));
    assertThat(
            repository.findAllByBaseDateBetweenOrderByBaseDate(
                LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31)))
        .hasSize(366);
    verify(events).publishEvent(new MarketCalendarSyncResult(366));
  }
}
