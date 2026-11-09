package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.news.domain.Category;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class OpenAiImageGeneratorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private HttpServer server;
  private String requestBody;
  private final Logger logger = (Logger) LoggerFactory.getLogger(OpenAiImageGenerator.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private Level originalLevel;

  @BeforeEach
  void setUp() {
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("OpenAI Images API 응답의 Base64 이미지를 byte 배열로 변환한다")
  void generateEconomicNewsImageReturnsBytes() throws Exception {
    OpenAiImageGenerator generator =
        new OpenAiImageGenerator(
            new OpenAiProperties("test-key", startServer(openAiImageResponse()), "image-model"),
            org.springframework.web.client.RestClient.builder());

    byte[] image = generator.generateEconomicNewsImage("제목", "본문", Category.STOCK);

    assertThat(image).isEqualTo("image".getBytes(StandardCharsets.UTF_8));
    assertThat(keyValues(logEvent(LogEvent.AI_IMAGE_GENERATION_COMPLETED)))
        .containsKey("durationMs");
  }

  @Test
  @DisplayName("이미지 생성 프롬프트가 사람·문자·범용 금융 상징 생성을 제한한다")
  void imagePromptRestrictsPeopleAndText() throws Exception {
    OpenAiImageGenerator generator =
        new OpenAiImageGenerator(
            new OpenAiProperties("test-key", startServer(openAiImageResponse()), "image-model"),
            org.springframework.web.client.RestClient.builder());

    generator.generateEconomicNewsImage("금리 인상 전망", "시장 금리가 상승했다", Category.STOCK);
    String economicPrompt = requestPrompt();
    assertThat(economicPrompt.replaceAll("\\s+", " "))
        .contains("Compose an entirely unoccupied scene with no people first")
        .contains(
            "only when removing all people would make the central event visually incomprehensible")
        .contains("No such text is requested here, so use no text at all")
        .contains("semantic subject reference only")
        .doesNotContain("Headline:");
    assertSharedImageRestrictions(economicPrompt);
  }

  @Test
  @DisplayName("OpenAI Images API에 설정한 모델과 이미지 옵션을 전달한다")
  void generateEconomicNewsImageUsesConfiguredRequestOptions() throws Exception {
    OpenAiImageGenerator generator =
        new OpenAiImageGenerator(
            new OpenAiProperties(
                "test-key", startServer(openAiImageResponse()), "gpt-image-2.5-flare"),
            org.springframework.web.client.RestClient.builder());

    generator.generateEconomicNewsImage("금리 인상 전망", "시장 금리가 상승했다", Category.STOCK);

    JsonNode request = OBJECT_MAPPER.readTree(requestBody);
    assertThat(request.path("model").asText()).isEqualTo("gpt-image-2.5-flare");
    assertThat(request.path("quality").asText()).isEqualTo("low");
    assertThat(request.path("size").asText()).isEqualTo("1280x720");
    assertThat(request.path("output_format").asText()).isEqualTo("webp");
    assertThat(request.path("n").asInt()).isOne();
  }

  @Test
  @DisplayName("GPT Image 1.5에는 지원되는 기본 가로 크기를 전달한다")
  void generateEconomicNewsImageUsesSupportedSizeForGptImage15() throws Exception {
    OpenAiImageGenerator generator =
        new OpenAiImageGenerator(
            new OpenAiProperties("test-key", startServer(openAiImageResponse()), "gpt-image-1.5"),
            org.springframework.web.client.RestClient.builder());

    generator.generateEconomicNewsImage("금리 인상 전망", "시장 금리가 상승했다", Category.STOCK);

    JsonNode request = OBJECT_MAPPER.readTree(requestBody);
    assertThat(request.path("size").asText()).isEqualTo("1536x1024");
  }

  @Test
  @DisplayName("경제 뉴스 이미지 프롬프트가 실제 카메라의 자연스러운 선명도 차이를 요구한다")
  void economicNewsImagePromptUsesNaturalCameraRendering() throws Exception {
    OpenAiImageGenerator generator =
        new OpenAiImageGenerator(
            new OpenAiProperties("test-key", startServer(openAiImageResponse()), "image-model"),
            org.springframework.web.client.RestClient.builder());

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

  @Test
  @DisplayName("OpenAI Images API HTTP 오류를 내부 예외로 변환한다")
  void generateNewsImageConvertsHttpError() throws Exception {
    MockWebServer mockServer = new MockWebServer();
    mockServer.start();
    mockServer.enqueue(new MockResponse().setResponseCode(503));

    try {
      OpenAiImageGenerator generator = newGenerator(mockServer, Duration.ofSeconds(1));

      assertImageGenerationFailed(
          () -> generator.generateEconomicNewsImage("제목", "본문", Category.STOCK));
      assertThat(keyValues(logEvent(LogEvent.AI_IMAGE_GENERATION_FAILED)))
          .containsKey("durationMs");
    } finally {
      mockServer.shutdown();
    }
  }

  @Test
  @DisplayName("OpenAI Images API 타임아웃을 내부 예외로 변환한다")
  void generateNewsImageConvertsTimeout() throws Exception {
    MockWebServer mockServer = new MockWebServer();
    mockServer.start();
    mockServer.enqueue(
        new MockResponse()
            .setHeadersDelay(500, TimeUnit.MILLISECONDS)
            .setBody(openAiImageResponse()));

    try {
      OpenAiImageGenerator generator = newGenerator(mockServer, Duration.ofMillis(100));

      assertImageGenerationFailed(
          () -> generator.generateEconomicNewsImage("제목", "본문", Category.STOCK));
    } finally {
      mockServer.shutdown();
    }
  }

  @Test
  @DisplayName("OpenAI Images API 응답에 이미지가 없으면 내부 예외로 변환한다")
  void generateNewsImageRejectsMissingImage() throws Exception {
    OpenAiImageGenerator generator =
        new OpenAiImageGenerator(
            new OpenAiProperties("test-key", startServer("{\"data\": []}"), "image-model"),
            org.springframework.web.client.RestClient.builder());

    assertImageGenerationFailed(
        () -> generator.generateEconomicNewsImage("제목", "본문", Category.STOCK));
  }

  @Test
  @DisplayName("OpenAI Images API의 잘못된 Base64 이미지를 내부 예외로 변환한다")
  void generateNewsImageRejectsInvalidBase64() throws Exception {
    String response = "{\"data\": [{\"b64_json\": \"not-base64!\"}]}";
    OpenAiImageGenerator generator =
        new OpenAiImageGenerator(
            new OpenAiProperties("test-key", startServer(response), "image-model"),
            org.springframework.web.client.RestClient.builder());

    assertImageGenerationFailed(
        () -> generator.generateEconomicNewsImage("제목", "본문", Category.STOCK));
  }

  private OpenAiImageGenerator newGenerator(MockWebServer mockServer, Duration readTimeout) {
    OpenAiProperties properties =
        new OpenAiProperties(
            "test-key",
            mockServer.url("/").toString(),
            "image-model",
            "news-model",
            Duration.ofSeconds(1),
            readTimeout);
    return new OpenAiImageGenerator(
        properties, org.springframework.web.client.RestClient.builder());
  }

  private void assertImageGenerationFailed(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(ExternalErrorCode.AI_IMAGE_GENERATION_FAILED));
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
        .contains("Compose an entirely unoccupied scene with no people")
        .contains("Treat a people-free image as the default and strongest preference")
        .contains("removing all people would make the central event visually incomprehensible")
        .contains("is never by itself a reason to include a worker")
        .contains("Do not add people for scale, atmosphere, realism, or visual interest")
        .contains("If a person is indispensable")
        .contains("exactly one anonymous, non-identifiable person")
        .contains("shown from behind or with the face fully obscured")
        .contains(
            "Never include crowds, groups, background figures, silhouettes, reflections of people")
        .contains("Absolute text policy: the image must contain no visible text by default")
        .contains("Do not include words, letters, Korean characters, numbers, prices, dates")
        .contains("logos, brand marks, ticker symbols, labels, captions, headlines")
        .contains("signs, newspapers, documents, contracts, screens, dashboards, charts, graphs")
        .contains("watermarks, or UI elements")
        .contains("Do not create fake writing, pseudo-text, scribbles, or random glyphs")
        .contains("Only include a specific text element when the request explicitly identifies it")
        .contains("No such text is requested here, so use no text at all")
        .contains("at least two concrete visual anchors specific to the supplied title")
        .contains("an image that could be reused for an unrelated finance story is invalid")
        .contains("rising or falling arrows, stacks of coins")
        .contains("generic bar, line, or candlestick charts")
        .contains("bull or bear figures, floating currency symbols")
        .contains("Never render, copy, paraphrase, translate, or visualize any words, letters")
        .contains("Final check: remove all text-like marks and all unnecessary people");
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }

  private ILoggingEvent logEvent(LogEvent event) {
    return appender.list.stream()
        .filter(loggingEvent -> event.code().equals(keyValues(loggingEvent).get("event")))
        .findFirst()
        .orElseThrow();
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
