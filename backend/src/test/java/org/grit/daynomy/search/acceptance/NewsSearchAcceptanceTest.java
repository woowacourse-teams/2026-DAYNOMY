package org.grit.daynomy.search.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.Instant;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSource;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class NewsSearchAcceptanceTest {

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

  @BeforeEach
  void setUp() {
    newsRepository.deleteAll();
  }

  @Test
  @DisplayName("뉴스 검색 API는 실제 HTTP 요청의 검색어와 카테고리에 맞는 뉴스 페이지를 반환한다")
  void searchNews() {
    newsRepository.save(
        createNews("기준금리 동결", "search-bond", Category.BOND, Instant.parse("2026-08-17T10:00:00Z")));
    newsRepository.save(
        createNews("증시 반등", "search-stock", Category.STOCK, Instant.parse("2026-08-17T09:00:00Z")));
    newsRepository.save(
        News.createDraft(
            "기준금리 초안",
            "content",
            "description",
            "image.png",
            NewsSource.DART,
            "search-draft",
            "https://example.com/search-draft",
            Category.BOND));

    given()
        .port(port)
        .queryParam("q", "금리")
        .queryParam("category", "BOND")
        .queryParam("page", 1)
        .queryParam("size", 20)
        .when()
        .get("/api/search/news")
        .then()
        .statusCode(200)
        .body("content", hasSize(1))
        .body("content[0].title", equalTo("기준금리 동결"))
        .body("content[0].category", equalTo("BOND"))
        .body("page", equalTo(1))
        .body("size", equalTo(20))
        .body("totalElements", equalTo(1));
  }

  private News createNews(String title, String externalId, Category category, Instant publishedAt) {
    return News.createPublished(
        title,
        "content",
        "description",
        "image.png",
        NewsSource.DART,
        externalId,
        "https://example.com/" + externalId,
        category,
        publishedAt);
  }
}
