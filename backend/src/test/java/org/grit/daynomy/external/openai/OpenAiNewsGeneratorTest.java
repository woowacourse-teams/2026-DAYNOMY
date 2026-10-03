package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.domain.Category;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiNewsGeneratorTest {

  private MockWebServer server;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  @DisplayName("뉴스 생성 요청은 대형주 편중을 줄이고 다양한 기업과 경제 이슈를 검색하도록 지시한다")
  void generateEconomicNewsUsesDiversifiedPrompt() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(successResponseWithFourArticles()));
    OpenAiNewsGenerator generator =
        new OpenAiNewsGenerator(
            new OpenAiProperties(
                "test-key",
                server.url("/v1").toString(),
                "test-image-model",
                "test-news-model",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)));

    List<GeneratedEconomicNews> generatedNews = generator.generateEconomicNews();

    assertThat(generatedNews).hasSize(4);
    assertThat(generatedNews)
        .extracting(GeneratedEconomicNews::category)
        .containsExactly(Category.STOCK, Category.STOCK, Category.ETF, Category.STOCK);
    RecordedRequest request = server.takeRequest();
    String requestBody = request.getBody().readUtf8();
    assertThat(requestBody)
        .contains("특정 대기업이나 인기 종목의 뉴스만 반복하지 마세요")
        .contains("코스피·코스닥의 다양한 업종과 규모의 상장 기업")
        .contains("대형주와 중소형주, 코스피와 코스닥")
        .contains("해라체")
        .contains("합쇼체")
        .contains("공백 포함 1,000~1,800자")
        .contains("최근 3일 이내")
        .contains("시장 분석이나 투자 의견이 아니라");
  }

  @Test
  @DisplayName("확인된 부동산 이슈가 있으면 기본 뉴스에 부동산 뉴스를 추가한다")
  void generateEconomicNewsAllowsAdditionalRealEstateArticle() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(successResponseWithFiveArticles()));
    OpenAiNewsGenerator generator =
        new OpenAiNewsGenerator(
            new OpenAiProperties(
                "test-key",
                server.url("/v1").toString(),
                "test-image-model",
                "test-news-model",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)));

    List<GeneratedEconomicNews> generatedNews = generator.generateEconomicNews();

    assertThat(generatedNews).hasSize(5);
    assertThat(generatedNews)
        .extracting(GeneratedEconomicNews::category)
        .containsExactly(
            Category.STOCK,
            Category.STOCK,
            Category.STOCK,
            Category.REAL_ESTATE,
            Category.REAL_ESTATE);
  }

  @Test
  @DisplayName("본문 검증에 실패하면 최대 재시도 횟수 내에서 뉴스를 다시 생성한다")
  void generateEconomicNewsRetriesWhenContentValidationFails() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(successResponseWithFourArticles(false)));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(successResponseWithFourArticles(true)));
    OpenAiNewsGenerator generator =
        new OpenAiNewsGenerator(
            new OpenAiProperties(
                "test-key",
                server.url("/v1").toString(),
                "test-image-model",
                "test-news-model",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)));

    List<GeneratedEconomicNews> generatedNews = generator.generateEconomicNews();

    assertThat(generatedNews).hasSize(4);
    server.takeRequest();
    String retryRequestBody = server.takeRequest().getBody().readUtf8();
    assertThat(retryRequestBody)
        .contains("이전 뉴스 초안이 본문 규칙 검증에 실패했습니다")
        .contains("공백 포함 1,000~1,800자");
  }

  @Test
  @DisplayName("본문 검증에 계속 실패하면 최대 세 번 요청한 뒤 생성에 실패한다")
  void generateEconomicNewsStopsAfterMaximumAttempts() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(successResponseWithFourArticles(false)));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(successResponseWithFourArticles(false)));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(successResponseWithFourArticles(false)));
    OpenAiNewsGenerator generator =
        new OpenAiNewsGenerator(
            new OpenAiProperties(
                "test-key",
                server.url("/v1").toString(),
                "test-image-model",
                "test-news-model",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)));

    assertThatThrownBy(generator::generateEconomicNews).isInstanceOf(BusinessException.class);
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  private String successResponseWithFourArticles() throws Exception {
    return successResponseWithFourArticles(true);
  }

  private String successResponseWithFourArticles(boolean validContent) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    List<Map<String, Object>> sources =
        List.of(
            Map.of("type", "url_citation", "url", "https://example.com/1", "title", "출처 1"),
            Map.of("type", "url_citation", "url", "https://example.com/2", "title", "출처 2"),
            Map.of("type", "url_citation", "url", "https://example.com/3", "title", "출처 3"),
            Map.of("type", "url_citation", "url", "https://example.com/4", "title", "출처 4"),
            Map.of("type", "url_citation", "url", "https://example.com/5", "title", "출처 5"),
            Map.of("type", "url_citation", "url", "https://example.com/6", "title", "출처 6"),
            Map.of("type", "url_citation", "url", "https://example.com/7", "title", "출처 7"),
            Map.of("type", "url_citation", "url", "https://example.com/8", "title", "출처 8"));
    List<Map<String, Object>> articles =
        List.of(
            article("첫 번째 뉴스", "첫 번째 본문", "STOCK", 1, validContent),
            article("두 번째 뉴스", "두 번째 본문", "STOCK", 3, validContent),
            article("세 번째 뉴스", "세 번째 본문", "ETF", 5, validContent),
            article("네 번째 뉴스", "네 번째 본문", "STOCK", 7, validContent));
    String generatedArticles = objectMapper.writeValueAsString(Map.of("articles", articles));
    Map<String, Object> outputText =
        Map.of(
            "type", "output_text",
            "text", generatedArticles,
            "annotations", sources);
    return objectMapper.writeValueAsString(
        Map.of("output", List.of(Map.of("content", List.of(outputText)))));
  }

  private Map<String, Object> article(
      String title, String content, String category, int source, boolean validContent) {
    return Map.of(
        "title",
        title,
        "content",
        validContent ? validContent(content) : content,
        "category",
        category,
        "sourceUrls",
        List.of("https://example.com/" + source, "https://example.com/" + (source + 1)));
  }

  private String validContent(String label) {
    String paragraph =
        "공식 자료에 따르면 계약 대상과 금액, 일정이 확인됐다. 관련 기업은 공개된 조건에 따라 사업을 진행하며, 계약 이행 과정과 결과는 향후 공시와 자료를 통해 확인할 수 있다. ";
    return String.join(
        "\n\n",
        label + " " + paragraph.repeat(3),
        "계약의 주요 조건과 사업 범위가 공개됐다. " + paragraph.repeat(3),
        "관련 기업과 기관은 공개된 일정에 따라 후속 절차를 진행한다. " + paragraph.repeat(3),
        "계약 기간과 공급 대상은 공개 자료에 기재된 내용에 따른다. " + paragraph.repeat(3),
        "추가 내용은 향후 공시와 공식 자료를 통해 확인될 예정이다. " + paragraph.repeat(3));
  }

  private String successResponseWithFiveArticles() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    List<Map<String, Object>> sources = new ArrayList<>();
    List<Map<String, Object>> articles = new ArrayList<>();
    for (int index = 0; index < 5; index++) {
      int source = index * 2 + 1;
      sources.add(
          Map.of(
              "type", "url_citation",
              "url", "https://example.com/" + source,
              "title", "출처 " + source));
      sources.add(
          Map.of(
              "type", "url_citation",
              "url", "https://example.com/" + (source + 1),
              "title", "출처 " + (source + 1)));
      articles.add(
          article(
              (index + 1) + " 번째 뉴스",
              (index + 1) + " 번째 본문",
              index >= 3 ? "REAL_ESTATE" : "STOCK",
              source,
              true));
    }
    String generatedArticles = objectMapper.writeValueAsString(Map.of("articles", articles));
    Map<String, Object> outputText =
        Map.of(
            "type", "output_text",
            "text", generatedArticles,
            "annotations", sources);
    return objectMapper.writeValueAsString(
        Map.of("output", List.of(Map.of("content", List.of(outputText)))));
  }

  private String successResponse() {
    return """
        {
          "output": [{
            "content": [{
              "type": "output_text",
              "text": "{\\"articles\\":[{\\"title\\":\\"첫 번째 뉴스\\",\\"content\\":\\"첫 번째 본문\\",\\"category\\":\\"STOCK\\",\\"sourceUrls\\":[\\"https://example.com/1\\",\\"https://example.com/2\\"]},{\\"title\\":\\"두 번째 뉴스\\",\\"content\\":\\"두 번째 본문\\",\\"category\\":\\"STOCK\\",\\"sourceUrls\\":[\\"https://example.com/3\\",\\"https://example.com/4\\"]},{\\"title\\":\\"세 번째 뉴스\\",\\"content\\":\\"세 번째 본문\\",\\"category\\":\\"ETF\\",\\"sourceUrls\\":[\\"https://example.com/5\\",\\"https://example.com/6\\"]}]}",
              "annotations": [
                {"type": "url_citation", "url": "https://example.com/1", "title": "출처 1"},
                {"type": "url_citation", "url": "https://example.com/2", "title": "출처 2"},
                {"type": "url_citation", "url": "https://example.com/3", "title": "출처 3"},
                {"type": "url_citation", "url": "https://example.com/4", "title": "출처 4"},
                {"type": "url_citation", "url": "https://example.com/5", "title": "출처 5"},
                {"type": "url_citation", "url": "https://example.com/6", "title": "출처 6"}
              ]
            }]
          }]
        }
        """;
  }
}
