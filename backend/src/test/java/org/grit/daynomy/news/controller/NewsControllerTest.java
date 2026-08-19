package org.grit.daynomy.news.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.grit.daynomy.keyword.repository.NewsKeywordRepository;
import org.grit.daynomy.market.repository.NewsMarketAnalysisRepository;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NewsControllerTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @LocalServerPort private int port;

  @Autowired private NewsRepository newsRepository;

  @Autowired private NewsKeywordRepository newsKeywordRepository;

  @Autowired private NewsMarketAnalysisRepository newsMarketAnalysisRepository;

  @BeforeEach
  void setUp() {
    newsMarketAnalysisRepository.deleteAll();
    newsKeywordRepository.deleteAll();
    newsRepository.deleteAll();
  }

  @Test
  @DisplayName("뉴스 목록 조회 API는 페이지 응답을 반환한다")
  void findNewsReturnsPagedNews() throws Exception {
    newsRepository.save(
        new News(
            "stock news",
            "content",
            "description",
            "image.png",
            Category.STOCK,
            LocalDateTime.of(2026, 8, 17, 10, 0)));

    HttpResponse<String> response = get("/api/news?page=1&size=15");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.at("/items")).hasSize(1);
    assertThat(body.at("/items/0/title").asText()).isEqualTo("stock news");
    assertThat(body.at("/page").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("뉴스 목록 조회 API는 카테고리로 필터링한다")
  void findNewsFiltersByCategory() throws Exception {
    newsRepository.save(
        new News(
            "stock news",
            "content",
            "description",
            "image.png",
            Category.STOCK,
            LocalDateTime.of(2026, 8, 17, 10, 0)));
    newsRepository.save(
        new News(
            "estate news",
            "content",
            "description",
            "image.png",
            Category.REAL_ESTATE,
            LocalDateTime.of(2026, 8, 17, 9, 0)));

    HttpResponse<String> response = get("/api/news?category=REAL_ESTATE");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.at("/items")).hasSize(1);
    assertThat(body.at("/items/0/category").asText()).isEqualTo("REAL_ESTATE");
  }

  @Test
  @DisplayName("오늘의 뉴스 조회 API는 오늘 발행된 최신 뉴스를 반환한다")
  void findTodayNewsReturnsLatestNewsPublishedToday() throws Exception {
    LocalDate today = LocalDate.now();
    newsRepository.save(
        new News(
            "yesterday news",
            "content",
            "description",
            "image.png",
            Category.STOCK,
            today.minusDays(1).atTime(23, 0)));
    newsRepository.save(
        new News(
            "morning news",
            "content",
            "description",
            "image.png",
            Category.STOCK,
            today.atTime(9, 0)));
    newsRepository.save(
        new News(
            "latest today news",
            "content",
            "description",
            "image.png",
            Category.REAL_ESTATE,
            today.atTime(18, 0)));

    HttpResponse<String> response = get("/api/news/today");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.at("/title").asText()).isEqualTo("latest today news");
    assertThat(body.at("/category").asText()).isEqualTo("REAL_ESTATE");
  }

  @Test
  @DisplayName("오늘의 뉴스 조회 API는 오늘 발행된 뉴스가 없으면 빈 응답을 반환한다")
  void findTodayNewsReturnsNullWhenMissing() throws Exception {
    LocalDate today = LocalDate.now();
    newsRepository.save(
        new News(
            "yesterday news",
            "content",
            "description",
            "image.png",
            Category.STOCK,
            today.minusDays(1).atTime(23, 0)));

    HttpResponse<String> response = get("/api/news/today");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isBlank();
  }

  @Test
  @DisplayName("뉴스 상세 조회 API는 뉴스 본문을 반환한다")
  void findNewsDetailReturnsNews() throws Exception {
    News news =
        newsRepository.save(
            new News(
                "detail news",
                "content",
                "description",
                "image.png",
                Category.STOCK,
                LocalDateTime.of(2026, 8, 17, 10, 0)));

    HttpResponse<String> response = get("/api/news/" + news.getId());
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.at("/title").asText()).isEqualTo("detail news");
    assertThat(body.at("/content").asText()).isEqualTo("content");
  }

  @Test
  @DisplayName("뉴스 상세 조회 API는 없는 뉴스에 에러 응답을 반환한다")
  void findNewsDetailReturnsNotFound() throws Exception {
    HttpResponse<String> response = get("/api/news/999");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(body.at("/code").asText()).isEqualTo("NEWS_NOT_FOUND");
    assertThat(body.at("/message").asText()).isEqualTo("해당 뉴스를 찾을 수 없습니다.");
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
