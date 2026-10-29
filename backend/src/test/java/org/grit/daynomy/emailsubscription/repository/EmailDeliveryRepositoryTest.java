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
