package org.grit.daynomy.keyword.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.keyword.repository.NewsKeywordRepository;
import org.grit.daynomy.market.repository.NewsMarketAnalysisRepository;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSourceInfo;
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
    newsKeywordRepository.save(createKeyword(news, "기준금리"));

    HttpResponse<String> response = get("/api/news/" + news.getId() + "/keywords");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.at("/keywords")).hasSize(2);
    assertThat(body.at("/keywords/0/category").asText()).isEqualTo("POLICY");
    assertThat(body.at("/keywords/0/keyword").asText()).isEqualTo("금리 인하");
    assertThat(body.at("/keywords/0/points")).hasSize(3);
    assertThat(body.at("/keywords/0/points/0").asText()).isEqualTo("키워드의 쉬운 뜻");
    assertThat(body.at("/keywords/0/definition").asText()).isEqualTo("키워드의 쉬운 뜻");
    assertThat(body.at("/keywords/0/context").asText()).isEqualTo("기사에서 키워드가 갖는 의미");
    assertThat(body.at("/keywords/0/watchPoint").asText()).isEqualTo("추가로 확인할 사항");
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
                "image.png",
                List.of(new NewsSourceInfo("DART", "https://example.com/draft-news")),
                Category.STOCK));
    newsKeywordRepository.save(createKeyword(draft, "금리 인하"));

    HttpResponse<String> response = get("/api/news/" + draft.getId() + "/keywords");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(body.at("/code").asText()).isEqualTo("NEWS_NOT_FOUND");
  }

  @Test
  @DisplayName("뉴스 키워드 조회 API는 설명할 개념이 없으면 빈 목록을 반환한다")
  void findNewsKeywordsReturnsEmptyWhenKeywordsMissing() throws Exception {
    News news = newsRepository.save(createNews());

    HttpResponse<String> response = get("/api/news/" + news.getId() + "/keywords");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.at("/keywords").isArray()).isTrue();
    assertThat(body.at("/keywords")).isEmpty();
  }

  private NewsKeyword createKeyword(News news, String keyword) {
    return new NewsKeyword(
        news, KeywordCategory.POLICY, keyword, "키워드의 쉬운 뜻", "기사에서 키워드가 갖는 의미", "추가로 확인할 사항");
  }

  private News createNews() {
    return News.createPublished(
        "keyword news",
        "content",
        "image.png",
        List.of(new NewsSourceInfo("DART", "https://example.com/keyword-news")),
        Category.STOCK,
        Instant.parse("2026-08-17T10:00:00Z"));
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
