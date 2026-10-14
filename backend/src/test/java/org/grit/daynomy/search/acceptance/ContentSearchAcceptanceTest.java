package org.grit.daynomy.search.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.Instant;
import java.util.List;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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

  @BeforeEach
  void setUp() {
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
}
