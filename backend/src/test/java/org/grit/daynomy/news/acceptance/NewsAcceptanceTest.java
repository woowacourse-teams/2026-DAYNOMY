package org.grit.daynomy.news.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
class NewsAcceptanceTest {

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
  @DisplayName("뉴스 목록을 실제 HTTP 요청으로 조회한다")
  void findNewsPage() {
    newsRepository.save(
        createNews(
            "stock news", "external-1", Category.STOCK, Instant.parse("2026-08-17T10:00:00Z")));
    newsRepository.save(
        createNews(
            "estate news",
            "external-2",
            Category.REAL_ESTATE,
            Instant.parse("2026-08-17T09:00:00Z")));

    given()
        .port(port)
        .queryParam("page", 1)
        .queryParam("size", 15)
        .when()
        .get("/api/news")
        .then()
        .statusCode(200)
        .body("items", hasSize(2))
        .body("items[0].title", equalTo("stock news"))
        .body("page", equalTo(1))
        .body("size", equalTo(15))
        .body("totalElements", equalTo(2));
  }

  @Test
  @DisplayName("뉴스 상세를 실제 HTTP 요청으로 조회한다")
  void findNewsDetail() {
    News news =
        newsRepository.save(
            createNews(
                "detail news",
                "external-1",
                Category.STOCK,
                Instant.parse("2026-08-17T10:00:00Z")));

    given()
        .port(port)
        .when()
        .get("/api/news/{id}", news.getId())
        .then()
        .statusCode(200)
        .body("title", equalTo("detail news"))
        .body("content", equalTo("content"))
        .body("category", equalTo("STOCK"));
  }

  @Test
  @DisplayName("오늘의 뉴스를 실제 HTTP 요청으로 조회한다")
  void findTodayNews() {
    LocalDate today = LocalDate.now();
    newsRepository.save(
        createNews("yesterday news", "external-1", Category.STOCK, atHour(today.minusDays(1), 23)));
    newsRepository.save(createNews("morning news", "external-2", Category.STOCK, atHour(today, 9)));
    newsRepository.save(
        createNews("latest today news", "external-3", Category.REAL_ESTATE, atHour(today, 18)));

    given()
        .port(port)
        .when()
        .get("/api/news/today")
        .then()
        .statusCode(200)
        .body("title", equalTo("latest today news"))
        .body("category", equalTo("REAL_ESTATE"));
  }

  private News createNews(String title, String externalId, Category category, Instant publishedAt) {
    return new News(
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

  private static Instant atHour(LocalDate date, int hour) {
    return date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant();
  }
}
