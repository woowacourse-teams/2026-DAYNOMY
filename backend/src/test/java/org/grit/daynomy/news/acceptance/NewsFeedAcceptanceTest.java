package org.grit.daynomy.news.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
class NewsFeedAcceptanceTest {

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
  @MockitoBean private Clock clock;

  @BeforeEach
  void setUp() {
    when(clock.instant()).thenReturn(Instant.parse("2026-10-21T01:00:00Z"));
    newsRepository.deleteAll();
  }

  @Test
  void pagesLatestNewsFeed() {
    News oldest = published("첫 번째 소식", "2026-10-18T00:00:00Z");
    News middle = published("두 번째 소식", "2026-10-19T00:00:00Z");
    News latest = published("세 번째 소식", "2026-10-20T00:00:00Z");

    given()
        .port(port)
        .queryParam("size", 2)
        .when()
        .get("/api/news/feed")
        .then()
        .statusCode(200)
        .body("items.id", equalTo(List.of(latest.getId().intValue(), middle.getId().intValue())))
        .body("totalElements", equalTo(3))
        .body("totalPages", equalTo(2));

    given()
        .port(port)
        .queryParam("page", 2)
        .queryParam("size", 2)
        .when()
        .get("/api/news/feed")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].id", equalTo(oldest.getId().intValue()));
  }

  private News published(String title, String publishedAt) {
    return newsRepository.save(
        News.createPublished(
            title,
            "본문",
            null,
            List.of(),
            Category.STOCK,
            Instant.parse(publishedAt)));
  }
}
