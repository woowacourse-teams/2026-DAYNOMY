package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
  void generatorResearchesFactsThenWritesAllArticlesInOneRequest() throws Exception {
    server.enqueue(jsonResponse(researchResponse()));
    server.enqueue(
        jsonResponse(
            writerResponse(
                List.of(
                    validContent("뉴스 0"),
                    validContent("뉴스 1"),
                    validContent("뉴스 2"),
                    validContent("뉴스 3")))));

    OpenAiNewsGenerator generator = newGenerator();

    List<GeneratedEconomicNews> generatedNews = generator.generateEconomicNews();

    assertThat(generatedNews).hasSize(4);
    assertThat(generatedNews)
        .extracting(GeneratedEconomicNews::category)
        .containsExactly(Category.STOCK, Category.STOCK, Category.ETF, Category.STOCK);
    String researchRequest = server.takeRequest().getBody().readUtf8();
    assertThat(researchRequest)
        .contains("web_search")
        .contains("최근 3일")
        .contains("독립된 출처 2곳 이상")
        .contains("한국에서 발행된");
    String writerRequest = server.takeRequest().getBody().readUtf8();
    assertThat(writerRequest)
        .doesNotContain("web_search")
        .contains("공백 포함 1,000~1,800자")
        .contains("해라체")
        .contains("시장 분석, 투자 의견")
        .contains("조사 자료 묶음");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void researcherRetriesWhenRecentDateValidationFails() throws Exception {
    server.enqueue(jsonResponse(researchResponse("2026-09-01")));
    server.enqueue(jsonResponse(researchResponse(LocalDate.now(ZoneId.of("Asia/Seoul")).toString())));

    new OpenAiNewsResearcher(properties()).researchEconomicNews();

    assertThat(server.getRequestCount()).isEqualTo(2);
    assertThat(server.takeRequest()).isNotNull();
    assertThat(server.takeRequest().getBody().readUtf8())
        .contains("이전 조사 결과가 최신성");
  }

  @Test
  void writerRetriesWhenContentValidationFails() throws Exception {
    server.enqueue(jsonResponse(writerResponse(List.of("짧은 본문"))));
    server.enqueue(jsonResponse(writerResponse(List.of(validContent("재작성된 뉴스")))));
    EconomicNewsResearch research =
        new EconomicNewsResearch(
            "뉴스 제목",
            LocalDate.now(ZoneId.of("Asia/Seoul")),
            Category.STOCK,
            List.of("첫 번째 확인 사실", "두 번째 확인 사실", "세 번째 확인 사실"),
            List.of(
                new NewsSourceInfo("출처 1", "https://example.com/1"),
                new NewsSourceInfo("출처 2", "https://example.com/2")));

    List<GeneratedEconomicNews> generated =
        new OpenAiNewsWriter(properties()).write(List.of(research));

    assertThat(generated).hasSize(1);
    assertThat(generated.getFirst().title()).isEqualTo("뉴스 제목");
    assertThat(generated.getFirst().content()).hasSizeGreaterThanOrEqualTo(1_000);
    assertThat(server.getRequestCount()).isEqualTo(2);
    server.takeRequest();
    assertThat(server.takeRequest().getBody().readUtf8())
        .contains("이전 본문 묶음이 형식 검증에 실패했다");
  }

  private OpenAiNewsGenerator newGenerator() {
    OpenAiProperties properties = properties();
    return new OpenAiNewsGenerator(
        new OpenAiNewsResearcher(properties), new OpenAiNewsWriter(properties));
  }

  private OpenAiProperties properties() {
    return new OpenAiProperties(
        "test-key",
        server.url("/v1").toString(),
        "test-image-model",
        "test-news-model",
        Duration.ofSeconds(1),
        Duration.ofSeconds(1));
  }

  private MockResponse jsonResponse(String body) {
    return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
  }

  private String researchResponse() throws Exception {
    return researchResponse(LocalDate.now(ZoneId.of("Asia/Seoul")).toString());
  }

  private String researchResponse(String date) throws Exception {
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
            researchArticle("첫 번째 뉴스", date, "STOCK", 1),
            researchArticle("두 번째 뉴스", date, "STOCK", 3),
            researchArticle("세 번째 뉴스", date, "ETF", 5),
            researchArticle("네 번째 뉴스", date, "STOCK", 7));
    Map<String, Object> outputText =
        Map.of(
            "type",
            "output_text",
            "text",
            objectMapper.writeValueAsString(Map.of("articles", articles)),
            "annotations",
            sources);
    return objectMapper.writeValueAsString(
        Map.of("output", List.of(Map.of("content", List.of(outputText)))));
  }

  private Map<String, Object> researchArticle(String title, String date, String category, int source) {
    return Map.of(
        "title",
        title,
        "date",
        date,
        "facts",
        List.of("첫 번째 확인 사실", "두 번째 확인 사실", "세 번째 확인 사실"),
        "category",
        category,
        "sourceUrls",
        List.of("https://example.com/" + source, "https://example.com/" + (source + 1)));
  }

  private String writerResponse(List<String> contents) throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    List<Map<String, String>> articles =
        contents.stream().map(content -> Map.of("content", content)).toList();
    String writerJson = objectMapper.writeValueAsString(Map.of("articles", articles));
    return objectMapper.writeValueAsString(
        Map.of(
            "output",
            List.of(
                Map.of(
                    "content",
                    List.of(Map.of("type", "output_text", "text", writerJson))))));
  }

  private String validContent(String label) {
    String paragraph =
        "공식 자료에 따르면 계약 대상과 금액, 일정이 확인됐다. 관련 기업은 공개된 조건에 따라 사업을 진행하며 계약 이행 과정은 향후 자료를 통해 확인할 수 있다. ";
    return String.join(
        "\n\n",
        label + " " + paragraph.repeat(3),
        "계약의 주요 조건과 사업 범위가 공개됐다. " + paragraph.repeat(3),
        "관련 기업과 기관은 공개된 일정에 따라 후속 절차를 진행한다. " + paragraph.repeat(3),
        "계약 기간과 공급 대상은 공개 자료에 기재된 내용에 따른다. " + paragraph.repeat(3),
        "추가 내용은 향후 공시와 공식 자료를 통해 확인될 예정이다. " + paragraph.repeat(3));
  }
}
