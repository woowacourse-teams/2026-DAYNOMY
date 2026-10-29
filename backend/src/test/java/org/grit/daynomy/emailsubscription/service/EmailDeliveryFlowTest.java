package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.emailsubscription.EmailFixtures.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.content.domain.*;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.emailsubscription.domain.*;
import org.grit.daynomy.emailsubscription.external.ResendEmailClient;
import org.grit.daynomy.emailsubscription.repository.*;
import org.grit.daynomy.external.openai.OpenAiSocialPostSearchClient;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.domain.*;
import org.grit.daynomy.portfolio.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
class EmailDeliveryFlowTest {
  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRESQL::getUsername);
    r.add("spring.datasource.password", POSTGRESQL::getPassword);
    r.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
    r.add("email.delivery.unsubscribe-secret", () -> "test-only-secret-at-least-32-bytes");
    r.add("email.delivery.public-base-url", () -> "http://localhost:8080");
  }

  @Autowired EmailDeliveryService service;
  @Autowired EmailDeliveryRepository deliveries;
  @Autowired EmailSubscriptionRepository subscriptions;
  @Autowired MemberRepository members;
  @Autowired AssetRepository assets;
  @Autowired PortfolioRepository portfolios;
  @Autowired PortfolioHoldingRepository holdings;
  @Autowired AssetContentRepository contents;
  @Autowired JdbcTemplate jdbc;
  @Autowired EmailUnsubscribeService unsubscribe;
  @MockitoBean PortfolioAnalysisAiClient ai;
  @MockitoBean OpenAiSocialPostSearchClient socialSearch;

  private Member subscriber() {
    var member = members.save(member());
    var sub = new EmailSubscription(member);
    sub.subscribe(NOW.minusSeconds(7200));
    subscriptions.save(sub);
    var asset =
        assets.save(
            Asset.listedStock(
                "종목", UUID.randomUUID().toString(), StockMarket.KOSPI, null, MAIL_DATE));
    var portfolio = portfolios.save(Portfolio.create(member));
    holdings.save(new PortfolioHolding(portfolio, asset, 1, BigDecimal.TEN));
    var content =
        contents.save(
            AssetContent.create(
                asset,
                ContentSourceType.THREADS,
                "새 관련 글",
                "https://threads.net/post/" + UUID.randomUUID(),
                null,
                MAIL_DATE.minusYears(1)));
    jdbc.update(
        "UPDATE stock_related_contents SET created_at = ? WHERE id = ?",
        java.sql.Timestamp.from(NOW.minusSeconds(60)),
        content.getId());
    return member;
  }

  private EmailSendingService sender(ResendEmailClient client, Instant at) {
    return new EmailSendingService(service, client, Clock.fixed(at, ZoneOffset.UTC));
  }

  @Test
  void preparesAndSendsStoredContentWithoutAiAndOnlyAcceptsOnce() {
    var member = subscriber();
    var id = service.prepare(member.getId(), NOW).orElseThrow();
    assertThat(service.prepare(member.getId(), NOW)).contains(id);
    var client = mock(ResendEmailClient.class);
    when(client.send(any()))
        .thenAnswer(
            i -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(deliveries.findById(id).orElseThrow().getStatus())
                  .isEqualTo(EmailDelivery.Status.SENDING);
              return new ResendEmailClient.Result(ResendEmailClient.Status.ACCEPTED, "message");
            });
    sender(client, NOW).send(id);
    sender(client, NOW).send(id);
    verify(client, times(1)).send(any());
    verifyNoInteractions(ai, socialSearch);
    var saved = deliveries.findById(id).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(EmailDelivery.Status.ACCEPTED);
    assertThat(saved.getHtmlBody())
        .contains("새 관련 글", "자세한 자산 현황은 포트폴리오에서 확인해보세요")
        .doesNotContain("분석이 달라", "출처:");
    assertThat(saved.getAnalysisIds()).isEmpty();
    unsubscribe.unsubscribe(saved.getUnsubscribeUrl().split("\\?token=", 2)[1]);
    assertThat(service.prepare(member.getId(), NOW.plusSeconds(86400))).isEmpty();
  }

  @Test
  void retryKeepsTheSamePayloadAndUnsubscribedMembersAreNeverSent() {
    var member = subscriber();
    var id = service.prepare(member.getId(), NOW).orElseThrow();
    var client = mock(ResendEmailClient.class);
    when(client.send(any()))
        .thenReturn(
            new ResendEmailClient.Result(ResendEmailClient.Status.RETRYABLE_FAILURE, null),
            new ResendEmailClient.Result(ResendEmailClient.Status.ACCEPTED, "message"));
    sender(client, NOW).send(id);
    var before = deliveries.findById(id).orElseThrow().getHtmlBody();
    sender(client, NOW.plusSeconds(300)).send(id);
    sender(client, NOW.plusSeconds(600)).send(id);
    verify(client, times(2)).send(any());
    assertThat(deliveries.findById(id).orElseThrow().getHtmlBody()).isEqualTo(before);
    var other = subscriber();
    var queued = service.prepare(other.getId(), NOW).orElseThrow();
    unsubscribe.unsubscribe(
        deliveries.findById(queued).orElseThrow().getUnsubscribeUrl().split("\\?token=", 2)[1]);
    sender(client, NOW).send(queued);
    assertThat(deliveries.findById(queued).orElseThrow().getStatus())
        .isEqualTo(EmailDelivery.Status.CANCELLED);
  }

  @Test
  void concurrentPreparationCreatesOneDelivery() throws Exception {
    var member = subscriber();
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> service.prepare(member.getId(), NOW));
      var second = pool.submit(() -> service.prepare(member.getId(), NOW));
      assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
    }
  }
}
