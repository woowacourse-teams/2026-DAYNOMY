package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
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
            .setBody(successResponse()));
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

    assertThat(generatedNews).hasSize(3);
    RecordedRequest request = server.takeRequest();
    String requestBody = request.getBody().readUtf8();
    assertThat(requestBody)
        .contains("특정 대기업이나 인기 종목의 뉴스만 반복하지 마세요")
        .contains("코스피·코스닥의 다양한 업종과 규모의 상장 기업")
        .contains("대형주와 중소형주, 코스피와 코스닥");
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
