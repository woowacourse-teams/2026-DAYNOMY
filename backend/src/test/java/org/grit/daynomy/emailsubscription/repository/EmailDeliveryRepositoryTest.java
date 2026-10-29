package org.grit.daynomy.emailsubscription.repository;

import static org.assertj.core.api.Assertions.*;
import static org.grit.daynomy.emailsubscription.EmailFixtures.*;

import java.time.*;
import java.util.*;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.content.domain.*;
import org.grit.daynomy.emailsubscription.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmailDeliveryRepositoryTest {
  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRESQL::getUsername);
    r.add("spring.datasource.password", POSTGRESQL::getPassword);
    r.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
  }

  @Autowired TestEntityManager em;
  @Autowired EmailDeliveryRepository deliveries;
  @Autowired EmailStockContentRepository contents;
  @Autowired EmailSubscriptionRepository subscriptions;
  @Autowired javax.sql.DataSource dataSource;

  @Test
  void analysisNoticeDeduplicationUsesMemberAndSavedAnalysisId() {
    var member = em.persist(member());
    var empty = EmailStockDigest.current(null, null, Map.of(1L, 1L), List.of(), List.of());
    var notice = new EmailDigestSupplement.Analysis(101L, NOW.minusSeconds(3600), List.of("삼성전자"));
    var mail =
        new EmailDelivery(member, NOW, empty, new EmailDigestSupplement(null, notice), MAIL_DATE);
    mail.claim(NOW);
    mail.unknown();
    em.persist(mail);
    em.flush();
    assertThat(deliveries.hasCoveredPortfolioAnalysis(member.getId(), "101")).isTrue();
    assertThat(deliveries.hasCoveredPortfolioAnalysis(member.getId(), "102")).isFalse();
    assertThat(deliveries.hasCoveredPortfolioAnalysis(-1L, "101")).isFalse();
  }

  @Test
  void automaticRunReservesDailyMonthlyAndConcurrentLimitsAndConsumesFailedSignals() {
    var one = em.persist(member());
    var two = em.persist(member());
    em.flush();
    var repository =
        new EmailAutoAnalysisRepository(
            org.springframework.jdbc.core.simple.JdbcClient.create(dataSource));
    var first =
        repository.claim(one.getId(), MAIL_DATE, List.of("price:date"), 10, 10, NOW).orElseThrow();
    assertThat(repository.claim(two.getId(), MAIL_DATE, List.of("price:date"), 10, 10, NOW))
        .isEmpty();
    repository.complete(first, null, false, NOW);
    assertThat(
            repository.claim(
                one.getId(), MAIL_DATE.plusDays(1), List.of("price:date"), 10, 10, NOW))
        .isEmpty();
    assertThat(repository.claim(two.getId(), MAIL_DATE, List.of("price:date"), 1, 10, NOW))
        .isEmpty();
    assertThat(
            repository.claim(two.getId(), MAIL_DATE.plusDays(1), List.of("price:date"), 10, 1, NOW))
        .isEmpty();
    assertThat(
            repository.claim(two.getId(), MAIL_DATE.plusDays(1), List.of("price:date"), 10, 2, NOW))
        .isPresent();
  }

  @Test
  void earningsSignalRequiresHeldDomesticStockConfirmedValueAndCollectedOfficialSource() {
    var stock = em.persist(Asset.listedStock("삼성전자", "005930", StockMarket.KOSPI, null, MAIL_DATE));
    var etf =
        em.persist(
            Asset.listedSecurity(
                "별도 ETF", AssetCategory.ETF, "005930", StockMarket.KOSPI, null, MAIL_DATE));
    var event =
        em.persist(
            new org.grit.daynomy.investmentcalendar.domain.InvestmentEvent(
                org.grit.daynomy.investmentcalendar.domain.InvestmentEventType.CORPORATE_EARNINGS,
                "확인된 실적 발표",
                NOW.minusSeconds(3600),
                null,
                java.math.BigDecimal.TEN,
                "억원",
                "전자공시시스템(DART)",
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=123",
                "DART-EARNINGS-005930-2026Q3",
                "005930"));
    em.flush();
    var jdbc = org.springframework.jdbc.core.simple.JdbcClient.create(dataSource);
    jdbc.sql("update investment_events set updated_at = :at where id = :id")
        .param("at", java.sql.Timestamp.from(NOW.minusSeconds(60)))
        .param("id", event.getId())
        .update();
    var repository = new EmailAutoAnalysisRepository(jdbc);
    jdbc.sql("set local time zone 'UTC'").update();
    var signal = repository.earningsSignals(List.of(stock.getId()), NOW.minusSeconds(86400), NOW);
    assertThat(signal).hasSize(1);
    jdbc.sql("set local time zone 'Asia/Seoul'").update();
    assertThat(repository.earningsSignals(List.of(stock.getId()), NOW.minusSeconds(86400), NOW))
        .isEqualTo(signal);
    assertThat(repository.earningsSignals(List.of(etf.getId()), NOW.minusSeconds(86400), NOW))
        .isEmpty();
    jdbc.sql("update investment_events set actual_value = null where id = :id")
        .param("id", event.getId())
        .update();
    assertThat(repository.earningsSignals(List.of(stock.getId()), NOW.minusSeconds(86400), NOW))
        .isEmpty();
    var subscriber = em.persist(new EmailSubscription(em.persist(member())));
    assertThat(subscriptions.isEligible(subscriber.getMemberId())).isFalse();
    subscriber.subscribe(NOW);
    em.flush();
    assertThat(subscriptions.isEligible(subscriber.getMemberId())).isTrue();
  }

  @Test
  void nativePriceDeduplicationIsAccountAndBaseDateScopedAndUnknownBlocksResending() {
    var member = em.persist(member());
    var d = new EmailDelivery(member, NOW, digest(1L), MAIL_DATE);
    d.claim(NOW);
    d.unknown();
    em.persist(d);
    em.flush();
    assertThat(
            deliveries.findCoveredPriceAssetIds(
                member.getId(), MAIL_DATE.minusDays(3).toString(), List.of(1L)))
        .containsExactly(1L);
    assertThat(
            deliveries.findCoveredPriceAssetIds(
                member.getId(), MAIL_DATE.minusDays(4).toString(), List.of(1L)))
        .isEmpty();
    assertThat(
            deliveries.findCoveredPriceAssetIds(
                -1L, MAIL_DATE.minusDays(3).toString(), List.of(1L)))
        .isEmpty();
  }

  @Test
  void contentDeduplicationAndAcceptedNoticeTimesUsePersistedJson() {
    var member = em.persist(member());
    var item =
        new EmailStockDigest.Content(
            10L,
            "THREADS|https://threads.net/post/1",
            1L,
            "종목",
            ContentSourceType.THREADS,
            "새 글",
            "https://threads.net/post/1",
            NOW.minusSeconds(60));
    var profile =
        EmailStockDigest.current(
            MAIL_DATE.minusDays(3),
            MAIL_DATE.minusDays(4),
            Map.of(1L, 1L),
            digest(1L).stocks(),
            List.of(item));
    var d = new EmailDelivery(member, NOW, profile, MAIL_DATE);
    d.claim(NOW);
    d.accepted("message", NOW);
    em.persist(d);
    em.flush();
    em.clear();
    assertThat(deliveries.findCoveredContentKeys(member.getId(), List.of(item.key())))
        .containsExactly(item.key());
    assertThat(deliveries.findLastStockNotices(member.getId(), List.of(1L)))
        .singleElement()
        .satisfies(n -> assertThat(n.getAcceptedAt()).isEqualTo(NOW));
    var saved = deliveries.findById(d.getId()).orElseThrow();
    assertThat(saved.getStockDigest()).isEqualTo(profile);
    assertThat(saved.getAnalysisIds()).isEmpty();
  }

  @Test
  void materialWindowUsesRegistrationTimeAndOnlyHeldAssets() {
    var asset = em.persist(Asset.listedStock("종목", "005930", StockMarket.KOSPI, null, MAIL_DATE));
    var c =
        em.persist(
            AssetContent.create(
                asset,
                ContentSourceType.YOUTUBE,
                "영상",
                "https://youtube.com/watch?v=id",
                null,
                MAIL_DATE.minusYears(1)));
    em.flush();
    em.getEntityManager()
        .createNativeQuery("UPDATE stock_related_contents SET created_at = :time WHERE id = :id")
        .setParameter("time", NOW.minusSeconds(60))
        .setParameter("id", c.getId())
        .executeUpdate();
    em.clear();
    assertThat(
            contents.findRelatedCandidates(
                List.of(asset.getId()),
                List.of(ContentSourceType.YOUTUBE),
                NOW.minusSeconds(86400),
                NOW))
        .hasSize(1);
    assertThat(
            contents.findRelatedCandidates(
                List.of(asset.getId()),
                List.of(ContentSourceType.YOUTUBE),
                NOW.minusSeconds(30),
                NOW))
        .isEmpty();
    assertThat(
            contents.findRelatedCandidates(
                List.of(-1L), List.of(ContentSourceType.YOUTUBE), NOW.minusSeconds(86400), NOW))
        .isEmpty();
  }

  @Test
  void internalNewsBecomesFreshWhenPublishedAndHiddenNewsIsNotSelected() {
    var asset = em.persist(Asset.listedStock("종목", "005931", StockMarket.KOSPI, null, MAIL_DATE));
    var news =
        em.persist(
            org.grit.daynomy.news.domain.News.createPublished(
                "새 소식",
                "본문",
                null,
                List.of(),
                org.grit.daynomy.news.domain.Category.STOCK,
                NOW.minusSeconds(60)));
    var content = em.persist(AssetContent.createInternalNews(asset, news));
    em.flush();
    em.getEntityManager()
        .createNativeQuery("UPDATE stock_related_contents SET created_at = :time WHERE id = :id")
        .setParameter("time", NOW.minusSeconds(172800))
        .setParameter("id", content.getId())
        .executeUpdate();
    assertThat(
            contents.findRelatedCandidates(
                List.of(asset.getId()),
                List.of(ContentSourceType.INTERNAL_NEWS),
                NOW.minusSeconds(86400),
                NOW))
        .hasSize(1);
    news.delete();
    em.flush();
    assertThat(
            contents.findRelatedCandidates(
                List.of(asset.getId()),
                List.of(ContentSourceType.INTERNAL_NEWS),
                NOW.minusSeconds(86400),
                NOW))
        .isEmpty();
  }
}
