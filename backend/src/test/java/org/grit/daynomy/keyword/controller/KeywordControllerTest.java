package org.grit.daynomy.keyword.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import org.grit.daynomy.keyword.domain.entity.NewsKeywordEntity;
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
    newsKeywordRepository.save(new NewsKeywordEntity(news, "금리 인하", "대출 수요 회복과 연결됨"));
    newsKeywordRepository.save(new NewsKeywordEntity(news, "부동산 규제", "거래량 회복 기대와 연결됨"));

    HttpResponse<String> response = get("/api/news/" + news.getId() + "/keywords");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.at("/status").asText()).isEqualTo("SUCCESS");
    assertThat(body.at("/message").asText()).isEqualTo("뉴스 키워드를 조회했습니다.");
    assertThat(body.at("/data/keywords")).hasSize(2);
    assertThat(body.at("/data/keywords/0/keyword").asText()).isEqualTo("금리 인하");
    assertThat(body.at("/data/keywords/0/description").asText()).isEqualTo("대출 수요 회복과 연결됨");
  }

  @Test
  @DisplayName("뉴스 키워드 조회 API는 없는 뉴스에 에러 응답을 반환한다")
  void findNewsKeywordsReturnsNotFound() throws Exception {
    HttpResponse<String> response = get("/api/news/999/keywords");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(body.at("/status").asText()).isEqualTo("ERROR");
    assertThat(body.at("/message").asText()).isEqualTo("해당 뉴스를 찾을 수 없습니다.");
    assertThat(body.at("/data").isNull()).isTrue();
  }

  private News createNews() {
    return new News(
        "keyword news",
        "content",
        "description",
        "image.png",
        Category.STOCK,
        LocalDateTime.of(2026, 8, 17, 10, 0));
  }

  private HttpResponse<String> get(String path) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
