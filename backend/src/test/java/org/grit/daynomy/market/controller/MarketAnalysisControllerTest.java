package org.grit.daynomy.market.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.grit.daynomy.keyword.repository.NewsKeywordRepository;
import org.grit.daynomy.market.domain.asset.Asset;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.market.domain.scenario.TimeHorizon;
import org.grit.daynomy.market.entity.AssetImpactEntity;
import org.grit.daynomy.market.entity.NewsMarketAnalysisEntity;
import org.grit.daynomy.market.entity.ScenarioEntity;
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
class MarketAnalysisControllerTest {

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
  @DisplayName("뉴스 시장 분석 조회 API는 뉴스에 연결된 시장 분석을 반환한다")
  void findNewsMarketAnalysisReturnsAnalysis() throws Exception {
    News news = newsRepository.save(createNews());
    newsMarketAnalysisRepository.save(createMarketAnalysisEntity(news));

    HttpResponse<String> response = get("/api/news/" + news.getId() + "/market-analysis");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(body.at("/cause").asText()).isEqualTo("금리 인하 기대가 위험자산 선호를 높입니다.");
    assertThat(body.at("/assets")).hasSize(1);
    assertThat(body.at("/assets/0/asset").asText()).isEqualTo("STOCK");
    assertThat(body.at("/assets/0/direction").asText()).isEqualTo("POSITIVE");
    assertThat(body.at("/scenarios")).hasSize(1);
    assertThat(body.at("/scenarios/0/timeHorizon").asText()).isEqualTo("SHORT_TERM");
    assertThat(body.at("/scenarios/0/probability").asInt()).isEqualTo(70);
  }

  @Test
  @DisplayName("뉴스 시장 분석 조회 API는 시장 분석이 없으면 에러 응답을 반환한다")
  void findNewsMarketAnalysisReturnsNotFound() throws Exception {
    HttpResponse<String> response = get("/api/news/999/market-analysis");
    JsonNode body = objectMapper.readTree(response.body());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(body.at("/code").asText()).isEqualTo("MARKET_ANALYSIS_NOT_FOUND");
    assertThat(body.at("/message").asText()).isEqualTo("해당 뉴스의 시장 분석을 찾을 수 없습니다.");
  }

  private NewsMarketAnalysisEntity createMarketAnalysisEntity(News news) {
    return new NewsMarketAnalysisEntity(
        news,
        "금리 인하 기대가 위험자산 선호를 높입니다.",
        List.of(
            new AssetImpactEntity(
                Asset.STOCK,
                ImpactDirection.POSITIVE,
                ImpactLevel.HIGH,
                "할인율 하락 기대가 주식 밸류에이션에 긍정적입니다.")),
        List.of(
            new ScenarioEntity(
                TimeHorizon.SHORT_TERM,
                "단기적으로 주식 선호가 개선될 수 있습니다.",
                70,
                "금리 인하 기대가 투자 심리를 자극하기 때문입니다.")));
  }

  private News createNews() {
    return new News(
        "market news",
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
