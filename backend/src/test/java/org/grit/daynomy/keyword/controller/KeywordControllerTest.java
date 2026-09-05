package org.grit.daynomy.keyword.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.keyword.repository.NewsKeywordRepository;
import org.grit.daynomy.market.repository.NewsMarketAnalysisRepository;
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

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KeywordControllerTest {

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
  @DisplayName("뉴스 키워드 조회 API는 뉴스에 연결된 키워드 목록을 반환한다")
  void findNewsKeywordsReturnsKeywords() throws Exception {
    News news = newsRepository.save(createNews());
    newsKeywordRepository.save(createKeyword(news, "금리 인하"));
    newsKeywordRepository.save(createKeyword(news, "부동산 규제"));

    HttpResponse<String> response = get("/api/news/" + news.getId() + "/keywords");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.at("/keywords")).hasSize(2);
    assertThat(body.at("/keywords/0/category").asText()).isEqualTo("POLICY");
    assertThat(body.at("/keywords/0/keyword").asText()).isEqualTo("금리 인하");
    assertThat(body.at("/keywords/0/points")).hasSize(3);
    assertThat(body.at("/keywords/0/points/0").asText()).isEqualTo("첫 번째 분석 포인트");
  }

  @Test
  @DisplayName("뉴스 키워드 조회 API는 없는 뉴스에 에러 응답을 반환한다")
  void findNewsKeywordsReturnsNotFound() throws Exception {
    HttpResponse<String> response = get("/api/news/999/keywords");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(body.at("/code").asText()).isEqualTo("NEWS_NOT_FOUND");
    assertThat(body.at("/message").asText()).isEqualTo("해당 뉴스를 찾을 수 없습니다.");
  }

  @Test
  @DisplayName("뉴스 키워드 조회 API는 비발행 뉴스의 키워드를 반환하지 않는다")
  void findNewsKeywordsReturnsNotFoundWhenNewsIsNotPublished() throws Exception {
    News draft =
        newsRepository.save(
            News.createDraft(
                "draft news",
                "content",
                "description",
                "image.png",
                NewsSource.DART,
                "draft-news",
                "https://example.com/draft-news",
                Category.STOCK));
    newsKeywordRepository.save(createKeyword(draft, "금리 인하"));

    HttpResponse<String> response = get("/api/news/" + draft.getId() + "/keywords");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(body.at("/code").asText()).isEqualTo("NEWS_NOT_FOUND");
  }

  @Test
  @DisplayName("뉴스 키워드 조회 API는 키워드가 없으면 에러 응답을 반환한다")
  void findNewsKeywordsReturnsNotFoundWhenKeywordsMissing() throws Exception {
    News news = newsRepository.save(createNews());

    HttpResponse<String> response = get("/api/news/" + news.getId() + "/keywords");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(body.at("/code").asText()).isEqualTo("KEYWORD_NOT_FOUND");
    assertThat(body.at("/message").asText()).isEqualTo("해당 뉴스의 키워드를 찾을 수 없습니다.");
  }

  private NewsKeyword createKeyword(News news, String keyword) {
    return new NewsKeyword(
        news, KeywordCategory.POLICY, keyword, "첫 번째 분석 포인트", "두 번째 분석 포인트", "세 번째 분석 포인트");
  }

  private News createNews() {
    return News.createPublished(
        "keyword news",
        "content",
        "description",
        "image.png",
        NewsSource.DART,
        "keyword-news",
        "https://example.com/keyword-news",
        Category.STOCK,
        Instant.parse("2026-08-17T10:00:00Z"));
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
