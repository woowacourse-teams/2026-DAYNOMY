package org.grit.daynomy.news.acceptance;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.grit.daynomy.news.repository.NewsRepository;
import org.grit.daynomy.support.SharedDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NewsAcceptanceTest extends SharedDatabaseTest {

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
    newsRepository.save(
        News.createDraft(
            "draft news",
            "content",
            "image.png",
            List.of(new NewsSourceInfo("DART", "https://example.com/draft-1")),
            Category.STOCK));
    News deletedNews =
        createNews(
            "deleted news",
            "external-deleted",
            Category.STOCK,
            Instant.parse("2026-08-17T08:00:00Z"));
    deletedNews.delete();
    newsRepository.save(deletedNews);

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
  @DisplayName("발행되지 않은 뉴스는 공개 상세 조회에서 제외한다")
  void findDraftNewsReturnsNotFound() {
    News draft =
        newsRepository.save(
            News.createDraft(
                "draft news",
                "content",
                "image.png",
                List.of(new NewsSourceInfo("DART", "https://example.com/draft-1")),
                Category.STOCK));

    given()
        .port(port)
        .when()
        .get("/api/news/{id}", draft.getId())
        .then()
        .statusCode(404)
        .body("code", equalTo("NEWS_NOT_FOUND"));
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
        .body("imageSource.name", equalTo(""))
        .body("imageSource.url", equalTo(""))
        .body("category", equalTo("STOCK"));
  }

  private News createNews(String title, String externalId, Category category, Instant publishedAt) {
    return News.createPublished(
        title,
        "content",
        "image.png",
        List.of(new NewsSourceInfo("DART", "https://example.com/" + externalId)),
        category,
        publishedAt);
  }
}
