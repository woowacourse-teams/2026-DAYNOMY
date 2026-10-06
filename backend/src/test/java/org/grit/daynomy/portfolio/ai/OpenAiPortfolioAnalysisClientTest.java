package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class OpenAiPortfolioAnalysisClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Logger logger =
      (Logger) LoggerFactory.getLogger(OpenAiPortfolioAnalysisClient.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private MockWebServer server;
  private OpenAiPortfolioAnalysisClient client;
  private Level originalLevel;

  @BeforeEach
  void setUp() throws IOException {
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
    server = new MockWebServer();
    server.start();
    client =
        new OpenAiPortfolioAnalysisClient(
            RestClient.builder(), server.url("/").toString(), "test-api-key", "test-model");
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
  }

  @Test
  @DisplayName("사용자가 입력한 종목명을 전달하고 AI 분석 결과를 영향도순으로 반환한다")
  void analyzeParsesAndSortsImpacts() throws Exception {
    enqueueOutput(
        """
        {
          "overallImpact": "반도체 비중이 높아 관련 이슈의 영향을 크게 받을 수 있어요.",
          "impacts": [
            {
              "assetName": "삼성전자",
              "direction": "POSITIVE",
              "impactLevel": "LOW",
              "issueSummary": "신규 수요가 증가할 전망이에요.",
              "expectedReaction": "장기적으로 긍정적일 수 있습니다.",
              "outlook": "수요 증가 여부를 지켜봐야 해요.",
              "reason": "신규 수요가 예상됩니다.",
              "evidenceSentence": "삼성전자의 신규 수요가 증가할 전망입니다.",
              "sourceUrls": ["https://example.com/semiconductor"]
            },
            {
              "assetName": "SK하이닉스",
              "direction": "NEUTRAL",
              "impactLevel": "HIGH",
              "issueSummary": "수요와 비용이 함께 증가했어요.",
              "expectedReaction": "방향은 불분명합니다.",
              "outlook": "비용 부담의 지속 여부를 확인해야 해요.",
              "reason": "상반된 요인이 존재합니다.",
              "evidenceSentence": "SK하이닉스의 수요와 비용이 모두 증가했습니다.",
              "sourceUrls": ["https://example.com/semiconductor"]
            }
          ]
        }
        """);

    PortfolioAnalysisResult result = client.analyze(targets());

    assertThat(result.impacts())
        .extracting(PortfolioAnalysisResult.AssetImpactResult::assetName)
        .containsExactly("SK하이닉스", "삼성전자");
    assertThat(result.impacts())
        .extracting(PortfolioAnalysisResult.AssetImpactResult::sortOrder)
        .containsExactly(1, 2);
    assertThat(result.impacts().getFirst().direction()).isEqualTo(ImpactDirection.NEUTRAL);
    assertThat(result.impacts().getFirst().impactLevel()).isEqualTo(ImpactLevel.HIGH);
    assertThat(result.overallImpact()).contains("반도체 비중");
    assertThat(result.impacts().getFirst().issueSummary()).contains("수요와 비용");
    assertThat(result.impacts().getFirst().outlook()).contains("비용 부담");
    assertThat(result.impacts().getFirst().evidenceSentence())
        .isEqualTo("SK하이닉스의 수요와 비용이 모두 증가했습니다.");
    assertThat(result.impacts().getFirst().sources())
        .containsExactly(
            new PortfolioAnalysisResult.Source("반도체 산업 동향", "https://example.com/semiconductor"));
    assertThat(result.sources())
        .containsExactly(
            new PortfolioAnalysisResult.Source("반도체 산업 동향", "https://example.com/semiconductor"));

    ILoggingEvent completedLog = appender.list.getLast();
    assertThat(completedLog.getLevel()).isEqualTo(Level.INFO);
    assertThat(completedLog.getFormattedMessage())
        .isEqualTo(LogEvent.PORTFOLIO_ANALYSIS_COMPLETED.message());
    assertThat(keyValues(completedLog))
        .containsEntry("event", "portfolio.analysis.completed")
        .containsEntry("api", "OpenAI")
        .containsEntry("operation", "portfolio-analysis")
        .containsEntry("targetCount", 2)
        .containsEntry("analyzedCount", 2)
        .containsKey("durationMs");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/responses");
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key");

    JsonNode requestBody = objectMapper.readTree(request.getBody().readUtf8());
    assertThat(requestBody.path("reasoning").path("effort").asText()).isEqualTo("low");
    assertThat(requestBody.path("tools").get(0).path("type").asText()).isEqualTo("web_search");
    assertThat(requestBody.path("tool_choice").asText()).isEqualTo("required");
    assertThat(requestBody.path("include").get(0).asText())
        .isEqualTo("web_search_call.action.sources");
    JsonNode impactsSchema =
        requestBody.path("text").path("format").path("schema").path("properties").path("impacts");
    assertThat(impactsSchema.path("minItems").asInt()).isEqualTo(2);
    assertThat(impactsSchema.path("maxItems").asInt()).isEqualTo(2);
    JsonNode userContent =
        objectMapper.readTree(requestBody.path("input").get(1).path("content").asText());
    assertThat(userContent.path("assets").get(0).path("assetName").asText()).isEqualTo("삼성전자");
    assertThat(userContent.path("assets").get(0).path("weight").decimalValue())
        .isEqualByComparingTo("60");
    assertThat(userContent.toString()).doesNotContain("assetId");
  }

  @Test
  @DisplayName("파인튜닝 모델 요청에는 지원하지 않는 배열 길이 제약을 포함하지 않는다")
  void analyzeOmitsArrayBoundsForFineTunedModel() throws Exception {
    client =
        new OpenAiPortfolioAnalysisClient(
            RestClient.builder(), server.url("/").toString(), "test-api-key", "ft:test-model");
    enqueueOutput(validOutput());

    client.analyze(targets());

    RecordedRequest request = server.takeRequest();
    JsonNode requestBody = objectMapper.readTree(request.getBody().readUtf8());
    JsonNode impactsSchema =
        requestBody.path("text").path("format").path("schema").path("properties").path("impacts");
    assertThat(impactsSchema.has("minItems")).isFalse();
    assertThat(impactsSchema.has("maxItems")).isFalse();
  }

  @Test
  @DisplayName("사용자 노출 문장을 해요체로 작성하도록 요청한다")
  void analyzeRequestsFriendlyTone() throws Exception {
    enqueueOutput(validOutput());

    client.analyze(targets());

    RecordedRequest request = server.takeRequest();
    JsonNode requestBody = objectMapper.readTree(request.getBody().readUtf8());
    String developerPrompt = requestBody.path("input").get(0).path("content").asText();

    assertThat(developerPrompt)
        .contains("issueSummary에는 자산과 관련된 오늘의 주요 이슈를 요약하세요.")
        .contains("expectedReaction에는 예상되는 자산 반응을 자연스러운 해요체로 작성하세요.")
        .contains("outlook에는 검색 결과를 근거로 향후 방향성을 작성하세요.")
        .contains("overallImpact는 투자 초보자도 쉽게 이해할 수 있도록 3~4문장으로 작성하세요.")
        .contains("overallImpact에는 포트폴리오에서 비중이 큰 자산과 주요 이슈를 먼저 설명하세요.")
        .contains("긍정·부정 요인이 전체 포트폴리오에 어떻게 작용하는지 구체적으로 설명하세요.")
        .contains("전체 포트폴리오의 최종 긍정·중립·부정 방향은 서버가 계산하므로 overallImpact에서 최종 방향을 단정하지 마세요.")
        .contains("overallImpact의 마지막 문장에는 앞으로 주의해서 볼 지표나 이슈를 안내하세요.")
        .contains("어려운 금융 용어와 단정적인 투자 권유 표현은 사용하지 마세요.")
        .contains("reason에는 판단 근거를 자연스러운 해요체로 작성하세요.")
        .contains("sourceUrls에는 해당 자산의 판단에 직접 사용한 인용 출처 URL만 포함하세요.");
  }

  @Test
  @DisplayName("웹 검색의 직접적인 근거를 기준으로 영향 방향을 판단하도록 요청한다")
  void analyzeRequestsDirectionBasedOnDirectEvidence() throws Exception {
    enqueueOutput(validOutput());

    client.analyze(targets());

    RecordedRequest request = server.takeRequest();
    JsonNode requestBody = objectMapper.readTree(request.getBody().readUtf8());
    String developerPrompt = requestBody.path("input").get(0).path("content").asText();

    assertThat(developerPrompt)
        .contains("자산의 실적, 수요, 경쟁력 또는 수급에 유리한 직접 영향이 명확하면 POSITIVE로 판단하세요.")
        .contains("자산의 실적, 수요, 경쟁력 또는 수급에 불리한 직접 영향이 명확하면 NEGATIVE로 판단하세요.")
        .contains("긍정·부정 요인이 함께 존재하면 NEUTRAL로 판단하세요.")
        .contains("시장 전반의 분위기나 일반적인 업황만으로 개별 자산의 방향을 추측하지 마세요.");
  }

  @Test
  @DisplayName("영향 수준을 HIGH, LOW, MEDIUM 우선순위에 따라 판단하도록 요청한다")
  void analyzeRequestsImpactLevelBasedOnExplicitCriteria() throws Exception {
    enqueueOutput(validOutput());

    client.analyze(targets());

    RecordedRequest request = server.takeRequest();
    JsonNode requestBody = objectMapper.readTree(request.getBody().readUtf8());
    String developerPrompt = requestBody.path("input").get(0).path("content").asText();

    assertThat(developerPrompt)
        .contains(
            "impactLevel은 direction과 관계없이 검색 결과에서 확인한 영향의 범위, 규모, 즉시성, 확실성을 기준으로 HIGH, LOW, MEDIUM 순서로 판단하세요.")
        .contains("영향 기간이 짧더라도 규모가 크면 HIGH를 유지하세요.")
        .contains("HIGH에 해당하지 않고 영향 규모가 작거나 일시적이라고 명시된 경우에는 LOW로 우선 판단하세요.")
        .contains(
            "HIGH와 LOW에 해당하지 않으면서 직접적인 영향은 명확하지만 범위가 일부 사업·제품에 한정되거나 규모 또는 시점이 불확실하면 MEDIUM으로 판단하세요.");
  }

  @Test
  @DisplayName("요청하지 않은 종목명이 AI 응답에 포함되면 분석 실패로 처리한다")
  void analyzeRejectsUnknownAssetName() throws Exception {
    enqueueOutput(
        """
        {
          "overallImpact": "전체 영향이에요.",
          "impacts": [
            {
              "assetName": "현대차",
              "direction": "POSITIVE",
              "impactLevel": "HIGH",
              "issueSummary": "수요가 증가했어요.",
              "expectedReaction": "긍정적입니다.",
              "outlook": "수요가 이어질 수 있어요.",
              "reason": "수요가 증가했습니다.",
              "evidenceSentence": "현대차 수요가 증가했습니다.",
              "sourceUrls": ["https://example.com/semiconductor"]
            }
          ]
        }
        """);

    assertAnalysisFailed(() -> client.analyze(targets()));
  }

  @Test
  @DisplayName("근거 문장이 비어 있으면 분석 실패로 처리한다")
  void analyzeRejectsBlankEvidenceSentence() throws Exception {
    enqueueOutput(
        """
        {
          "overallImpact": "전체 영향이에요.",
          "impacts": [
            {
              "assetName": "삼성전자",
              "direction": "POSITIVE",
              "impactLevel": "HIGH",
              "issueSummary": "수요가 증가했어요.",
              "expectedReaction": "긍정적입니다.",
              "outlook": "수요가 이어질 수 있어요.",
              "reason": "수요가 증가했습니다.",
              "evidenceSentence": "   ",
              "sourceUrls": ["https://example.com/semiconductor"]
            }
          ]
        }
        """);

    assertAnalysisFailed(() -> client.analyze(targets()));
  }

  @Test
  @DisplayName("AI 응답 형식이 올바르지 않으면 분석 실패로 처리한다")
  void analyzeRejectsMalformedOutput() throws Exception {
    enqueueOutput("{\"invalid\":[]}");

    assertAnalysisFailed(() -> client.analyze(targets()));
  }

  @Test
  @DisplayName("웹 검색 인용 출처가 없으면 분석 실패로 처리한다")
  void analyzeRejectsMissingSources() throws Exception {
    enqueueOutputWithoutAnnotations("{\"overallImpact\":\"전체 영향이에요.\",\"impacts\":[]}");

    assertAnalysisFailed(() -> client.analyze(targets()));
  }

  @Test
  @DisplayName("OpenAI가 인용하지 않은 자산별 출처는 결과에서 제외한다")
  void analyzeIgnoresUncitedAssetSource() throws Exception {
    enqueueOutput(
        validOutput()
            .replace(
                "[\"https://example.com/semiconductor\"]",
                "[\"https://example.com/semiconductor\", \"https://example.com/uncited\"]"));

    PortfolioAnalysisResult result = client.analyze(targets());

    assertThat(result.impacts())
        .allSatisfy(
            impact ->
                assertThat(impact.sources())
                    .containsExactly(
                        new PortfolioAnalysisResult.Source(
                            "반도체 산업 동향", "https://example.com/semiconductor")));
  }

  @Test
  @DisplayName("OpenAI 인용 URL의 UTM 추적 파라미터는 제거하고 일반 파라미터는 유지한다")
  void analyzeMatchesCitationWithTrackingParameter() throws Exception {
    String sourceUrl = "https://example.com/semiconductor?articleId=123";
    String citationUrl =
        "https://example.com/semiconductor?utm_source=openai&utm_medium=referral&UTM_Campaign=spring&articleId=123";
    enqueueOutput(
        validOutput().replace("https://example.com/semiconductor", sourceUrl), citationUrl);

    PortfolioAnalysisResult result = client.analyze(targets());

    assertThat(result.impacts())
        .allSatisfy(
            impact ->
                assertThat(impact.sources())
                    .containsExactly(new PortfolioAnalysisResult.Source("반도체 산업 동향", citationUrl)));
  }

  @Test
  @DisplayName("인용에 포함되지 않아도 웹 검색에서 사용한 출처는 자산별 근거로 연결한다")
  void analyzeUsesCompleteWebSearchSources() throws Exception {
    String searchSourceUrl = "https://example.com/consulted-source";
    enqueueOutputWithSearchSources(
        validOutput()
            .replace("\"direction\": \"NEUTRAL\"", "\"direction\": \"POSITIVE\"")
            .replace("https://example.com/semiconductor", searchSourceUrl),
        searchSourceUrl);

    PortfolioAnalysisResult result = client.analyze(targets());

    PortfolioAnalysisResult.Source expectedSource =
        new PortfolioAnalysisResult.Source("웹 검색 사용 출처", searchSourceUrl);
    assertThat(result.impacts())
        .allSatisfy(impact -> assertThat(impact.sources()).containsExactly(expectedSource));
    assertThat(result.sources()).containsExactly(expectedSource);
  }

  @Test
  @DisplayName("출처가 없는 자산은 중립이며 영향 수준이 낮을 때만 허용한다")
  void analyzeAllowsNeutralLowAssetWithoutSource() throws Exception {
    enqueueOutput(validOutput().replace("[\"https://example.com/semiconductor\"]", "[]"));

    PortfolioAnalysisResult result = client.analyze(targets());

    assertThat(result.impacts()).allSatisfy(impact -> assertThat(impact.sources()).isEmpty());
    assertThat(result.sources())
        .containsExactly(
            new PortfolioAnalysisResult.Source("반도체 산업 동향", "https://example.com/semiconductor"));
  }

  @Test
  @DisplayName("출처가 없는 자산이 중립 또는 낮은 영향이 아니면 분석 실패로 처리한다")
  void analyzeRejectsDirectionalAssetWithoutSource() throws Exception {
    enqueueOutput(
        validOutput()
            .replaceFirst("\"direction\": \"NEUTRAL\"", "\"direction\": \"POSITIVE\"")
            .replaceFirst("\\[\"https://example\\.com/semiconductor\"\\]", "[]"));

    assertAnalysisFailed(() -> client.analyze(targets()));
  }

  @Test
  @DisplayName("OpenAI가 HTTP 오류를 반환하면 분석 실패로 처리한다")
  void analyzeHandlesHttpError() {
    String responseBody = "{\"error\":\"sensitive-response\"}";
    server.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(responseBody));

    assertAnalysisFailed(() -> client.analyze(targets()));

    ILoggingEvent failedLog = appender.list.getLast();
    assertThat(failedLog.getLevel()).isEqualTo(Level.WARN);
    assertThat(failedLog.getFormattedMessage())
        .isEqualTo(LogEvent.PORTFOLIO_ANALYSIS_FAILED.message())
        .doesNotContain(responseBody)
        .doesNotContain("sensitive-response");
    assertThat(keyValues(failedLog))
        .containsEntry("event", "portfolio.analysis.failed")
        .containsEntry("api", "OpenAI")
        .containsEntry("operation", "portfolio-analysis")
        .containsEntry("httpStatus", 500)
        .containsEntry("targetCount", 2)
        .containsKey("durationMs")
        .containsKey("errorType");
  }

  private List<PortfolioAnalysisTarget> targets() {
    return List.of(
        new PortfolioAnalysisTarget("삼성전자", new java.math.BigDecimal("60")),
        new PortfolioAnalysisTarget("SK하이닉스", new java.math.BigDecimal("40")));
  }

  private String validOutput() {
    return """
        {
          "overallImpact": "전체 영향이에요.",
          "impacts": [
            {
              "assetName": "삼성전자",
              "direction": "NEUTRAL",
              "impactLevel": "LOW",
              "issueSummary": "관련 이슈를 확인했어요.",
              "expectedReaction": "중립적인 반응이 예상돼요.",
              "outlook": "추가 흐름을 확인해야 해요.",
              "reason": "직접적인 영향이 제한적이에요.",
              "evidenceSentence": "관련 검색 근거예요.",
              "sourceUrls": ["https://example.com/semiconductor"]
            },
            {
              "assetName": "SK하이닉스",
              "direction": "NEUTRAL",
              "impactLevel": "LOW",
              "issueSummary": "관련 이슈를 확인했어요.",
              "expectedReaction": "중립적인 반응이 예상돼요.",
              "outlook": "추가 흐름을 확인해야 해요.",
              "reason": "직접적인 영향이 제한적이에요.",
              "evidenceSentence": "관련 검색 근거예요.",
              "sourceUrls": ["https://example.com/semiconductor"]
            }
          ]
        }
        """;
  }

  private void enqueueOutput(String outputText) throws JsonProcessingException {
    enqueueOutput(outputText, "https://example.com/semiconductor");
  }

  private void enqueueOutput(String outputText, String citationUrl) throws JsonProcessingException {
    String response =
        objectMapper.writeValueAsString(
            Map.of(
                "output",
                List.of(
                    Map.of(
                        "content",
                        List.of(
                            Map.of(
                                "type",
                                "output_text",
                                "text",
                                outputText,
                                "annotations",
                                List.of(
                                    Map.of(
                                        "type",
                                        "url_citation",
                                        "title",
                                        "반도체 산업 동향",
                                        "url",
                                        citationUrl))))))));
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(response));
  }

  private void enqueueOutputWithSearchSources(String outputText, String sourceUrl)
      throws JsonProcessingException {
    String response =
        objectMapper.writeValueAsString(
            Map.of(
                "output",
                List.of(
                    Map.of(
                        "type",
                        "web_search_call",
                        "action",
                        Map.of(
                            "type",
                            "search",
                            "sources",
                            List.of(
                                Map.of("type", "url", "title", "웹 검색 사용 출처", "url", sourceUrl)))),
                    Map.of(
                        "type",
                        "message",
                        "content",
                        List.of(
                            Map.of(
                                "type",
                                "output_text",
                                "text",
                                outputText,
                                "annotations",
                                List.of()))))));
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(response));
  }

  private void enqueueOutputWithoutAnnotations(String outputText) throws JsonProcessingException {
    String response =
        objectMapper.writeValueAsString(
            Map.of(
                "output",
                List.of(
                    Map.of(
                        "content",
                        List.of(
                            Map.of(
                                "type",
                                "output_text",
                                "text",
                                outputText,
                                "annotations",
                                List.of()))))));
    server.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(response));
  }

  private void assertAnalysisFailed(ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    return loggingEvent.getKeyValuePairs().stream()
        .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
  }
}
