package org.grit.daynomy.portfolio.v1.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

class OpenAiPortfolioAnalysisClientV1Test {
  private final ObjectMapper mapper = new ObjectMapper();
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(OpenAiPortfolioAnalysisClientV1.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private MockWebServer server;
  private OpenAiPortfolioAnalysisClientV1 client;

  @BeforeEach
  void setUp() throws Exception {
    appender.start();
    logger.addAppender(appender);
    server = new MockWebServer();
    ((okhttp3.mockwebserver.QueueDispatcher) server.getDispatcher()).setFailFast(true);
    server.start();
    client =
        new OpenAiPortfolioAnalysisClientV1(
            RestClient.builder(), server.url("/").toString(), "test-key", "test-model");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void preservesOriginalPromptAndPersonalWeightsInOpenAiRequest() throws Exception {
    try (var server = new MockWebServer()) {
      server.start();
      server.enqueue(new MockResponse().setResponseCode(500));
      var client =
          new OpenAiPortfolioAnalysisClientV1(
              RestClient.builder(), server.url("/").toString(), "test-key", "test-model");
      assertThatThrownBy(
              () ->
                  client.analyze(
                      List.of(
                          new PortfolioAnalysisTargetV1("삼성전자", new BigDecimal("80")),
                          new PortfolioAnalysisTargetV1("SK하이닉스", new BigDecimal("20")))))
          .isInstanceOf(BusinessException.class);

      var request = server.takeRequest(5, TimeUnit.SECONDS);
      assertThat(request).isNotNull();
      assertThat(request.getPath()).isEqualTo("/responses");
      var mapper = new ObjectMapper();
      var body = mapper.readTree(request.getBody().readUtf8());
      assertThat(body.path("model").asText()).isEqualTo("test-model");
      assertThat(body.path("input").get(0).path("content").asText())
          .contains("사용자의 포트폴리오 자산에 미치는 영향을 분석하세요.")
          .doesNotContain("사용자 보유 비중·수량이나 함께 요청된 다른 종목을 추측하거나");
      var input = mapper.readTree(body.path("input").get(1).path("content").asText());
      assertThat(input.path("assets").get(0).path("weight").decimalValue())
          .isEqualByComparingTo("80");
      assertThat(input.path("assets").get(1).path("weight").decimalValue())
          .isEqualByComparingTo("20");
    }
  }

  @Test
  void assignsOneRequiredSlotPerAssetAndReturnsAllFiftyAssets() throws Exception {
    var targets =
        IntStream.range(0, 50)
            .mapToObj(i -> new PortfolioAnalysisTargetV1("종목" + i, new BigDecimal("2")))
            .toList();
    var impacts = mapper.createObjectNode();
    targets.forEach(target -> impacts.set(target.assetName(), validImpact()));
    enqueueOutput(impacts);

    var result = client.analyze(targets);

    assertThat(result.impacts())
        .extracting(impact -> impact.assetName())
        .containsExactlyInAnyOrderElementsOf(
            targets.stream().map(PortfolioAnalysisTargetV1::assetName).toList());
    assertThat(result.impacts())
        .allSatisfy(
            impact -> {
              assertThat(impact.direction()).isNotNull();
              assertThat(impact.sources()).isNotEmpty();
            });
    assertThat(server.getRequestCount()).isEqualTo(1);
    var request = mapper.readTree(server.takeRequest().getBody().readUtf8());
    var schema = request.path("text").path("format").path("schema");
    var slots = schema.path("properties").path("impacts");
    assertThat(slots.path("type").asText()).isEqualTo("object");
    assertThat(slots.path("additionalProperties").asBoolean()).isFalse();
    assertThat(slots.path("required").size()).isEqualTo(50);
    assertThat(slots.path("properties").size()).isEqualTo(50);
    for (var target : targets) {
      assertThat(slots.path("properties").path(target.assetName()).path("$ref").asText())
          .isEqualTo("#/$defs/assetImpact");
    }
    for (var variant : schema.path("$defs").path("assetImpact").path("anyOf")) {
      assertThat(variant.path("properties").has("assetName")).isFalse();
    }
  }

  @Test
  void correctsOnlyTheInsufficientAssetUsingItsFixedSlot() throws Exception {
    var impacts = mapper.createObjectNode();
    impacts.set("삼성전자", validImpact());
    var insufficient = validImpact().putNull("direction").putNull("impactLevel");
    insufficient.putArray("sourceUrls");
    insufficient.putArray("evidence");
    impacts.set("현대차", insufficient);
    enqueueOutput(impacts);
    enqueueOutput(mapper.createObjectNode().set("현대차", validImpact()));

    var result = client.analyze(targets());

    assertThat(result.impacts())
        .hasSize(2)
        .allSatisfy(impact -> assertThat(impact.direction()).isNotNull());
    assertThat(server.getRequestCount()).isEqualTo(2);
    server.takeRequest();
    var correction = mapper.readTree(server.takeRequest().getBody().readUtf8());
    var slots =
        correction.path("text").path("format").path("schema").path("properties").path("impacts");
    assertThat(slots.path("required").toString()).isEqualTo("[\"현대차\"]");
    var previous = mapper.readTree(correction.path("input").get(2).path("content").asText());
    assertThat(previous.path("impacts").size()).isEqualTo(1);
    assertThat(previous.path("impacts").has("현대차")).isTrue();
    assertThat(previous.path("impacts").has("삼성전자")).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"현대자동차, UNKNOWN_ASSET", "삼성전자, DUPLICATE_ASSET"})
  void distinguishesUnknownAndDuplicateNamesInLegacyResponses(
      String rejectedAssetName, String expectedReason) throws Exception {
    var impacts = mapper.createArrayNode();
    impacts.add(validImpact().put("assetName", "삼성전자"));
    impacts.add(validImpact().put("assetName", rejectedAssetName));
    enqueueOutput(impacts);

    assertThatThrownBy(() -> client.analyze(targets()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED));

    assertThat(server.getRequestCount()).isEqualTo(1);
    assertThat(failureFields())
        .containsEntry("validationReason", expectedReason)
        .containsEntry("validationField", "assetName")
        .containsEntry("validationAssetName", rejectedAssetName);
    assertThat(failureFields().toString()).doesNotContain("test-key", "sourceUrls", "weight");
  }

  @Test
  void rejectsMissingAssetRatherThanReturningIncompleteSuccess() throws Exception {
    enqueueOutput(mapper.createObjectNode().set("삼성전자", validImpact()));
    assertThatThrownBy(() -> client.analyze(targets())).isInstanceOf(BusinessException.class);
    assertThat(failureFields()).containsEntry("validationReason", "MISSING_ASSET");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void rejectsDuplicateJsonKeysRatherThanSilentlyReplacingAnAsset() throws Exception {
    String impact = mapper.writeValueAsString(validImpact());
    enqueueText("{\"impacts\":{\"삼성전자\":" + impact + ",\"삼성전자\":" + impact + "}}");
    assertThatThrownBy(() -> client.analyze(targets())).isInstanceOf(BusinessException.class);
    assertThat(failureFields()).containsEntry("validationReason", "INVALID_ANALYSIS_JSON");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  private List<PortfolioAnalysisTargetV1> targets() {
    return List.of(
        new PortfolioAnalysisTargetV1("삼성전자", new BigDecimal("80")),
        new PortfolioAnalysisTargetV1("현대차", new BigDecimal("20")));
  }

  private ObjectNode validImpact() {
    return mapper.valueToTree(
        Map.of(
            "direction",
            "NEUTRAL",
            "impactLevel",
            "LOW",
            "issueSummary",
            "관련 이슈를 확인했어요.",
            "expectedReaction",
            "중립적인 반응이 예상돼요.",
            "outlook",
            "추가 흐름을 확인해야 해요.",
            "reason",
            "직접적인 영향이 제한적이에요.",
            "sourceUrls",
            List.of("https://example.com/announcement"),
            "evidence",
            List.of(
                Map.of(
                    "url",
                    "https://example.com/announcement",
                    "publishedOn",
                    "2026-10-12",
                    "occurredOn",
                    "2026-10-12",
                    "excerpt",
                    "확인된 사업 변화예요.",
                    "factQuote",
                    "Operating profit increased by 10%."))));
  }

  private void enqueueOutput(com.fasterxml.jackson.databind.JsonNode impacts) throws Exception {
    enqueueText(mapper.createObjectNode().set("impacts", impacts).toString());
  }

  private void enqueueText(String output) throws Exception {
    String response =
        mapper.writeValueAsString(
            Map.of(
                "output",
                List.of(
                    Map.of("type", "web_search_call", "status", "completed"),
                    Map.of(
                        "content",
                        List.of(
                            Map.of(
                                "type",
                                "output_text",
                                "text",
                                output,
                                "annotations",
                                List.of(
                                    Map.of(
                                        "type",
                                        "url_citation",
                                        "title",
                                        "공식 발표",
                                        "url",
                                        "https://example.com/announcement"))))))));
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(response));
  }

  private Map<String, Object> failureFields() {
    var event =
        appender.list.stream()
            .filter(log -> "포트폴리오 분석 실패".equals(log.getFormattedMessage()))
            .findFirst()
            .orElseThrow();
    return event.getKeyValuePairs().stream()
        .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
  }
}
