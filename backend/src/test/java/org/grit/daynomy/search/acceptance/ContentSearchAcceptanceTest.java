package org.grit.daynomy.search.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.repository.AssetContentRepository;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.person.domain.FeaturedPerson;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.grit.daynomy.person.repository.FeaturedPersonPostRepository;
import org.grit.daynomy.person.repository.FeaturedPersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContentSearchAcceptanceTest {

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("daynomy")
          .withUsername("daynomy")
          .withPassword("daynomy");

  @DynamicPropertySource
  static void configurePostgresql(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
  }

  @LocalServerPort private int port;
  @Autowired private NewsRepository newsRepository;
  @Autowired private FeaturedPersonRepository personRepository;
  @Autowired private FeaturedPersonPostRepository personPostRepository;
  @Autowired private AssetContentRepository contentRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private Clock clock;

  @BeforeEach
  void setUp() {
    when(clock.instant()).thenReturn(Instant.parse("2026-10-21T01:00:00Z"));
    jdbc.update("DELETE FROM content_interactions");
    contentRepository.deleteAll();
    personPostRepository.deleteAll();
    personRepository.deleteAll();
    newsRepository.deleteAll();
    assetRepository.deleteAll();
  }

  @Test
  void searchesAllPublishedFeedSourcesBeforePagination() {
    newsRepository.save(
        News.createPublished(
            "금리 동결 기사",
            "본문",
            null,
            List.of(),
            Category.ETF,
            Instant.parse("2026-08-12T00:00:00Z")));
    newsRepository.save(News.createDraft("금리 비공개 기사", "본문", null, List.of(), Category.ETF));
    FeaturedPerson person =
        personRepository.save(FeaturedPerson.create("경제 연구원", null, null, null, null));
    personPostRepository.save(
        FeaturedPersonPost.create(
            person,
            ContentSourceType.X,
            "금리 전망",
            "https://twitter.com/example/status/123",
            Instant.parse("2026-08-12T00:00:00Z")));
    Asset asset = assetRepository.save(new Asset("종목", AssetCategory.STOCK, "SEARCH001"));
    contentRepository.save(
        AssetContent.create(
            asset, ContentSourceType.X, "금리 중복 글", "https://x.com/example/status/123?ref=feed"));
    contentRepository.save(
        AssetContent.create(
            asset, ContentSourceType.YOUTUBE, "금리 해설 영상", "https://youtu.be/abcdefghijk"));

    given()
        .port(port)
        .queryParam("q", "금리")
        .queryParam("size", 2)
        .when()
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("items", hasSize(2))
        .body("totalElements", equalTo(3))
        .body("totalPages", equalTo(2));

    given()
        .port(port)
        .queryParam("q", "금리")
        .queryParam("page", 2)
        .queryParam("size", 2)
        .when()
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].kind", equalTo("NEWS"))
        .body("items[0].title", equalTo("금리 동결 기사"));

    given()
        .port(port)
        .queryParam("q", "금리")
        .queryParam("source", "YOUTUBE")
        .when()
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("totalElements", equalTo(1))
        .body("items[0].kind", equalTo("ASSET_CONTENT"))
        .body("items[0].source", equalTo("YOUTUBE"));
  }

  @Test
  void searchesLiteralTermsAndDeduplicatesYoutubeBeforePaging() {
    newsRepository.save(
        News.createPublished(
            "경제 소식",
            "금리% 전망",
            null,
            List.of(),
            Category.ETF,
            Instant.parse("2026-08-12T00:00:00Z")));
    Asset asset = assetRepository.save(new Asset("종목", AssetCategory.STOCK, "SEARCH002"));
    contentRepository.save(
        AssetContent.create(
            asset, ContentSourceType.YOUTUBE, "금리% 전망", "https://youtu.be/abcdefghijk"));
    AssetContent latest =
        contentRepository.save(
            AssetContent.create(
                asset,
                ContentSourceType.YOUTUBE,
                "금리% 전망",
                "https://www.youtube.com/watch?v=abcdefghijk&t=1"));

    given()
        .port(port)
        .queryParam("q", " 금리%  전망 금리% ")
        .queryParam("size", 1)
        .when()
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("totalElements", equalTo(2))
        .body("totalPages", equalTo(2))
        .body("items[0].id", equalTo(latest.getId().intValue()));

    given()
        .port(port)
        .queryParam("q", "금리% 전망")
        .queryParam("page", 2)
        .queryParam("size", 1)
        .when()
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("items[0].kind", equalTo("NEWS"));

    given()
        .port(port)
        .queryParam("q", "금리% 전망")
        .queryParam("page", 3)
        .queryParam("size", 1)
        .when()
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("items", hasSize(0))
        .body("totalElements", equalTo(2));

    given()
        .port(port)
        .queryParam("q", "금리_ 전망")
        .when()
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("items", hasSize(0))
        .body("totalElements", equalTo(0));
  }

  @Test
  void sortsAllMatchingResultsBySevenDayPopularityBeforePaging() {
    News popular = published("금리 인기 기사", "2026-10-01T00:00:00Z");
    News latest = published("금리 최신 기사", "2026-10-20T00:00:00Z");
    News zero = published("금리 반응 없는 기사", "2026-10-19T00:00:00Z");
    News expired = published("금리 집계 기간 밖 기사", "2026-10-19T00:00:00Z");
    News unrelated = published("다른 주제", "2026-10-21T00:00:00Z");
    News draft =
        newsRepository.save(News.createDraft("금리 비공개 기사", "본문", null, List.of(), Category.ETF));
    interaction("NEWS", popular.getId(), "OUTBOUND_CLICK", LocalDate.of(2026, 10, 15));
    interaction("NEWS", latest.getId(), "OPEN", LocalDate.of(2026, 10, 20));
    interaction("NEWS", latest.getId(), "EXPAND", LocalDate.of(2026, 10, 21));
    interaction("NEWS", expired.getId(), "OUTBOUND_CLICK", LocalDate.of(2026, 10, 14));
    interaction("NEWS", expired.getId(), "OUTBOUND_CLICK", LocalDate.of(2026, 10, 14));
    interaction("NEWS", unrelated.getId(), "OUTBOUND_CLICK", LocalDate.of(2026, 10, 21));
    interaction("NEWS", draft.getId(), "OUTBOUND_CLICK", LocalDate.of(2026, 10, 21));

    given()
        .port(port)
        .queryParam("q", "금리")
        .queryParam("size", 1)
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("items[0].id", equalTo(latest.getId().intValue()))
        .body("totalElements", equalTo(4));

    given()
        .port(port)
        .queryParam("q", "금리")
        .queryParam("sort", "POPULAR")
        .queryParam("size", 2)
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("items.id", equalTo(List.of(popular.getId().intValue(), latest.getId().intValue())))
        .body("totalElements", equalTo(4))
        .body("totalPages", equalTo(2));

    given()
        .port(port)
        .queryParam("q", "금리")
        .queryParam("sort", "POPULAR")
        .queryParam("page", 2)
        .queryParam("size", 2)
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("items.id", equalTo(List.of(zero.getId().intValue(), expired.getId().intValue())))
        .body("totalElements", equalTo(4));

    given()
        .port(port)
        .queryParam("q", "금리")
        .queryParam("sort", "POPULAR")
        .queryParam("page", 3)
        .queryParam("size", 2)
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("items", hasSize(0))
        .body("totalElements", equalTo(4));
  }

  @Test
  void usesRepresentativeContentScoresAndAppliesSourceFilters() {
    FeaturedPerson person =
        personRepository.save(FeaturedPerson.create("경제 연구원", null, null, null, null));
    FeaturedPersonPost post =
        personPostRepository.save(
            FeaturedPersonPost.create(
                person, ContentSourceType.X, "금리 전망", "https://x.com/example/status/456", null));
    Asset asset = assetRepository.save(new Asset("종목", AssetCategory.STOCK, "SEARCH003"));
    AssetContent duplicate =
        contentRepository.save(
            AssetContent.create(
                asset, ContentSourceType.X, "금리 중복 글", "https://twitter.com/example/status/456"));
    AssetContent video =
        contentRepository.save(
            AssetContent.create(
                asset, ContentSourceType.YOUTUBE, "금리 해설", "https://youtu.be/abcdefghijk"));
    LocalDate today = LocalDate.of(2026, 10, 21);
    interaction("PERSON_POST", post.getId(), "OPEN", today);
    interaction("ASSET_CONTENT", duplicate.getId(), "OUTBOUND_CLICK", today);
    interaction("ASSET_CONTENT", duplicate.getId(), "OUTBOUND_CLICK", today);
    interaction("ASSET_CONTENT", video.getId(), "OUTBOUND_CLICK", today);

    given()
        .port(port)
        .queryParam("q", "금리")
        .queryParam("sort", "POPULAR")
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("totalElements", equalTo(2))
        .body("items.kind", equalTo(List.of("ASSET_CONTENT", "PERSON_POST")))
        .body("items.id", equalTo(List.of(video.getId().intValue(), post.getId().intValue())));

    given()
        .port(port)
        .queryParam("q", "금리")
        .queryParam("source", "X")
        .queryParam("sort", "POPULAR")
        .get("/api/search/contents")
        .then()
        .statusCode(200)
        .body("totalElements", equalTo(1))
        .body("items[0].kind", equalTo("PERSON_POST"))
        .body("items[0].id", equalTo(post.getId().intValue()));
  }

  private News published(String title, String publishedAt) {
    return newsRepository.save(
        News.createPublished(
            title, "본문", null, List.of(), Category.ETF, Instant.parse(publishedAt)));
  }

  private void interaction(String kind, Long id, String type, LocalDate date) {
    Timestamp timestamp =
        Timestamp.from(date.atTime(9, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant());
    jdbc.update(
        """
        INSERT INTO content_interactions
          (event_id, deduplication_key, content_type, content_id, interaction_type,
           interaction_date, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        UUID.randomUUID().toString(),
        kind,
        id,
        type,
        date,
        timestamp,
        timestamp);
  }
}
