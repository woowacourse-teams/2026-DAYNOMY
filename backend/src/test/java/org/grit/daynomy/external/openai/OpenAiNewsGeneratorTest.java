package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.grit.daynomy.news.ai.GeneratedNews;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiNewsGeneratorTest {

  private HttpServer server;
  private final List<String> requestBodies = new ArrayList<>();
  private Deque<String> responseBodies;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
    requestBodies.clear();
  }

  @Test
  @DisplayName("OpenAI Responses API 응답에서 생성된 뉴스를 파싱한다")
  void generateParsesResponseOutputText() throws Exception {
    OpenAiNewsGenerator generator =
        new OpenAiNewsGenerator(
            new OpenAiProperties(
                "test-key", startServer(openAiResponse()), "test-model", "image-model"));
    NewsPrompt prompt =
        new NewsPrompt(
            NewsSource.DART,
            "external-1",
            "https://dart.example/1",
            Category.STOCK,
            Instant.parse("2026-08-17T00:00:00Z"),
            "prompt");

    var generatedNews = generator.generate(prompt);

    assertThat(generatedNews.title()).isEqualTo("테스트 제목");
    assertThat(generatedNews.description()).isEqualTo("테스트 요약");
    assertThat(generatedNews.content()).isEqualTo("테스트 본문");
  }

  @Test
  @DisplayName("구조화된 DART 프롬프트를 역할별로 전송하고 검증 실패 시 재생성한다")
  void generateSendsStructuredDartInputAndRetriesInvalidNews() throws Exception {
    OpenAiNewsGenerator generator =
        new OpenAiNewsGenerator(
            new OpenAiProperties(
                "test-key",
                startServer(
                    openAiResponse("테스트 제목", "테스트 요약.", "요약:\n- 핵심 내용"),
                    openAiResponse(
                        "테스트 제목",
                        "테스트 회사의 핵심 결정이 공시됐다. 운영자금 조달을 위한 유상증자가 진행된다.",
                        "테스트 회사는 핵심 결정을 공시했다.\n\nDART 공시에 따르면 관련 일정과 금액이 공시에 기재됐다.")),
                "test-model",
                "image-model"));
    NewsPrompt prompt =
        new NewsPrompt(
            NewsSource.DART,
            "external-1",
            "https://dart.example/1",
            Category.STOCK,
            Instant.parse("2026-08-17T00:00:00Z"),
            "DART 기사 작성 지침",
            "[DART 참고 데이터]\n회사명: 테스트 회사");

    GeneratedNews generatedNews = generator.generate(prompt);

    assertThat(generatedNews.title()).isEqualTo("테스트 제목");
    assertThat(requestBodies).hasSize(2);
    assertThat(requestBodies.getFirst())
        .contains("\"role\":\"developer\"", "\"role\":\"user\"", "DART 참고 데이터");
    assertThat(requestBodies.get(1)).contains("[재작성 지침]");
  }

  @Test
  @DisplayName("구조화된 KOSIS 프롬프트를 역할별로 전송하고 검증 실패 시 재생성한다")
  void generateSendsStructuredKosisInputAndRetriesInvalidNews() throws Exception {
    OpenAiNewsGenerator generator =
        new OpenAiNewsGenerator(
            new OpenAiProperties(
                "test-key",
                startServer(
                    openAiResponse("물가 제목", "물가 요약", "첫 문단만 작성됨"),
                    openAiResponse(
                        "물가 제목",
                        "소비자물가지수 최신 수치를 설명한 요약입니다.",
                        "소비자물가지수는 최신 시점에 상승했다.\n\nKOSIS에 따르면 이전 시점보다 값이 높아졌다.")),
                "test-model",
                "image-model"));
    NewsPrompt prompt =
        new NewsPrompt(
            NewsSource.KOSIS,
            "consumer-price-index:202607",
            "https://kosis.example/consumer-price-index",
            Category.ECONOMY,
            Instant.parse("2026-08-17T00:00:00Z"),
            "KOSIS 기사 작성 지침",
            "[KOSIS 참고 데이터]\n최신 값: 113.42");

    GeneratedNews generatedNews = generator.generate(prompt);

    assertThat(generatedNews.title()).isEqualTo("물가 제목");
    assertThat(requestBodies).hasSize(2);
    assertThat(requestBodies.getFirst())
        .contains("\"role\":\"developer\"", "\"role\":\"user\"", "KOSIS 참고 데이터");
    assertThat(requestBodies.get(1)).contains("[재작성 지침]", "KOSIS 출처 표현");
  }

  @Test
  @DisplayName("구조화된 한국은행 프롬프트를 전송하고 한국은행 출처 표현을 검증한다")
  void generateValidatesStructuredBokInput() throws Exception {
    OpenAiNewsGenerator generator =
        new OpenAiNewsGenerator(
            new OpenAiProperties(
                "test-key",
                startServer(
                    openAiResponse(
                        "금리 제목",
                        "기준금리 최신 수치를 설명한 요약입니다.",
                        "한국은행 기준금리는 최신 시점에 유지됐다.\n\n한국은행 ECOS에 따르면 이전 시점과 같은 수준이다.")),
                "test-model",
                "image-model"));
    NewsPrompt prompt =
        new NewsPrompt(
            NewsSource.BOK,
            "base-rate:202607",
            "https://ecos.bok.or.kr",
            Category.ECONOMY,
            Instant.parse("2026-08-17T00:00:00Z"),
            "한국은행 기사 작성 지침",
            "[한국은행 ECOS 참고 데이터]\n최신 값: 2.75");

    GeneratedNews generatedNews = generator.generate(prompt);

    assertThat(generatedNews.title()).isEqualTo("금리 제목");
    assertThat(requestBodies).hasSize(1);
    assertThat(requestBodies.getFirst())
        .contains("\"role\":\"developer\"", "\"role\":\"user\"", "한국은행 ECOS 참고 데이터");
  }

  private String startServer(String... responseBodies) throws IOException {
    this.responseBodies = new ArrayDeque<>(List.of(responseBodies));
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/responses",
        exchange -> {
          requestBodies.add(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = this.responseBodies.removeFirst().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return "http://localhost:" + server.getAddress().getPort();
  }

  private String openAiResponse() {
    return openAiResponse("테스트 제목", "테스트 요약", "테스트 본문");
  }

  private String openAiResponse(String title, String description, String content) {
    String outputText =
        "{\"title\":\""
            + title
            + "\",\"description\":\""
            + description
            + "\",\"content\":\""
            + content.replace("\\", "\\\\").replace("\n", "\\n")
            + "\"}";
    return """
        {
          "output": [
            {
              "type": "message",
              "content": [
                {
                  "type": "output_text",
                  "text": "%s"
                }
              ]
            }
          ]
        }
        """
        .formatted(outputText.replace("\\", "\\\\").replace("\"", "\\\""));
  }
}
