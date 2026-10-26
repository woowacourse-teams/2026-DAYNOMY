package org.grit.daynomy.emailsubscription.repository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.emailsubscription.domain.*;
import org.grit.daynomy.emailsubscription.service.EmailDeliveryService;
import org.grit.daynomy.emailsubscription.service.EmailStockDigestService;
import org.grit.daynomy.emailsubscription.service.EmailUnsubscribeService;
import org.grit.daynomy.member.domain.Member;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
@DataJpaTest
@Import(EmailDeliveryService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailDeliveryRepositoryTest {
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

  @Autowired private EmailDeliveryRepository deliveries;
  @Autowired private EmailDeliveryService service;
  @Autowired private TestEntityManager em;
  @Autowired private jakarta.persistence.EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;
  @MockitoBean private EmailStockDigestService digests;
  @MockitoBean private EmailUnsubscribeService unsubscribe;
  private final Instant now = Instant.parse("2026-10-26T00:00:00Z");
  private final Instant asOf = now.minusSeconds(7200);

  @Test
  void lastNoticeUsesAcceptanceAndBlocksUnknownWhilePersistingExactDigest() {
    var member = em.persist(member());
    var asset =
        em.persist(
            org.grit.daynomy.asset.domain.Asset.listedStock(
                "종목",
                "005930",
                org.grit.daynomy.asset.domain.StockMarket.KOSPI,
                null,
                LocalDate.of(2026, 10, 26)));
    var analysis =
        em.persist(
            new CommonStockAnalysis(
                asset,
                new CommonStockAnalysisKey(asset.getId(), "model", "v1", "e1", asOf),
                new CommonStockAnalysisContent(null, null, "소식", "", "", "근거", List.of()),
                "a".repeat(64),
                asOf));
    var snapshot =
        new EmailStockDigest(
            LocalDate.of(2026, 10, 23),
            LocalDate.of(2026, 10, 22),
            List.of(
                new EmailStockDigest.Stock(
                    analysis.getId(),
                    asset.getId(),
                    "종목",
                    BigDecimal.TEN,
                    null,
                    null,
                    analysis.getContent())));
    var old =
        em.persist(
            new EmailDelivery(
                member, asOf.minusSeconds(86400), snapshot, LocalDate.of(2026, 10, 25)));
    old.claim(now.minusSeconds(86400));
    old.accepted("old-provider", now.minusSeconds(86400));
    var current = em.persist(new EmailDelivery(member, asOf, snapshot, LocalDate.of(2026, 10, 26)));
    em.flush();
    assertThat(
            deliveries.findLastNoticeForAsset(member.getId(), asset.getId()).orElseThrow().getId())
        .isEqualTo(old.getId());
    current.claim(now);
    current.unknown();
    em.flush();
    em.clear();
    var found = deliveries.findLastNoticeForAsset(member.getId(), asset.getId()).orElseThrow();
    assertThat(found.getId()).isEqualTo(current.getId());
    assertThat(found.getStockDigest()).isEqualTo(snapshot);
    assertThat(deliveries.findLastNoticeForAsset(member.getId(), asset.getId() + 1)).isEmpty();
  }

  @Test
  void dueQueryExcludesEarlyRetriesUnknownResultsAndPreviousDays() {
    var date = LocalDate.of(2026, 10, 26);
    var ready = em.persist(new EmailDelivery(em.persist(member()), asOf, digest(), date));
    var retry = em.persist(new EmailDelivery(em.persist(member()), asOf, digest(), date));
    retry.claim(now);
    retry.failed(true, now.plusSeconds(1));
    var unknown = em.persist(new EmailDelivery(em.persist(member()), asOf, digest(), date));
    unknown.claim(now);
    unknown.unknown();
    em.persist(
        new EmailDelivery(
            em.persist(member()), asOf.minusSeconds(86400), digest(), date.minusDays(1)));
    em.flush();
    var statuses = List.of(EmailDelivery.Status.READY, EmailDelivery.Status.RETRY_PENDING);
    assertThat(deliveries.findDueIds(date, now, statuses, PageRequest.of(0, 1)))
        .containsExactly(ready.getId());
    assertThat(deliveries.findDueIds(date, now.plusSeconds(600), statuses, PageRequest.of(0, 10)))
        .containsExactly(ready.getId(), retry.getId());
    ready.claim(now);
    em.flush();
    assertThat(deliveries.findDueIds(date, now.plusSeconds(600), statuses, PageRequest.of(0, 10)))
        .containsExactly(retry.getId());
  }

  @Test
  void onlyAcceptedOrUncertainSendingReservesAnAnalysisInHistory() {
    var member = em.persist(member());
    var delivery =
        em.persist(new EmailDelivery(member, asOf, digest(), LocalDate.of(2026, 10, 26)));
    em.flush();
    assertThat(deliveries.findCoveredAnalysisIds(member.getId(), List.of(1L))).isEmpty();
    delivery.claim(now);
    em.flush();
    assertThat(deliveries.findCoveredAnalysisIds(member.getId(), List.of(1L))).containsExactly(1L);
    delivery.unknown();
    em.flush();
    assertThat(deliveries.findCoveredAnalysisIds(member.getId(), List.of(1L))).containsExactly(1L);
    assertThat(delivery.getAcceptedAt()).isNull();
    delivery.accepted("provider-message", now.plusSeconds(1));
    em.flush();
    em.clear();
    var saved = deliveries.findById(delivery.getId()).orElseThrow();
    assertThat(saved.getAcceptedAt()).isEqualTo(now.plusSeconds(1));
    assertThat(saved.getProviderMessageId()).isEqualTo("provider-message");
    assertThat(deliveries.findCoveredAnalysisIds(member.getId(), List.of(1L))).containsExactly(1L);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentPreparationAndClaimsCreateOneJobAndOneSendAttempt() throws Exception {
    var tx = new TransactionTemplate(transactionManager);
    Long memberId =
        tx.execute(
            status -> {
              var member = entityManager.merge(member());
              var subscription = new EmailSubscription(member);
              subscription.subscribe(asOf);
              entityManager.persist(subscription);
              entityManager.flush();
              return member.getId();
            });
    when(digests.compose(memberId, asOf, List.of(1L))).thenReturn(Optional.of(digest()));
    when(unsubscribe.link(memberId, asOf))
        .thenReturn("https://example.invalid/unsubscribe?token=test");
    when(unsubscribe.matches("https://example.invalid/unsubscribe?token=test", memberId, asOf))
        .thenReturn(true);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Optional<UUID>> prepare =
          () -> {
            start.await();
            return service.prepare(memberId, asOf, List.of(1L), now);
          };
      var first = executor.submit(prepare);
      var second = executor.submit(prepare);
      start.countDown();
      UUID id = first.get(10, TimeUnit.SECONDS).orElseThrow();
      assertThat(second.get(10, TimeUnit.SECONDS)).contains(id);
      var attempt1 = executor.submit(() -> service.claim(id, now));
      var attempt2 = executor.submit(() -> service.claim(id, now));
      assertThat(List.of(attempt1.get(10, TimeUnit.SECONDS), attempt2.get(10, TimeUnit.SECONDS)))
          .filteredOn(Optional::isPresent)
          .hasSize(1);
    } finally {
      start.countDown();
      tx.executeWithoutResult(
          status -> {
            entityManager
                .createNativeQuery("DELETE FROM email_deliveries WHERE member_id = :id")
                .setParameter("id", memberId)
                .executeUpdate();
            entityManager
                .createNativeQuery("DELETE FROM email_subscriptions WHERE member_id = :id")
                .setParameter("id", memberId)
                .executeUpdate();
            entityManager
                .createNativeQuery("DELETE FROM members WHERE id = :id")
                .setParameter("id", memberId)
                .executeUpdate();
          });
    }
  }

  private Member member() {
    String id = UUID.randomUUID().toString();
    return Member.createGoogleMember(id, id + "@example.invalid", id.substring(0, 12), null);
  }

  private EmailStockDigest digest() {
    var content = new CommonStockAnalysisContent(null, null, "소식", "", "", "근거", List.of());
    return new EmailStockDigest(
        LocalDate.of(2026, 10, 23),
        LocalDate.of(2026, 10, 22),
        List.of(new EmailStockDigest.Stock(1L, 10L, "종목", BigDecimal.TEN, null, null, content)));
  }
}
