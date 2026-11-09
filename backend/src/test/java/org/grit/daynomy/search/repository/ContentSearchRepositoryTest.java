package org.grit.daynomy.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.person.domain.FeaturedPerson;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.search.domain.ContentSearchSort;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.domain.NewsSearchTerms;
import org.grit.daynomy.search.dto.ContentSearchItemResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
@Import(ContentSearchRepository.class)
class ContentSearchRepositoryTest {
  private static final LocalDate FROM_DATE = LocalDate.of(2026, 10, 15);

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

  @Autowired private TestEntityManager em;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ContentSearchRepository repository;

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://www.youtube.com/watch?v=abcdefghijk&t=1",
        "https://m.youtube.com/shorts/abcdefghijk?feature=share",
        "https://www.youtube.com/embed/abcdefghijk",
        "https://youtube.com/live/abcdefghijk#anchor"
      })
  void youtubeUrlVariantsCollapseBeforePagination(String variant) {
    Asset asset = em.persist(new Asset("종목", AssetCategory.STOCK, "SEARCH001"));
    em.persist(
        AssetContent.create(
            asset, ContentSourceType.YOUTUBE, "금리 영상", "https://youtu.be/abcdefghijk"));
    AssetContent representative =
        em.persist(AssetContent.create(asset, ContentSourceType.YOUTUBE, "금리 영상", variant));
    AssetContent distinct =
        em.persist(
            AssetContent.create(
                asset, ContentSourceType.YOUTUBE, "금리 다른 영상", "https://youtu.be/zyxwvutsrqp"));
    em.flush();
    jdbc.update(
        "UPDATE stock_related_contents SET created_at = TIMESTAMPTZ '2026-10-20 00:00:00+00'");

    var first =
        repository.search(
            NewsSearchTerms.from("금리"),
            ContentSearchSource.YOUTUBE,
            1,
            1,
            ContentSearchSort.LATEST,
            FROM_DATE);
    var second =
        repository.search(
            NewsSearchTerms.from("금리"),
            ContentSearchSource.YOUTUBE,
            2,
            1,
            ContentSearchSort.LATEST,
            FROM_DATE);
    var pastLast =
        repository.search(
            NewsSearchTerms.from("금리"),
            ContentSearchSource.YOUTUBE,
            3,
            1,
            ContentSearchSort.LATEST,
            FROM_DATE);
    assertThat(first.items())
        .extracting(ContentSearchItemResponse::id)
        .containsExactly(representative.getId());
    assertThat(second.items())
        .extracting(ContentSearchItemResponse::id)
        .containsExactly(distinct.getId());
    assertThat(first.totalElements()).isEqualTo(2);
    assertThat(first.totalPages()).isEqualTo(2);
    assertThat(pastLast.items()).isEmpty();
    assertThat(pastLast.totalElements()).isEqualTo(2);
  }

  @Test
  void threadsAliasesPreferThePersonPostAndUseOnlyItsPopularityScore() {
    Asset asset = em.persist(new Asset("종목", AssetCategory.STOCK, "SEARCH002"));
    FeaturedPerson person = em.persist(FeaturedPerson.create("연구원", null, null, null, null));
    FeaturedPersonPost post =
        em.persist(
            FeaturedPersonPost.create(
                person,
                ContentSourceType.THREADS,
                "금리 전망",
                "https://www.threads.net/@investor/post/ABC/?ref=feed",
                null));
    AssetContent duplicate =
        em.persist(
            AssetContent.create(
                asset,
                ContentSourceType.THREADS,
                "금리 전망",
                "https://threads.com/@investor/post/ABC#anchor"));
    News news = published("금리 기사", "본문");
    em.flush();
    interaction("ASSET_CONTENT", duplicate.getId(), "OUTBOUND_CLICK", FROM_DATE);
    interaction("NEWS", news.getId(), "OPEN", FROM_DATE);

    var all =
        repository.search(
            NewsSearchTerms.from("금리"),
            ContentSearchSource.ALL,
            1,
            10,
            ContentSearchSort.POPULAR,
            FROM_DATE);
    assertThat(all.items())
        .extracting(ContentSearchItemResponse::kind)
        .containsExactly("NEWS", "PERSON_POST");
    var filtered =
        repository.search(
            NewsSearchTerms.from("금리"),
            ContentSearchSource.THREADS,
            1,
            10,
            ContentSearchSort.POPULAR,
            FROM_DATE);
    assertThat(filtered.items())
        .extracting(ContentSearchItemResponse::id)
        .containsExactly(post.getId());
    assertThat(filtered.totalElements()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(ContentSearchSort.class)
  void matchesLiteralTermsAcrossTitleAndBodyAndKeepsOnlyPublishedNews(ContentSearchSort sort) {
    News match = published("금%리_!'ETF 전망", "인하");
    published("금X리A!'ETF 전망", "인하");
    published("금%리_!'ETF 전망", "동결");
    em.persist(News.createDraft("금%리_!'ETF 전망", "인하", null, List.of(), Category.ETF));
    News deleted = published("금%리_!'ETF 전망", "인하");
    deleted.delete();
    em.flush();

    var result =
        repository.search(
            NewsSearchTerms.from("금%리_!'etf 인하"),
            ContentSearchSource.DAYNOMY,
            1,
            10,
            sort,
            FROM_DATE);
    assertThat(result.items())
        .extracting(ContentSearchItemResponse::id)
        .containsExactly(match.getId());
    assertThat(result.totalElements()).isEqualTo(1);
    var empty =
        repository.search(
            NewsSearchTerms.from("없는검색어"), ContentSearchSource.ALL, 1, 10, sort, FROM_DATE);
    assertThat(empty.items()).isEmpty();
    assertThat(empty.totalElements()).isZero();
    assertThat(empty.totalPages()).isZero();
  }

  @Test
  void popularityIncludesTheFirstDayExcludesOlderEventsAndBreaksTiesById() {
    News first = published("금리 첫 기사", "본문");
    News second = published("금리 둘째 기사", "본문");
    News expired = published("금리 기간 밖 기사", "본문");
    em.flush();
    interaction("NEWS", first.getId(), "OUTBOUND_CLICK", FROM_DATE);
    interaction("NEWS", second.getId(), "OPEN", FROM_DATE);
    interaction("NEWS", second.getId(), "EXPAND", FROM_DATE);
    interaction("NEWS", second.getId(), "OPEN", FROM_DATE.plusDays(1));
    interaction("NEWS", expired.getId(), "OUTBOUND_CLICK", FROM_DATE.minusDays(1));
    interaction("NEWS", expired.getId(), "OUTBOUND_CLICK", FROM_DATE.minusDays(1));

    var firstPage =
        repository.search(
            NewsSearchTerms.from("금리"),
            ContentSearchSource.ALL,
            1,
            1,
            ContentSearchSort.POPULAR,
            FROM_DATE);
    var secondPage =
        repository.search(
            NewsSearchTerms.from("금리"),
            ContentSearchSource.ALL,
            2,
            1,
            ContentSearchSort.POPULAR,
            FROM_DATE);
    var thirdPage =
        repository.search(
            NewsSearchTerms.from("금리"),
            ContentSearchSource.ALL,
            3,
            1,
            ContentSearchSort.POPULAR,
            FROM_DATE);
    assertThat(firstPage.items())
        .extracting(ContentSearchItemResponse::id)
        .containsExactly(first.getId());
    assertThat(secondPage.items())
        .extracting(ContentSearchItemResponse::id)
        .containsExactly(second.getId());
    assertThat(thirdPage.items())
        .extracting(ContentSearchItemResponse::id)
        .containsExactly(expired.getId());
    assertThat(firstPage.totalElements()).isEqualTo(3);
    assertThat(firstPage.totalPages()).isEqualTo(3);
  }

  private News published(String title, String body) {
    return em.persist(
        News.createPublished(
            title, body, null, List.of(), Category.ETF, Instant.parse("2026-10-20T00:00:00Z")));
  }

  private void interaction(String kind, Long id, String type, LocalDate date) {
    jdbc.update(
        """
        INSERT INTO content_interactions
          (event_id, deduplication_key, content_type, content_id, interaction_type,
           interaction_date, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, now(), now())
        """,
        UUID.randomUUID(),
        UUID.randomUUID().toString(),
        kind,
        id,
        type,
        date);
  }
}
