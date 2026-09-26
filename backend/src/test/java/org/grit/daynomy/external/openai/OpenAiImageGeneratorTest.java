package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.grit.daynomy.news.domain.Category;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenAiImageGeneratorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private HttpServer server;
  private String requestBody;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("OpenAI Images API 응답의 Base64 이미지를 byte 배열로 변환한다")
  void generateNewsImageReturnsBytes() throws Exception {
    OpenAiImageGenerator generator =
        new OpenAiImageGenerator(
            new OpenAiProperties(
                "test-key", startServer(openAiImageResponse()), "image-model"));

    byte[] image = generator.generateNewsImage("제목");

    assertThat(image).isEqualTo("image".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("두 이미지 생성 프롬프트가 인물과 문자의 조건부 사용 기준을 공유한다")
  void imagePromptsSharePeopleAndTextGuidelines() throws Exception {
    OpenAiImageGenerator generator =
        new OpenAiImageGenerator(
            new OpenAiProperties(
                "test-key", startServer(openAiImageResponse()), "image-model"));

    generator.generateNewsImage("금리 인상 전망");
    String generalPrompt = requestPrompt();
    assertSharedImageRestrictions(generalPrompt);

    generator.generateEconomicNewsImage("금리 인상 전망", "시장 금리가 상승했다", Category.STOCK);
    String economicPrompt = requestPrompt();
    assertThat(economicPrompt.replaceAll("\\s+", " "))
        .contains("First compose the scene without people")
        .contains("only when the central event cannot be understood otherwise");
    assertSharedImageRestrictions(economicPrompt);
  }

  @Test
  @DisplayName("경제 뉴스 이미지 프롬프트가 실제 카메라의 자연스러운 선명도 차이를 요구한다")
  void economicNewsImagePromptUsesNaturalCameraRendering() throws Exception {
    OpenAiImageGenerator generator =
        new OpenAiImageGenerator(
            new OpenAiProperties(
                "test-key", startServer(openAiImageResponse()), "image-model"));

    generator.generateEconomicNewsImage("원료 공급 계약", "바이오 원료 공급 계약을 체결했다", Category.STOCK);

    assertThat(requestPrompt().replaceAll("\\s+", " "))
        .contains("use one believable focal plane")
        .contains("details gradually soften with distance and depth")
        .contains("gentle lens softness")
        .contains("natural highlight roll-off")
        .contains("subtle sensor grain")
        .contains("restrained micro-contrast")
        .contains("Avoid edge-to-edge sharpness")
        .contains("aggressive HDR")
        .contains("artificial sharpening")
        .contains("perfectly uniform textures")
        .contains("not a digitally perfected image");
  }

  private String startServer(String responseBody) throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/images/generations",
        exchange -> {
          requestBody =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return "http://localhost:" + server.getAddress().getPort();
  }

  private String requestPrompt() throws IOException {
    JsonNode request = OBJECT_MAPPER.readTree(requestBody);
    return request.path("prompt").asText();
  }

  private void assertSharedImageRestrictions(String prompt) {
    String normalizedPrompt = prompt.replaceAll("\\s+", " ");
    assertThat(normalizedPrompt)
        .contains("Start by composing an entirely unoccupied scene with no people")
        .contains("Treat a people-free image as the default and strongest preference")
        .contains("only as a strict exception")
        .contains(
            "removing all people would make the central event itself visually incomprehensible")
        .contains("factory, laboratory, hospital, store, construction site, or market")
        .contains("is never by itself a reason to include a worker")
        .contains(
            "If objects, machinery, products, documents, buildings, landscapes, or materials can carry the story, show no people")
        .contains(
            "facilities, production, contracts, supply, investment, earnings, logistics, technology, or research")
        .contains("If and only if a person is indispensable")
        .contains("exactly one anonymous, non-identifiable person")
        .contains("shown from behind or with their face fully obscured")
        .contains(
            "Never include crowds, groups, background figures, silhouettes, reflections of people")
        .contains("Do not add visible writing by default")
        .contains("Include background text or numerals only when they naturally belong")
        .contains("clean and correctly formed")
        .contains("never malformed, scrambled, misspelled, or like gibberish")
        .contains("Preserve the language of each text element")
        .contains("render Korean content in Korean and English content in English")
        .contains("Mixed languages are acceptable when natural to the setting")
        .contains("Never invent factual company names, ticker symbols, prices, dates, headlines")
        .contains("Do not copy the article title or context into the image")
        .contains("Render readable text or values only when exact content is explicitly supplied")
        .contains("softly out of focus so no inaccurate content is legible")
        .contains("omit the text if it cannot be rendered cleanly");
  }

  private String openAiImageResponse() {
    return """
        {
          "data": [
            {
              "b64_json": "aW1hZ2U="
            }
          ]
        }
        """;
  }
}
