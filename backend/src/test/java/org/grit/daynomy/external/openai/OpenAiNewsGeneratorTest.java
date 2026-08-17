package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiNewsGeneratorTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("OpenAI Responses API 응답에서 생성된 뉴스를 파싱한다")
  void generateParsesResponseOutputText() throws Exception {
    OpenAiNewsGenerator generator =
        new OpenAiNewsGenerator(
            new OpenAiProperties("test-key", startServer(openAiResponse()), "test-model"));
    NewsPrompt prompt =
        new NewsPrompt(
            NewsSource.DART,
            "external-1",
            "https://dart.example/1",
            Category.STOCK,
            LocalDateTime.of(2026, 8, 17, 0, 0),
            "prompt");

    var generatedNews = generator.generate(prompt);

    assertThat(generatedNews.title()).isEqualTo("테스트 제목");
    assertThat(generatedNews.description()).isEqualTo("테스트 요약");
    assertThat(generatedNews.content()).isEqualTo("테스트 본문");
  }

  private String startServer(String responseBody) throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/responses",
        exchange -> {
          byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return "http://localhost:" + server.getAddress().getPort();
  }

  private String openAiResponse() {
    return """
        {
          "output": [
            {
              "type": "message",
              "content": [
                {
                  "type": "output_text",
                  "text": "{\\"title\\":\\"테스트 제목\\",\\"description\\":\\"테스트 요약\\",\\"content\\":\\"테스트 본문\\"}"
                }
              ]
            }
          ]
        }
        """;
  }
}
