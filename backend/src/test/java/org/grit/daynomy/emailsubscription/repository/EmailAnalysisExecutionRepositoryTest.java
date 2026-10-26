package org.grit.daynomy.emailsubscription.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisContent;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisKey;
import org.grit.daynomy.emailsubscription.domain.EmailAnalysisPolicy;
import org.grit.daynomy.emailsubscription.domain.EmailSubscription;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@Import(EmailAnalysisExecutionRepository.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailAnalysisExecutionRepositoryTest {
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

  @Autowired private EmailAnalysisExecutionRepository executions;
  @Autowired private AssetRepository assets;
  @Autowired private EmailSubscriptionRepository subscriptions;
  @Autowired private TestEntityManager em;
  @Autowired private jakarta.persistence.EntityManager entityManager;
  @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
  private final LocalDate date = LocalDate.of(2026, 10, 26);
  private final Instant now = Instant.parse("2026-10-25T22:00:00Z");

  @Test
  void emailUsesOnlySelectedCurrentJobsSucceededBeforeCutoff() {
    var ids = List.of(asset(), asset(), asset(), asset());
    executions.register(date, now, ids);
    var resultIds = new java.util.ArrayList<Long>();
    for (int i = 0; i < ids.size(); i++) {
      var id = ids.get(i);
      var content = new CommonStockAnalysisContent(null, null, "소식", "", "", "근거", List.of());
      var analysis =
          em.persistAndFlush(
              new CommonStockAnalysis(
                  assets.findById(id).orElseThrow(),
                  new CommonStockAnalysisKey(id, "model", "v1", "e1", now),
                  content,
                  "a".repeat(64),
                  now));
      resultIds.add(analysis.getId());
      entityManager
          .createNativeQuery(
              """
          UPDATE email_analysis_jobs SET status = :status, analysis_id = :analysis,
            completed_at = :completed, as_of = :asOf WHERE run_date = :date AND asset_id = :asset
          """)
          .setParameter("status", i == 2 ? "FAILED" : "SUCCEEDED")
          .setParameter("analysis", analysis.getId())
          .setParameter("date", date)
          .setParameter("asset", id)
          .setParameter("completed", i == 1 ? now.plusSeconds(6300) : now.plusSeconds(3600))
          .setParameter("asOf", i == 3 ? now.minusSeconds(60) : now)
          .executeUpdate();
    }
    assertThat(executions.findSuccessfulAnalysisIds(date, now, ids))
        .containsExactly(resultIds.getFirst());
    assertThat(executions.findSuccessfulAnalysisIds(date, now, ids.subList(1, 4))).isEmpty();
    assertThat(executions.findSuccessfulAnalysisIds(date.minusDays(1), now, ids)).isEmpty();
    assertThat(executions.findSuccessfulAnalysisIds(date, now, List.of())).isEmpty();
  }

  @Test
  void registrationIsIdempotentAndGlobalActiveJobsAreLimitedToTwo() {
    var ids = List.of(asset(), asset(), asset());
    executions.register(date, now, ids);
    executions.register(date, now, ids);
    var first = executions.claim(date, now, policy("10", "100")).orElseThrow();
    var second = executions.claim(date, now, policy("10", "100")).orElseThrow();
    assertThat(first.assetId()).isNotEqualTo(second.assetId());
    assertThat(executions.claim(date, now, policy("10", "100"))).isEmpty();
    executions.finish(first.id(), "FAILED", null, now);
    assertThat(executions.claim(date, now, policy("10", "100"))).isPresent();
    assertThat(
            ((Number)
                    entityManager
                        .createNativeQuery("SELECT count(*) FROM email_analysis_jobs")
                        .getSingleResult())
                .longValue())
        .isEqualTo(3);
  }

  @Test
  void countsInFlightUnknownAndCompletedReservationsAgainstDailyBudget() {
    executions.register(date, now, List.of(asset()));
    var policy = policy("2", "100");
    var job = executions.claim(date, now, policy).orElseThrow();
    UUID first = executions.reserve(job, "source_search", "model", now, policy);
    executions.completeCall(first, "UNKNOWN", null, null, null, null, null, null, now);
    UUID second = executions.reserve(job, "initial", "model", now, policy);
    executions.completeCall(second, "COMPLETED", 1L, 0L, 1L, 0L, new BigDecimal("0.01"), null, now);
    assertThatThrownBy(() -> executions.reserve(job, "correction", "model", now, policy))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            (BigDecimal)
                entityManager
                    .createNativeQuery("SELECT sum(charged_usd) FROM email_analysis_calls")
                    .getSingleResult())
        .isEqualByComparingTo("1.01");
  }

  @Test
  void monthlyBudgetIncludesEarlierDaysAndCostsAboveReservationBlockNewCalls() {
    Long id = asset();
    var yesterday = date.minusDays(1);
    executions.register(yesterday, now.minusSeconds(86400), List.of(id));
    var policy = policy("10", "1");
    var old = executions.claim(yesterday, now.minusSeconds(86400), policy).orElseThrow();
    executions.reserve(old, "initial", "model", now.minusSeconds(86400), policy);
    executions.finish(old.id(), "FAILED", null, now);
    executions.register(date, now, List.of(id));
    assertThat(executions.claim(date, now, policy)).isEmpty();
    var current = executions.claim(date, now, policy("10", "100")).orElseThrow();
    UUID call = executions.reserve(current, "initial", "model", now, policy("10", "100"));
    executions.completeCall(call, "COMPLETED", 1L, 0L, 1L, 0L, new BigDecimal("2"), null, now);
    assertThatThrownBy(
            () -> executions.reserve(current, "correction", "model", now, policy("10", "100")))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesRequestsAfterCutoffOrWithTheWrongPricedModel() {
    executions.register(date, now, List.of(asset()));
    var policy = policy("10", "100");
    var job = executions.claim(date, now, policy).orElseThrow();
    assertThatThrownBy(() -> executions.reserve(job, "initial", "other-model", now, policy))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> executions.reserve(job, "initial", "model", now.plusSeconds(6300), policy))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void selectsOlderUnprocessedCandidatesFirstAndEnforcesDailyHundredStockCap() {
    Long recent = asset();
    Long waiting = asset();
    executions.register(date.minusDays(1), now.minusSeconds(86400), List.of(waiting));
    executions.register(date, now, List.of(recent, waiting));
    var job = executions.claim(date, now, policy("10", "100")).orElseThrow();
    assertThat(job.assetId()).isEqualTo(waiting);
    executions.finish(job.id(), "FAILED", null, now);
    for (int i = 0; i < 99; i++) {
      executions.register(date, now, List.of(asset()));
    }
    entityManager
        .createNativeQuery(
            "UPDATE email_analysis_jobs SET status = 'FAILED' WHERE run_date = :date AND asset_id <> :recent")
        .setParameter("date", date)
        .setParameter("recent", recent)
        .executeUpdate();
    assertThat(executions.claim(date, now, policy("10", "100"))).isEmpty();
  }

  @Test
  void eligibilityQueryExcludesWithdrawnAndUnsubscribedMembersAndRechecksHoldings() {
    Long assetId = asset();
    var stock = assets.findById(assetId).orElseThrow();
    var member =
        em.persist(Member.createGoogleMember("email-job", "test@example.com", "테스트", null));
    var subscription = new EmailSubscription(member);
    subscription.subscribe(now);
    em.persist(subscription);
    var portfolio = em.persist(Portfolio.create(member));
    em.persist(new PortfolioHolding(portfolio, stock, 1, BigDecimal.TEN));
    em.flush();
    assertThat(subscriptions.findEligibleMemberIds(0L, PageRequest.of(0, 500)))
        .containsExactly(member.getId());
    assertThat(executions.hasSubscriberHolding(assetId)).isTrue();
    subscription.unsubscribe();
    em.flush();
    assertThat(executions.hasSubscriberHolding(assetId)).isFalse();
    assertThat(subscriptions.findEligibleMemberIds(0L, PageRequest.of(0, 500))).isEmpty();
    subscription.subscribe(now);
    member.withdraw();
    em.flush();
    assertThat(executions.hasSubscriberHolding(assetId)).isFalse();
    assertThat(subscriptions.findEligibleMemberIds(0L, PageRequest.of(0, 500))).isEmpty();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentServersCannotClaimTheSameStock() throws Exception {
    Long id = asset();
    executions.register(date, now, List.of(id));
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                return executions.claim(date, now, policy("10", "100"));
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                return executions.claim(date, now, policy("10", "100"));
              });
      start.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .filteredOn(Optional -> Optional.isPresent())
          .hasSize(1);
    } finally {
      start.countDown();
      cleanup(id);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentReservationsCannotSpendTheSameRemainingBudget() throws Exception {
    Long id = asset();
    var policy = policy("1", "100");
    executions.register(date, now, List.of(id));
    var job = executions.claim(date, now, policy).orElseThrow();
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Boolean> reserve =
          () -> {
            start.await();
            try {
              executions.reserve(job, "initial", "model", now, policy);
              return true;
            } catch (IllegalStateException exhausted) {
              return false;
            }
          };
      var first = executor.submit(reserve);
      var second = executor.submit(reserve);
      start.countDown();
      assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    } finally {
      start.countDown();
      cleanup(id);
    }
  }

  @Test
  void releasesUnusedReservationOnlyWhenUsageHasBeenConfirmed() {
    var policy = policy("1.5", "100");
    executions.register(date, now, List.of(asset()));
    var job = executions.claim(date, now, policy).orElseThrow();
    UUID first = executions.reserve(job, "source_search", "model", now, policy);
    executions.completeCall(first, "COMPLETED", 1L, 0L, 1L, 0L, new BigDecimal("0.01"), null, now);
    assertThat(executions.reserve(job, "initial", "model", now, policy)).isNotNull();
    assertThat(
            (BigDecimal)
                entityManager
                    .createNativeQuery("SELECT sum(charged_usd) FROM email_analysis_calls")
                    .getSingleResult())
        .isEqualByComparingTo("1.01");
  }

  private void cleanup(Long assetId) {
    new org.springframework.transaction.support.TransactionTemplate(transactionManager)
        .executeWithoutResult(
            tx -> {
              entityManager
                  .createNativeQuery(
                      "DELETE FROM email_analysis_calls WHERE job_id IN (SELECT id FROM email_analysis_jobs WHERE asset_id = :asset)")
                  .setParameter("asset", assetId)
                  .executeUpdate();
              entityManager
                  .createNativeQuery("DELETE FROM email_analysis_jobs WHERE asset_id = :asset")
                  .setParameter("asset", assetId)
                  .executeUpdate();
              assets.deleteById(assetId);
            });
  }

  private Long asset() {
    return assets
        .saveAndFlush(
            Asset.listedStock("테스트", UUID.randomUUID().toString(), StockMarket.KOSPI, null, date))
        .getId();
  }

  private EmailAnalysisPolicy policy(String daily, String monthly) {
    return new EmailAnalysisPolicy(
        true,
        true,
        "model",
        new BigDecimal(daily),
        new BigDecimal(monthly),
        BigDecimal.ONE,
        new BigDecimal("2"),
        BigDecimal.ONE,
        new BigDecimal("8"),
        new BigDecimal("0.01"),
        2000,
        3);
  }
}
