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
  private PortfolioAnalysisEvidenceVerifier evidenceVerifier;
  private Level originalLevel;
  private MockResponse firstEnqueuedResponse;
  private MockResponse firstSearchResponse;
  private final Map<String, java.util.List<String>> fixtureUrls =
      new java.util.concurrent.ConcurrentHashMap<>();
  private final Map<String, org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument>
      fixtureDocuments = new java.util.concurrent.ConcurrentHashMap<>();

  @BeforeEach
  void setUp() throws IOException {
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
    ((Logger) LoggerFactory.getLogger(PortfolioAnalysisCall.class)).addAppender(appender);
    server = new MockWebServer();
    firstEnqueuedResponse = null;
    firstSearchResponse = null;
    fixtureUrls.clear();
    fixtureDocuments.clear();
    ((okhttp3.mockwebserver.QueueDispatcher) server.getDispatcher()).setFailFast(true);
    server.start();
    evidenceVerifier = acceptingVerifier();
    client =
        new OpenAiPortfolioAnalysisClient(
            RestClient.builder(),
            server.url("/").toString(),
            "test-api-key",
            "test-model",
            5,
            evidenceVerifier);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
    logger.detachAppender(appender);
    ((Logger) LoggerFactory.getLogger(PortfolioAnalysisCall.class)).detachAppender(appender);
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
              "expectedReaction": "장기적으로 긍정적일 수 있어요.",
              "outlook": "수요 증가 여부를 지켜봐야 해요.",
              "reason": "신규 수요가 예상돼요.",
              "evidenceSentence": "삼성전자의 신규 수요가 증가할 전망이에요.",
              "sourceUrls": ["https://example.com/semiconductor"]
            },
            {
              "assetName": "SK하이닉스",
              "direction": "NEUTRAL",
              "impactLevel": "HIGH",
              "issueSummary": "수요와 비용이 함께 증가했어요.",
              "expectedReaction": "방향은 불분명해요.",
              "outlook": "비용 부담의 지속 여부를 확인해야 해요.",
              "reason": "상반된 요인이 있어요.",
              "evidenceSentence": "SK하이닉스의 수요와 비용이 모두 증가했어요.",
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
    assertThat(result.impacts().getFirst().sources().getFirst().factQuote())
        .isEqualTo("Operating profit increased by 10%.");
    assertThat(result.overallImpact()).isEmpty();
    assertThat(result.impacts().getFirst().issueSummary()).contains("수요와 비용");
    assertThat(result.impacts().getFirst().outlook()).contains("비용 부담");
    assertThat(result.impacts().getFirst().evidenceSentence()).isEqualTo("확인된 사업 변화예요.");
    assertThat(result.impacts().getFirst().sources())
        .containsExactly(source("반도체 산업 동향", "https://example.com/semiconductor"));
    assertThat(result.sources())
        .containsExactly(source("반도체 산업 동향", "https://example.com/semiconductor"));

    ILoggingEvent completedLog = appender.list.getLast();
    assertThat(completedLog.getLevel()).isEqualTo(Level.INFO);
    assertThat(completedLog.getFormattedMessage())
        .isEqualTo(LogEvent.PORTFOLIO_ANALYSIS_COMPLETED.message());
    assertThat(keyValues(completedLog))
        .containsEntry("event", "portfolio.analysis.completed")
        .containsEntry("api", "OpenAI")
        .containsEntry("operation", "portfolio-analysis")
        .containsEntry("targetCount", 2)
        .containsEntry("analyzedCount", 2L)
        .containsKey("durationMs");

    RecordedRequest request = takeAnalysisRequest();
    assertThat(request.getPath()).isEqualTo("/responses");
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key");

    JsonNode requestBody = objectMapper.readTree(request.getBody().readUtf8());
    assertThat(requestBody.path("reasoning").path("effort").asText()).isEqualTo("low");
    assertThat(requestBody.path("text").path("verbosity").asText()).isEqualTo("low");
    assertThat(requestBody.has("max_output_tokens")).isFalse();
    assertThat(requestBody.has("tools")).isFalse();
    assertThat(requestBody.has("tool_choice")).isFalse();
    JsonNode impactsSchema =
        requestBody.path("text").path("format").path("schema").path("properties").path("impacts");
    assertThat(impactsSchema.path("minItems").asInt()).isEqualTo(2);
    assertThat(impactsSchema.path("maxItems").asInt()).isEqualTo(2);
    JsonNode userContent =
        objectMapper.readTree(requestBody.path("input").get(1).path("content").asText());
    assertThat(userContent.path("assets").get(0).path("assetName").asText()).isEqualTo("삼성전자");
    assertThat(userContent.toString()).doesNotContain("weight", "quantity", "assetId", "memberId");
    assertThat(userContent.path("assets"))
        .allSatisfy(asset -> assertThat(asset.size()).isEqualTo(1));
    assertThat(requestBody.path("input").get(0).path("content").asText())
        .contains("각 종목 자체", "서로 독립적으로", "사용자 보유 비중·수량");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {2, 5})
  void configuredBatchSizePreservesAllFourAssetsAndEvidence(int batchSize) throws Exception {
    client =
        new OpenAiPortfolioAnalysisClient(
            RestClient.builder(),
            server.url("/").toString(),
            "test-api-key",
            "test-model",
            batchSize,
            acceptingVerifier());
    var names = List.of("삼성전자", "SK하이닉스", "네이버", "카카오");
    var requests = new java.util.concurrent.ConcurrentLinkedQueue<List<String>>();
    var started = new java.util.concurrent.CountDownLatch(batchSize == 2 ? 2 : 1);
    server.setDispatcher(
        new okhttp3.mockwebserver.Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            try {
              var body = objectMapper.readTree(request.getBody().readUtf8());
              var requested = new java.util.ArrayList<String>();
              objectMapper
                  .readTree(body.path("input").get(1).path("content").asText())
                  .path("assets")
                  .forEach(asset -> requested.add(asset.path("assetName").asText()));
              if (body.path("text")
                  .path("format")
                  .path("name")
                  .asText()
                  .equals("portfolio_sources")) {
                fixtureDocuments.put(
                    "https://example.com/semiconductor",
                    new org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument(
                        "공식 발표", "Operating profit increased by 10%.", null));
                return sourceResponseFor(requested, "https://example.com/semiconductor");
              }
              assertThat(body.has("tools")).isFalse();
              requests.add(requested);
              started.countDown();
              assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
              var output = objectMapper.createObjectNode();
              var impacts = output.putArray("impacts");
              for (String name : requested) {
                var impact =
                    (com.fasterxml.jackson.databind.node.ObjectNode)
                        objectMapper.readTree(withEvidence(validOutput())).path("impacts").get(0);
                impacts.add(impact.put("assetName", name));
              }
              return new MockResponse()
                  .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                  .setBody(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "output",
                              List.of(
                                  Map.of("type", "web_search_call", "status", "completed"),
                                  Map.of(
                                      "type",
                                      "message",
                                      "content",
                                      List.of(
                                          Map.of(
                                              "type",
                                              "output_text",
                                              "text",
                                              output.toString(),
                                              "annotations",
                                              List.of(
                                                  Map.of(
                                                      "type",
                                                      "url_citation",
                                                      "title",
                                                      "공식 발표",
                                                      "url",
                                                      "https://example.com/semiconductor")))))))));
            } catch (Exception | AssertionError failure) {
              return new MockResponse().setResponseCode(500);
            }
          }
        });
    var result = client.analyze(names.stream().map(PortfolioAnalysisTarget::new).toList());
    assertThat(requests)
        .hasSize(batchSize == 2 ? 2 : 1)
        .allSatisfy(batch -> assertThat(batch.size()).isLessThanOrEqualTo(batchSize));
    assertThat(result.impacts())
        .extracting(PortfolioAnalysisResult.AssetImpactResult::assetName)
        .containsExactlyInAnyOrderElementsOf(names);
    assertThat(result.impacts())
        .allSatisfy(
            impact -> {
              assertThat(impact.direction()).isEqualTo(ImpactDirection.NEUTRAL);
              assertThat(impact.sources())
                  .singleElement()
                  .satisfies(
                      source ->
                          assertThat(source.factQuote())
                              .isEqualTo("Operating profit increased by 10%."));
            });
    assertThat(result.impacts())
        .extracting(PortfolioAnalysisResult.AssetImpactResult::sortOrder)
        .containsExactly(1, 2, 3, 4);
  }

  @Test
  @DisplayName("파인튜닝 모델 요청에는 지원하지 않는 배열 길이 제약을 포함하지 않는다")
  void analyzeOmitsArrayBoundsForFineTunedModel() throws Exception {
    client =
        new OpenAiPortfolioAnalysisClient(
            RestClient.builder(),
            server.url("/").toString(),
            "test-api-key",
            "ft:test-model",
            5,
            acceptingVerifier());
    enqueueOutput(validOutput());

    client.analyze(targets());

    RecordedRequest request = takeAnalysisRequest();
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

    RecordedRequest request = takeAnalysisRequest();
    JsonNode requestBody = objectMapper.readTree(request.getBody().readUtf8());
    String developerPrompt = requestBody.path("input").get(0).path("content").asText();

    assertThat(developerPrompt)
        .contains(
            "issueSummary",
            "expectedReaction",
            "outlook",
            "reason",
            "한국어 해요체",
            "서버가 제공한",
            "중요한 상충 근거");
  }

  @Test
  @DisplayName("웹 검색의 직접적인 근거를 기준으로 영향 방향을 판단하도록 요청한다")
  void analyzeRequestsDirectionBasedOnDirectEvidence() throws Exception {
    enqueueOutput(validOutput());

    client.analyze(targets());

    RecordedRequest request = takeAnalysisRequest();
    JsonNode requestBody = objectMapper.readTree(request.getBody().readUtf8());
    String developerPrompt = requestBody.path("input").get(0).path("content").asText();

    assertThat(developerPrompt)
        .contains(
            "유리한 직접 영향은 POSITIVE",
            "불리한 직접 영향은 NEGATIVE",
            "상쇄되거나 방향이 뚜렷하지 않을 때만 NEUTRAL",
            "근거 부족을 중립으로 바꾸지");
  }

  @Test
  @DisplayName("영향 수준을 HIGH, LOW, MEDIUM 우선순위에 따라 판단하도록 요청한다")
  void analyzeRequestsImpactLevelBasedOnExplicitCriteria() throws Exception {
    enqueueOutput(validOutput());

    client.analyze(targets());

    RecordedRequest request = takeAnalysisRequest();
    JsonNode requestBody = objectMapper.readTree(request.getBody().readUtf8());
    String developerPrompt = requestBody.path("input").get(0).path("content").asText();

    assertThat(developerPrompt).contains("크고 구체적이면 HIGH", "작거나 일시적이면 LOW", "그 외 직접 영향이면 MEDIUM");
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
              "expectedReaction": "긍정적이에요.",
              "outlook": "수요가 이어질 수 있어요.",
              "reason": "수요가 증가했어요.",
              "evidenceSentence": "현대차 수요가 증가했어요.",
              "sourceUrls": ["https://example.com/semiconductor"]
            }
          ]
        }
        """);

    assertAnalysisFailed(() -> client.analyze(targets()));
  }

  @Test
  @DisplayName("근거 문장이 비어 있으면 분석 실패로 처리한다")
  void analyzeRejectsBlankEvidenceExcerpt() throws Exception {
    String invalid = withEvidence(validOutput()).replace("확인된 사업 변화예요.", "   ");
    enqueueOutput(invalid);
    enqueueCorrection(invalid);
    assertAnalysisFailed(() -> client.analyze(targets()));
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void derivesEvidenceSentenceFromVerifiedExcerptInsteadOfLegacyAiText() throws Exception {
    enqueueOutput(validOutput().replace("관련 검색 근거예요.", "English original...요."));
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .allSatisfy(
            i -> assertThat(i.evidenceSentence()).isEqualTo(i.sources().getFirst().excerpt()));
    var request = objectMapper.readTree(takeAnalysisRequest().getBody().readUtf8());
    assertThat(request.path("text").toString()).doesNotContain("evidenceSentence");
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("AI 응답 형식이 올바르지 않으면 분석 실패로 처리한다")
  void analyzeRejectsMalformedOutput() throws Exception {
    enqueueOutput("{\"invalid\":[]}");

    assertAnalysisFailed(() -> client.analyze(targets()));
    assertThat(keyValues(appender.list.getLast()))
        .containsEntry("validationReason", "MISSING_SOURCE_ASSET")
        .containsEntry("model", "test-model");
  }

  @Test
  @DisplayName("웹 검색 인용 출처가 없으면 분석 실패로 처리한다")
  void analyzeRejectsMissingSources() throws Exception {
    enqueueOutputWithoutAnnotations("{\"overallImpact\":\"전체 영향이에요.\",\"impacts\":[]}");

    assertAnalysisFailed(() -> client.analyze(targets()));
  }

  @Test
  @DisplayName("서버가 수집하지 않은 근거 참조는 분석에 사용하지 않는다")
  void analyzeIgnoresUncitedAssetSource() throws Exception {
    enqueueOutput(
        validOutput()
            .replace(
                "[\"https://example.com/semiconductor\"]",
                "[\"https://example.com/semiconductor\", \"https://example.com/uncited\"]"));

    assertAnalysisFailed(() -> client.analyze(targets()));
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
            impact -> assertThat(impact.sources()).containsExactly(source("반도체 산업 동향", sourceUrl)));
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

    PortfolioAnalysisResult.Source expectedSource = source("웹 검색 사용 출처", searchSourceUrl);
    assertThat(result.impacts())
        .allSatisfy(impact -> assertThat(impact.sources()).containsExactly(expectedSource));
    assertThat(result.sources()).containsExactly(expectedSource);
  }

  @Test
  @DisplayName("검색 목록에 없어도 웹 도구가 직접 열어 본 페이지는 근거로 연결한다")
  void analyzeUsesOpenedPageAsEvidence() throws Exception {
    String url = "https://example.com/semiconductor";
    enqueueOutputWithSearchAction(validOutput(), Map.of("type", "open_page", "url", url));

    PortfolioAnalysisResult result = client.analyze(targets());

    assertThat(result.impacts())
        .allSatisfy(
            impact -> assertThat(impact.sources()).containsExactly(source("웹 검색 사용 출처", url)));
  }

  @Test
  @DisplayName("근거 부족 자산의 방향과 영향 수준은 중립이 아닌 null이다")
  void analyzeDistinguishesInsufficientEvidenceFromNeutral() throws Exception {
    enqueueOutput(
        validOutput()
            .replace("[\"https://example.com/semiconductor\"]", "[]")
            .replace("\"direction\": \"NEUTRAL\"", "\"direction\": null")
            .replace("\"impactLevel\": \"LOW\"", "\"impactLevel\": null"));
    enqueueCorrection(
        validOutput()
            .replace("[\"https://example.com/semiconductor\"]", "[]")
            .replace("\"direction\": \"NEUTRAL\"", "\"direction\": null")
            .replace("\"impactLevel\": \"LOW\"", "\"impactLevel\": null"));

    PortfolioAnalysisResult result = client.analyze(targets());

    assertThat(result.impacts()).allSatisfy(impact -> assertThat(impact.sources()).isEmpty());
    assertThat(result.impacts())
        .allSatisfy(
            impact -> {
              assertThat(impact.direction()).isNull();
              assertThat(impact.impactLevel()).isNull();
              assertThat(impact.expectedReaction()).isEmpty();
              assertThat(impact.outlook()).isEmpty();
            });
    assertThat(result.sources()).isEmpty();
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("교정 후에도 문체가 잘못된 종목만 제외하고 정상 종목은 유지한다")
  void retainsValidAssetAfterFailedCorrection() throws Exception {
    String broken =
        withEvidence(validOutput()).replaceFirst("확인된 사업 변화예요.", "English original sentence.. 해요.");
    enqueueOutput(broken);
    enqueueOutput(onlyAsset(broken, "삼성전자"));
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .filteredOn(i -> i.assetName().equals("삼성전자"))
        .singleElement()
        .satisfies(i -> assertThat(i.direction()).isNull());
    assertThat(result.impacts())
        .filteredOn(i -> i.assetName().equals("SK하이닉스"))
        .singleElement()
        .satisfies(i -> assertThat(i.direction()).isEqualTo(ImpactDirection.NEUTRAL));
    assertThat(result.overallImpact()).doesNotContain("삼성전자", "English");
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  @DisplayName("OpenAI가 HTTP 오류를 반환하면 분석 실패로 처리한다")
  void analyzeHandlesHttpError() {
    String responseBody = "{\"error\":\"sensitive-response\"}";
    enqueueResponse(
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
    return List.of(new PortfolioAnalysisTarget("삼성전자"), new PortfolioAnalysisTarget("SK하이닉스"));
  }

  @Test
  void deduplicatesIdenticalEvidenceWithoutDiscardingTheAsset() throws Exception {
    var output = objectMapper.readTree(withEvidence(validOutput()));
    for (var asset : output.path("impacts")) {
      var evidence = (com.fasterxml.jackson.databind.node.ArrayNode) asset.path("evidence");
      evidence.add(evidence.get(0).deepCopy());
    }
    enqueueOutput(objectMapper.writeValueAsString(output));
    assertThat(client.analyze(targets()).impacts())
        .allSatisfy(i -> assertThat(i.sources()).hasSize(1));
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void conflictingEvidenceForSameUrlRequiresCorrection() throws Exception {
    var output = objectMapper.readTree(withEvidence(validOutput()));
    for (var asset : output.path("impacts")) {
      var evidence = (com.fasterxml.jackson.databind.node.ArrayNode) asset.path("evidence");
      var other = (com.fasterxml.jackson.databind.node.ObjectNode) evidence.get(0).deepCopy();
      other.put("excerpt", "반대되는 사실이에요.");
      evidence.add(other);
    }
    enqueueOutput(objectMapper.writeValueAsString(output));
    enqueueCorrection(validOutput());
    assertThat(client.analyze(targets()).impacts())
        .allSatisfy(i -> assertThat(i.sources()).hasSize(1));
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void correctsUnconnectedEventDateInsteadOfPublishingIt() throws Exception {
    enqueueOutput(validOutput().replaceFirst("관련 이슈를 확인했어요.", "2026-07-30에 실적을 발표했어요."));
    enqueueOutput(onlyAsset(validOutput(), "삼성전자"));
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .allSatisfy(i -> assertThat(i.issueSummary()).doesNotContain("2026-07-30"));
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void preservesOfficialDocumentUrlsQuotesAndDifferentEventDates() throws Exception {
    JsonNode facts =
        objectMapper.readTree(
            """
        [
          {
            "assetName": "삼성전자",
            "url": "https://news.samsung.com/kr/삼성전자-2026년-2분기-실적-발표",
            "publishedOn": "2026-07-30",
            "occurredOn": "2026-07-30",
            "excerpt": "삼성전자는 2026-07-30에 2분기 연결 실적을 발표했어요.",
            "factQuote": "삼성전자는 연결 기준 매출 171.5조원, 영업이익 89.5조원의 2026년 2분기 실적을 발표했다."
          },
          {
            "assetName": "포스코인터내셔널",
            "url": "https://newsroom.posco.com/kr/포스코그룹-브라질과-희토류-공급망-강화한다/",
            "publishedOn": "2026-07-29",
            "occurredOn": "2026-07-28",
            "excerpt": "포스코인터내셔널은 2026-07-28에 브라질 기업과 희토류 공급망 구축 업무협약을 체결했어요.",
            "factQuote": "포스코인터내셔널, 브라질 희토류 기업 ‘메테오릭 리소시스’社와 원료조달 및 공급망 구축 MOU 체결"
          }
        ]
        """);
    var root = objectMapper.readTree(validOutput());
    var search = new java.util.ArrayList<Map<String, Object>>();
    var targets = new java.util.ArrayList<PortfolioAnalysisTarget>();
    for (int index = 0; index < facts.size(); index++) {
      var fact = (com.fasterxml.jackson.databind.node.ObjectNode) facts.get(index).deepCopy();
      String name = fact.remove("assetName").asText();
      String url = canonicalFixtureUrl(fact.path("url").asText());
      var impact = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("impacts").get(index);
      impact.put("assetName", name).put("issueSummary", fact.path("excerpt").asText());
      impact.putArray("sourceUrls").add(url);
      impact.putArray("evidence").add(fact);
      search.add(Map.of("type", "url", "title", name + " 공식 발표", "url", url));
      targets.add(new PortfolioAnalysisTarget(name));
    }
    enqueueOutputWithSearchAction(
        objectMapper.writeValueAsString(root), Map.of("type", "search", "sources", search));
    var result = client.analyze(targets);
    assertThat(result.impacts())
        .hasSize(2)
        .allSatisfy(
            i -> {
              var expected =
                  java.util.stream.StreamSupport.stream(facts.spliterator(), false)
                      .filter(f -> f.path("assetName").asText().equals(i.assetName()))
                      .findFirst()
                      .orElseThrow();
              assertThat(i.sources())
                  .singleElement()
                  .satisfies(
                      source -> {
                        assertThat(source.url())
                            .isEqualTo(canonicalFixtureUrl(expected.path("url").asText()));
                        assertThat(source.factQuote())
                            .isEqualTo(expected.path("factQuote").asText());
                        assertThat(source.publishedOn())
                            .isEqualTo(
                                java.time.LocalDate.parse(expected.path("publishedOn").asText()));
                        assertThat(source.occurredOn())
                            .isEqualTo(
                                java.time.LocalDate.parse(expected.path("occurredOn").asText()));
                      });
            });
  }

  @Test
  void preservesPublicationAndEventDatesSeparately() throws Exception {
    var output = objectMapper.readTree(withEvidence(validOutput()));
    for (JsonNode impact : output.path("impacts")) {
      ((com.fasterxml.jackson.databind.node.ObjectNode) impact.path("evidence").get(0))
          .put("publishedOn", "2026-09-29")
          .put("occurredOn", "2026-09-25");
    }
    enqueueOutput(objectMapper.writeValueAsString(output));
    var original = fixtureDocuments.get("https://example.com/semiconductor");
    fixtureDocuments.put(
        "https://example.com/semiconductor",
        new org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument(
            original.title(),
            original.body() + "\n2026-09-25",
            java.time.LocalDate.of(2026, 9, 29)));
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .allSatisfy(
            impact -> {
              assertThat(impact.sources().getFirst().publishedOn())
                  .isEqualTo(java.time.LocalDate.of(2026, 9, 29));
              assertThat(impact.sources().getFirst().occurredOn())
                  .isEqualTo(java.time.LocalDate.of(2026, 9, 25));
              assertThat(impact.sources().getFirst().excerpt()).isEqualTo("확인된 사업 변화예요.");
            });
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"2026-02-30", "2999-01-01", "어제", "2026-08-xx", "2023-07-00"})
  void rejectsImpossibleOrFutureEvidenceDatesWithoutLosingValidAssets(String invalid)
      throws Exception {
    var output = objectMapper.readTree(withEvidence(validOutput()));
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            output.path("impacts").get(0).path("evidence").get(0))
        .put("occurredOn", invalid);
    enqueueOutput(objectMapper.writeValueAsString(output));
    enqueueOutput(onlyAsset(objectMapper.writeValueAsString(output), "삼성전자"));
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .allSatisfy(
            impact -> {
              assertThat(impact.direction()).isNotNull();
              assertThat(impact.sources().getFirst().occurredOn()).isNull();
            });
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void repairsInvalidPublishedDateWithOneTargetedSearch() throws Exception {
    var output = objectMapper.readTree(withEvidence(validOutput()));
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            output.path("impacts").get(0).path("evidence").get(0))
        .put("publishedOn", "2023-07-00");
    enqueueOutput(objectMapper.writeValueAsString(output));
    enqueueOutput(onlyAsset(validOutput(), "삼성전자"));
    assertThat(client.analyze(targets()).impacts())
        .allSatisfy(i -> assertThat(i.direction()).isNotNull());
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void rejectsCitedUrlWithoutExtractedEvidence() throws Exception {
    var output = objectMapper.readTree(withEvidence(validOutput()));
    ((com.fasterxml.jackson.databind.node.ObjectNode) output.path("impacts").get(0))
        .putArray("evidence");
    enqueueOutput(objectMapper.writeValueAsString(output));
    enqueueOutput(onlyAsset(objectMapper.writeValueAsString(output), "삼성전자"));
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .filteredOn(i -> i.assetName().equals("삼성전자"))
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.direction()).isNull();
              assertThat(i.sources()).isEmpty();
            });
    assertThat(result.impacts())
        .filteredOn(i -> i.assetName().equals("SK하이닉스"))
        .singleElement()
        .satisfies(i -> assertThat(i.direction()).isNotNull());
    assertThat(result.overallImpact()).doesNotContain("삼성전자");
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void missingOriginalQuoteCannotBecomeAValidAnalysis() throws Exception {
    var output = objectMapper.readTree(withEvidence(validOutput()));
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            output.path("impacts").get(0).path("evidence").get(0))
        .remove("factQuote");
    enqueueOutput(output.toString());
    fixtureDocuments.replaceAll(
        (url, document) ->
            new org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument(
                document.title(), "", document.publishedOn()));
    assertAnalysisFailed(() -> client.analyze(targets()));
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void conflictingFactsForTheSameSourceRequireCorrection() throws Exception {
    var output = objectMapper.readTree(withEvidence(validOutput()));
    var evidence =
        (com.fasterxml.jackson.databind.node.ArrayNode)
            output.path("impacts").get(0).path("evidence");
    var conflicting = (com.fasterxml.jackson.databind.node.ObjectNode) evidence.get(0).deepCopy();
    evidence.add(conflicting.put("factQuote", "Operating profit declined by 10%."));
    enqueueOutput(output.toString());
    enqueueOutput(onlyAsset(validOutput(), "삼성전자"));
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .allSatisfy(
            impact ->
                assertThat(impact.sources())
                    .singleElement()
                    .satisfies(
                        source ->
                            assertThat(source.factQuote())
                                .isEqualTo("Operating profit increased by 10%.")));
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void completedSearchWithNoEvidenceIsNotSearchFailure() throws Exception {
    enqueueOutputWithoutAnnotations(
        validOutput()
            .replace("[\"https://example.com/semiconductor\"]", "[]")
            .replace("\"direction\": \"NEUTRAL\"", "\"direction\": null")
            .replace("\"impactLevel\": \"LOW\"", "\"impactLevel\": null"));
    enqueueCorrection(
        validOutput()
            .replace("[\"https://example.com/semiconductor\"]", "[]")
            .replace("\"direction\": \"NEUTRAL\"", "\"direction\": null")
            .replace("\"impactLevel\": \"LOW\"", "\"impactLevel\": null"));
    var result = client.analyze(targets());
    assertThat(result.sources()).isEmpty();
    assertThat(keyValues(appender.list.getLast())).containsEntry("analyzedCount", 0L);
    assertThat(result.impacts())
        .hasSize(2)
        .allSatisfy(impact -> assertThat(impact.direction()).isNull());
  }

  @Test
  void failedSearchCannotBeAcceptedAsInsufficientEvidence() throws Exception {
    String response =
        objectMapper.writeValueAsString(
            Map.of(
                "output",
                List.of(
                    Map.of("type", "web_search_call", "status", "failed"),
                    Map.of(
                        "content",
                        List.of(
                            Map.of(
                                "type",
                                "output_text",
                                "text",
                                withEvidence(validOutput()),
                                "annotations",
                                List.of(
                                    Map.of(
                                        "type",
                                        "url_citation",
                                        "title",
                                        "출처",
                                        "url",
                                        "https://example.com/semiconductor"))))))));
    enqueueResponse(
        new MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(response));
    assertAnalysisFailed(() -> client.analyze(targets()));
  }

  @Test
  void acceptsNegativeEvidenceWithoutForcingNeutralOrPositive() throws Exception {
    enqueueOutput(
        validOutput().replace("\"direction\": \"NEUTRAL\"", "\"direction\": \"NEGATIVE\""));
    assertThat(client.analyze(targets()).impacts())
        .allSatisfy(impact -> assertThat(impact.direction()).isEqualTo(ImpactDirection.NEGATIVE));
  }

  @Test
  void rejectsProseWhoseEndingCannotBeSafelyRepaired() throws Exception {
    enqueueOutput(validOutput().replace("중립적인 반응이 예상돼요.", "방향 불분명함. 추가 확인이 필요해요."));
    enqueueCorrection(validOutput().replace("중립적인 반응이 예상돼요.", "방향 불분명함. 추가 확인이 필요해요."));
    assertAnalysisFailed(() -> client.analyze(targets()));
  }

  @Test
  void completesMissingPeriodInGeneratedProse() throws Exception {
    enqueueOutput(validOutput().replace("중립적인 반응이 예상돼요.", "중립적인 반응이 예상돼요"));
    assertThat(client.analyze(targets()).impacts())
        .allSatisfy(impact -> assertThat(impact.expectedReaction()).isEqualTo("중립적인 반응이 예상돼요."));
  }

  @Test
  void refreshSendsPreviousEvidenceAsDataAndStillRequiresWebSearch() throws Exception {
    enqueueOutput(validOutput());
    String previous = "[{\"assetName\":\"삼성전자\",\"sources\":[]}]";
    client.refresh(targets(), previous);
    var request = objectMapper.readTree(takeAnalysisRequest().getBody().readUtf8());
    assertThat(request.has("tools")).isFalse();
    var targets = objectMapper.readTree(request.path("input").get(1).path("content").asText());
    assertThat(targets.toString()).doesNotContain("weight", "quantity", "memberId");
    assertThat(request.path("input").get(2).path("content").asText()).contains("재보도", "검증 대상 데이터");
    assertThat(request.path("input").get(3).path("role").asText()).isEqualTo("user");
    assertThat(request.path("input").get(3).path("content").asText()).isEqualTo(previous);
  }

  @Test
  void constrainsEvidenceDatesAndRequestsOriginalAnnouncements() throws Exception {
    enqueueOutput(validOutput());
    client.analyze(targets());
    var body = objectMapper.readTree(takeAnalysisRequest().getBody().readUtf8());
    var properties =
        body.path("text")
            .path("format")
            .path("schema")
            .path("properties")
            .path("impacts")
            .path("items")
            .path("anyOf")
            .get(0)
            .path("properties")
            .path("evidence")
            .path("items")
            .path("properties");
    assertThat(properties.path("occurredOn").path("pattern").asText())
        .isEqualTo("^[0-9]{4}-[0-9]{2}-[0-9]{2}$");
    assertThat(properties.has("publishedOn")).isFalse();
    assertThat(properties.has("reference")).isTrue();
    assertThat(body.path("input").get(0).path("content").asText())
        .contains("원문 문장만", "확인되지 않으면 null", "확정된 이익으로 바꾸지");
  }

  @Test
  void supportedAndInsufficientSchemasCannotMixEmptyExplanationWithNeutralJudgment()
      throws Exception {
    enqueueOutput(validOutput());
    client.analyze(targets());
    var body = objectMapper.readTree(takeAnalysisRequest().getBody().readUtf8());
    var variants =
        body.path("text")
            .path("format")
            .path("schema")
            .path("properties")
            .path("impacts")
            .path("items")
            .path("anyOf");
    assertThat(variants.size()).isEqualTo(2);
    assertThat(variants.get(0).path("properties").has("sourceUrls")).isFalse();
    assertThat(variants.get(1).path("properties").has("sourceUrls")).isFalse();
    assertThat(variants.get(0).path("properties").path("evidence").path("minItems").asInt())
        .isEqualTo(1);
    assertThat(variants.get(1).path("properties").path("evidence").path("maxItems").asInt())
        .isZero();
    assertThat(
            variants.get(0).path("properties").path("expectedReaction").path("minLength").asInt())
        .isEqualTo(1);
    assertThat(variants.get(0).path("properties").path("outlook").path("minLength").asInt())
        .isEqualTo(1);
    assertThat(variants.get(1).path("properties").path("direction").path("type").asText())
        .isEqualTo("null");
    assertThat(
            variants
                .get(1)
                .path("properties")
                .path("expectedReaction")
                .path("enum")
                .get(0)
                .asText())
        .isEmpty();
  }

  @Test
  void searchesOnlyCompanyWhoseOriginalCouldNotBePreparedBeforeAnalysis() throws Exception {
    String output = withEvidence(validOutput());
    String url = "https://example.com/semiconductor";
    server.enqueue(sourceResponseFor(List.of("삼성전자", "SK하이닉스"), url));
    server.enqueue(sourceResponseFor(List.of("SK하이닉스"), url));
    firstEnqueuedResponse = new MockResponse();
    enqueueOutput(output);
    var attempts = new java.util.concurrent.atomic.AtomicInteger();
    org.mockito.Mockito.doAnswer(
            invocation -> {
              Map<String, List<String>> selected =
                  new java.util.LinkedHashMap<>(invocation.getArgument(0));
              if (attempts.getAndIncrement() == 0) selected.put("SK하이닉스", List.of());
              return PortfolioAnalysisEvidence.from(selected, fixtureDocuments);
            })
        .when(evidenceVerifier)
        .prepare(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    assertThat(client.analyze(targets()).impacts())
        .allSatisfy(impact -> assertThat(impact.direction()).isNotNull());
    assertThat(server.getRequestCount()).isEqualTo(3);
    server.takeRequest();
    var fallback = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
    assertThat(fallback.path("input").get(1).path("content").asText())
        .contains("SK하이닉스")
        .doesNotContain("삼성전자");
  }

  @Test
  void searchesMissingAssetOnceAndKeepsAlreadyVerifiedJudgment() throws Exception {
    var root = objectMapper.readTree(validOutput());
    var missing = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("impacts").get(0);
    missing.putNull("direction");
    missing.putNull("impactLevel");
    missing.putArray("sourceUrls");
    enqueueOutput(objectMapper.writeValueAsString(root));
    enqueueOutput(onlyAsset(validOutput().replace("NEUTRAL", "POSITIVE"), "삼성전자"));
    org.mockito.Mockito.doReturn(
            PortfolioAnalysisEvidence.from(
                Map.of(
                    "삼성전자",
                    List.of("https://example.com/semiconductor"),
                    "SK하이닉스",
                    List.of("https://example.com/semiconductor")),
                fixtureDocuments))
        .when(evidenceVerifier)
        .prepare(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .filteredOn(i -> i.assetName().equals("삼성전자"))
        .singleElement()
        .satisfies(i -> assertThat(i.direction()).isEqualTo(ImpactDirection.POSITIVE));
    assertThat(result.impacts())
        .filteredOn(i -> i.assetName().equals("SK하이닉스"))
        .singleElement()
        .satisfies(i -> assertThat(i.direction()).isEqualTo(ImpactDirection.NEUTRAL));
    takeAnalysisRequest();
    var request = objectMapper.readTree(takeAnalysisRequest().getBody().readUtf8());
    var instructions =
        request.path("input").get(request.path("input").size() - 1).path("content").asText();
    assertThat(instructions).contains("제공된 원문만 다시 확인", "자료에 직접 근거가 없다면 판단을 만들지");
    var correction = objectMapper.readTree(request.path("input").get(2).path("content").asText());
    assertThat(correction.path("assets")).hasSize(1);
    assertThat(correction.path("assets").get(0).path("assetName").asText()).isEqualTo("삼성전자");
    missing.putArray("evidence");
    missing.remove("sourceUrls");
    assertThat(correction.path("assets").get(0).path("sourceUrls")).hasSize(1);
    assertThat(correction.path("sources").toString())
        .contains("Operating profit increased by 10%.");
    assertThat(correction.toString()).doesNotContain("SK하이닉스");
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void correctionKeepsOnlyTargetPreviousEvidence() throws Exception {
    var root = objectMapper.readTree(validOutput());
    var missing = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("impacts").get(0);
    missing.putNull("direction");
    missing.putNull("impactLevel");
    missing.putArray("sourceUrls");
    enqueueOutput(objectMapper.writeValueAsString(root));
    enqueueOutput(onlyAsset(validOutput(), "삼성전자"));
    String previous =
        """
        [{"assetName":"삼성전자","sources":[{"factQuote":"Keep this exact quote."}]},
         {"assetName":"SK하이닉스","sources":[{"factQuote":"Unrelated previous evidence."}]}]
        """;
    org.mockito.Mockito.doReturn(
            PortfolioAnalysisEvidence.from(
                Map.of(
                    "삼성전자",
                    List.of("https://example.com/semiconductor"),
                    "SK하이닉스",
                    List.of("https://example.com/semiconductor")),
                fixtureDocuments))
        .when(evidenceVerifier)
        .prepare(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    client.refresh(targets(), previous);
    var initial = objectMapper.readTree(takeAnalysisRequest().getBody().readUtf8());
    var correction = objectMapper.readTree(takeAnalysisRequest().getBody().readUtf8());
    assertThat(objectMapper.readTree(initial.path("input").get(3).path("content").asText()))
        .hasSize(2);
    var evidence = objectMapper.readTree(correction.path("input").get(3).path("content").asText());
    assertThat(evidence).hasSize(1);
    assertThat(evidence.get(0)).isEqualTo(objectMapper.readTree(previous).get(0));
    assertThat(correction.path("input").toString())
        .doesNotContain("SK하이닉스", "Unrelated previous evidence");
  }

  @Test
  void rejectsLanguageRootAndKeepsFactQuoteUntranslated() throws Exception {
    String root = "https://newsroom.posco.com/en/";
    enqueueOutputWithSearchSources(
        validOutput().replace("https://example.com/semiconductor", root), root);
    enqueueCorrection(validOutput());
    assertAnalysisFailed(() -> client.analyze(targets()));
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void indexSourceTriggersOneOriginalDocumentSearch() throws Exception {
    String index = "https://example.com/newsroom/";
    enqueueOutputWithSearchSources(
        validOutput().replace("https://example.com/semiconductor", index), index);
    enqueueCorrection(validOutput());

    assertAnalysisFailed(() -> client.analyze(targets()));
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void repeatedIndexSourceFailsWithoutUnboundedRetries() throws Exception {
    String index = "https://example.com/newsroom/";
    enqueueOutputWithSearchSources(
        validOutput().replace("https://example.com/semiconductor", index), index);
    enqueueCorrection(validOutput().replace("https://example.com/semiconductor", index));
    assertThatThrownBy(() -> client.analyze(targets())).isInstanceOf(BusinessException.class);
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void documentIdentifiedByQueryIsAccepted() throws Exception {
    String url = "https://example.com/news?id=123";
    enqueueOutputWithSearchSources(
        validOutput().replace("https://example.com/semiconductor", url), url);
    assertThat(client.analyze(targets()).impacts()).hasSize(2);
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void correctsEnglishEvidenceExcerptOnce() throws Exception {
    enqueueOutput(
        withEvidence(validOutput())
            .replace(
                "확인된 사업 변화예요.",
                "Samsung Electronics today announced its earnings guidance for the third quarter of 2026.. 해요."));
    enqueueCorrection(validOutput());
    assertThat(client.analyze(targets()).impacts())
        .allSatisfy(i -> assertThat(i.direction()).isNotNull());
    takeAnalysisRequest();
    String request = takeAnalysisRequest().getBody().readUtf8();
    assertThat(request).contains("문체·근거 참조 검증", "reference");
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void proseCorrectionNeedsNoSearchAndPreservesOriginalFacts() throws Exception {
    String original =
        withEvidence(validOutput()).replaceFirst("확인된 사업 변화예요.", "English original.. 해요.");
    enqueueOutput(original);
    String edited =
        onlyAsset(withEvidence(validOutput()), "삼성전자")
            .replace("NEUTRAL", "POSITIVE")
            .replace("Operating profit increased by 10%.", "Invented quote.")
            .replace("\"publishedOn\":null", "\"publishedOn\":\"2099-01-01\"");
    enqueueResponse(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(
                objectMapper.writeValueAsString(
                    Map.of(
                        "output",
                        List.of(
                            Map.of(
                                "type",
                                "message",
                                "content",
                                List.of(
                                    Map.of(
                                        "type",
                                        "output_text",
                                        "text",
                                        edited,
                                        "annotations",
                                        List.of()))))))));

    String unrelated = "Unselected background material must not be repeated for prose correction.";
    fixtureDocuments.replaceAll(
        (url, document) ->
            new org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument(
                document.title(), document.body() + " " + unrelated, document.publishedOn()));
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .allSatisfy(
            impact -> {
              assertThat(impact.direction()).isEqualTo(ImpactDirection.NEUTRAL);
              assertThat(impact.sources())
                  .singleElement()
                  .satisfies(
                      source -> {
                        assertThat(source.factQuote())
                            .isEqualTo("Operating profit increased by 10%.");
                        assertThat(source.publishedOn()).isNull();
                        assertThat(source.url()).isEqualTo("https://example.com/semiconductor");
                        assertThat(source.excerpt()).isEqualTo("확인된 사업 변화예요.");
                      });
            });
    assertThat(takeAnalysisRequest().getBody().readUtf8()).contains(unrelated);
    var request = objectMapper.readTree(takeAnalysisRequest().getBody().readUtf8());
    assertThat(request.has("tools")).isFalse();
    assertThat(request.has("tool_choice")).isFalse();
    assertThat(request.path("input").toString()).doesNotContain("반드시 웹 검색", "원문을 검색하세요");
    assertThat(request.path("input").toString())
        .contains("English original.. 해요.", "Operating profit increased by 10%.")
        .doesNotContain(unrelated);
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void proseFailureWithInvalidEvidenceStillRequiresSearch() throws Exception {
    var original = objectMapper.readTree(withEvidence(validOutput()));
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            original.path("impacts").get(0).path("evidence").get(0))
        .put("excerpt", "English original.. 해요.");
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            original.path("impacts").get(1).path("evidence").get(0))
        .put("occurredOn", "2099-01-01");
    String broken = original.toString();
    enqueueOutput(broken);
    enqueueCorrection(validOutput());
    assertThat(client.analyze(targets()).impacts())
        .allSatisfy(impact -> assertThat(impact.direction()).isNotNull());
    takeAnalysisRequest();
    var request = objectMapper.readTree(takeAnalysisRequest().getBody().readUtf8());
    assertThat(request.has("tools")).isFalse();
    assertThat(request.has("tool_choice")).isFalse();
  }

  @Test
  void proseCorrectionCannotIntroduceUnsupportedEventDate() throws Exception {
    enqueueOutput(withEvidence(validOutput()).replace("확인된 사업 변화예요.", "English original.. 해요."));
    enqueueCorrection(validOutput().replace("관련 이슈를 확인했어요.", "2099-01-01에 계약을 발표했어요."));
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .allSatisfy(
            impact -> {
              assertThat(impact.direction()).isNotNull();
              assertThat(impact.issueSummary()).doesNotContain("2099-01-01");
            });
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void invalidCorrectedProseDoesNotDiscardOtherValidAssets() throws Exception {
    enqueueOutput(withEvidence(validOutput()).replace("확인된 사업 변화예요.", "English original.. 해요."));
    var corrected = objectMapper.readTree(withEvidence(validOutput()));
    ((com.fasterxml.jackson.databind.node.ObjectNode)
            corrected.path("impacts").get(0).path("evidence").get(0))
        .put("excerpt", "");
    enqueueCorrection(corrected.toString());
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .filteredOn(impact -> impact.assetName().equals("삼성전자"))
        .singleElement()
        .satisfies(impact -> assertThat(impact.direction()).isNull());
    assertThat(result.impacts())
        .filteredOn(impact -> impact.assetName().equals("SK하이닉스"))
        .singleElement()
        .satisfies(impact -> assertThat(impact.direction()).isNotNull());
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void rejectsEvidenceUrlNotVerifiedBySearch() throws Exception {
    String broken =
        withEvidence(validOutput())
            .replace(
                PortfolioAnalysisEvidence.reference(
                    "https://example.com/semiconductor", "Operating profit increased by 10%."),
                "unknown-reference");
    enqueueOutput(broken);
    enqueueCorrection(validOutput());
    assertThat(client.analyze(targets()).impacts())
        .allSatisfy(i -> assertThat(i.sources()).hasSize(1));
    takeAnalysisRequest();
    assertThat(takeAnalysisRequest().getBody().readUtf8()).contains("문체·근거 참조 검증");
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void doesNotPublishAllInvalidCorrection() throws Exception {
    String broken = withEvidence(validOutput()).replace("확인된 사업 변화예요.", "English original.. 해요.");
    enqueueOutput(broken);
    enqueueCorrection(broken);
    assertAnalysisFailed(() -> client.analyze(targets()));
    assertThat(keyValues(appender.list.getLast()))
        .containsEntry("validationReason", "INVALID_ANALYSIS");
    assertThat(server.getRequestCount()).isEqualTo(3);
    var validations =
        appender.list.stream()
            .filter(
                e -> "portfolio.analysis.asset.validation.failed".equals(keyValues(e).get("event")))
            .map(
                event -> {
                  assertThat(event.getMDCPropertyMap()).containsEntry("batchNumber", "1");
                  assertThat(keyValues(event)).doesNotContainKey("batchNumber");
                  var fields =
                      new java.util.LinkedHashMap<String, Object>(event.getMDCPropertyMap());
                  fields.putAll(keyValues(event));
                  return fields;
                })
            .toList();
    assertThat(validations)
        .anySatisfy(
            fields ->
                assertThat(fields)
                    .containsEntry("phase", "initial")
                    .containsEntry("batchNumber", "1"));
    assertThat(validations)
        .anySatisfy(
            fields ->
                assertThat(fields)
                    .containsEntry("phase", "correction")
                    .containsEntry("batchNumber", "1"));
    assertThat(
            appender.list.stream()
                .filter(
                    e -> "portfolio.analysis.ai.call.completed".equals(keyValues(e).get("event")))
                .map(e -> keyValues(e).get("phase")))
        .containsExactly("source_search", "initial", "correction");
  }

  @Test
  void proseCorrectionPreservesJudgmentsForAllAssets() throws Exception {
    enqueueOutput(
        withEvidence(validOutput()).replaceFirst("확인된 사업 변화예요.", "English original.. 해요."));
    enqueueOutput(onlyAsset(validOutput().replace("NEUTRAL", "POSITIVE"), "삼성전자"));
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .filteredOn(i -> i.assetName().equals("삼성전자"))
        .singleElement()
        .satisfies(i -> assertThat(i.direction()).isEqualTo(ImpactDirection.NEUTRAL));
    assertThat(result.impacts())
        .filteredOn(i -> i.assetName().equals("SK하이닉스"))
        .singleElement()
        .satisfies(i -> assertThat(i.direction()).isEqualTo(ImpactDirection.NEUTRAL));
    assertThat(result.impacts())
        .extracting(PortfolioAnalysisResult.AssetImpactResult::sortOrder)
        .containsExactly(1, 2);
  }

  @Test
  void indexThenProseFailureDoesNotRetryCorrectionAgain() throws Exception {
    String index = "https://example.com/newsroom/";
    enqueueOutputWithSearchSources(
        validOutput().replace("https://example.com/semiconductor", index), index);
    enqueueCorrection(
        withEvidence(validOutput()).replace("확인된 사업 변화예요.", "English original.. 해요."));
    assertAnalysisFailed(() -> client.analyze(targets()));
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void directionalAssetWithoutSourceIsExcludedAfterOneCorrection() throws Exception {
    var output = objectMapper.readTree(validOutput());
    ((com.fasterxml.jackson.databind.node.ObjectNode) output.path("impacts").get(0))
        .putArray("sourceUrls");
    enqueueOutput(objectMapper.writeValueAsString(output));
    enqueueOutput(onlyAsset(objectMapper.writeValueAsString(output), "삼성전자"));
    org.mockito.Mockito.doReturn(
            PortfolioAnalysisEvidence.from(
                Map.of(
                    "삼성전자",
                    List.of("https://example.com/semiconductor"),
                    "SK하이닉스",
                    List.of("https://example.com/semiconductor")),
                fixtureDocuments))
        .when(evidenceVerifier)
        .prepare(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .filteredOn(i -> i.assetName().equals("삼성전자"))
        .singleElement()
        .satisfies(i -> assertThat(i.direction()).isNull());
    assertThat(result.impacts())
        .filteredOn(i -> i.assetName().equals("SK하이닉스"))
        .singleElement()
        .satisfies(i -> assertThat(i.direction()).isNotNull());
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  private PortfolioAnalysisResult.Source source(String title, String url) {
    return new PortfolioAnalysisResult.Source(
        title, url, null, null, "확인된 사업 변화예요.", "Operating profit increased by 10%.");
  }

  private String onlyAsset(String output, String name) throws JsonProcessingException {
    var root = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(output);
    var impacts = mapperAssets(root.path("impacts"), name);
    root.set("impacts", impacts);
    return objectMapper.writeValueAsString(root);
  }

  private com.fasterxml.jackson.databind.node.ArrayNode mapperAssets(JsonNode nodes, String name) {
    var array = objectMapper.createArrayNode();
    for (var node : nodes) if (name.equals(node.path("assetName").asText())) array.add(node);
    return array;
  }

  private String canonicalFixtureUrl(String value) {
    try {
      var uri = java.net.URI.create(value);
      var query =
          java.util.Arrays.stream(
                  uri.getRawQuery() == null ? new String[0] : uri.getRawQuery().split("&"))
              .filter(part -> !part.toLowerCase(java.util.Locale.ROOT).startsWith("utm_"))
              .collect(java.util.stream.Collectors.joining("&"));
      String path = uri.getRawPath() == null ? "" : uri.getRawPath().replaceAll("/$", "");
      return uri.getScheme()
          + "://"
          + uri.getRawAuthority()
          + path
          + (query.isEmpty() ? "" : "?" + query);
    } catch (Exception invalid) {
      return value;
    }
  }

  private String withEvidence(String json) throws JsonProcessingException {
    JsonNode root = objectMapper.readTree(json);
    for (JsonNode impact : root.path("impacts")) {
      var node = (com.fasterxml.jackson.databind.node.ObjectNode) impact;
      var facts =
          node.has("evidence") ? node.withArray("evidence") : objectMapper.createArrayNode();
      if (!node.has("evidence")) {
        for (var url : node.path("sourceUrls"))
          facts
              .addObject()
              .put("url", url.asText())
              .putNull("publishedOn")
              .putNull("occurredOn")
              .put("excerpt", "확인된 사업 변화예요.")
              .put("factQuote", "Operating profit increased by 10%.");
        node.set("evidence", facts);
      }
      for (var fact : facts) {
        if (fact.has("reference")) continue;
        String url = canonicalFixtureUrl(fact.path("url").asText());
        String quote = fact.path("factQuote").asText();
        fixtureUrls
            .computeIfAbsent(
                node.path("assetName").asText(), ignored -> new java.util.ArrayList<>())
            .add(url);
        java.time.LocalDate published = null;
        try {
          if (fact.path("publishedOn").isTextual())
            published = java.time.LocalDate.parse(fact.path("publishedOn").asText());
        } catch (java.time.DateTimeException ignored) {
        }
        var previous = fixtureDocuments.get(url);
        fixtureDocuments.put(
            url,
            new org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument(
                previous == null ? "반도체 산업 동향" : previous.title(),
                (previous == null ? quote : previous.body() + "\n" + quote)
                    + (fact.path("occurredOn").isTextual()
                        ? "\n" + fact.path("occurredOn").asText()
                        : ""),
                published));
        var item = (com.fasterxml.jackson.databind.node.ObjectNode) fact;
        item.put("reference", PortfolioAnalysisEvidence.reference(url, quote));
        item.remove(java.util.List.of("url", "publishedOn", "factQuote"));
      }
      node.remove("sourceUrls");
    }
    return objectMapper.writeValueAsString(root);
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

  private void enqueueCorrection(String outputText) {
    var initialResponse = firstEnqueuedResponse;
    firstEnqueuedResponse = null;
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    server.setDispatcher(
        new okhttp3.mockwebserver.Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
            int call = calls.getAndIncrement();
            if (call == 0 && firstSearchResponse != null) return firstSearchResponse;
            if (call == (firstSearchResponse == null ? 0 : 1)) return initialResponse;
            try {
              var body = objectMapper.readTree(request.getBody().clone().readUtf8());
              var requested =
                  objectMapper
                      .readTree(body.path("input").get(1).path("content").asText())
                      .path("assets");
              var names = new java.util.HashSet<String>();
              requested.forEach(asset -> names.add(asset.path("assetName").asText()));
              var output = objectMapper.readTree(withEvidence(outputText));
              var selected = objectMapper.createArrayNode();
              for (var impact : output.path("impacts")) {
                if (names.contains(impact.path("assetName").asText())) selected.add(impact);
              }
              ((com.fasterxml.jackson.databind.node.ObjectNode) output).set("impacts", selected);
              String response =
                  objectMapper.writeValueAsString(
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
                                          output.toString(),
                                          "annotations",
                                          List.of(
                                              Map.of(
                                                  "type",
                                                  "url_citation",
                                                  "title",
                                                  "공식 발표",
                                                  "url",
                                                  "https://example.com/semiconductor"))))))));
              return new MockResponse()
                  .setHeader("Content-Type", "application/json")
                  .setBody(response);
            } catch (Exception exception) {
              return new MockResponse().setResponseCode(500);
            }
          }
        });
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
                    Map.of("type", "web_search_call", "status", "completed"),
                    Map.of(
                        "content",
                        List.of(
                            Map.of(
                                "type",
                                "output_text",
                                "text",
                                withEvidence(outputText),
                                "annotations",
                                List.of(
                                    Map.of(
                                        "type",
                                        "url_citation",
                                        "title",
                                        "반도체 산업 동향",
                                        "url",
                                        citationUrl))))))));
    enqueueResponse(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(response));
  }

  private void enqueueOutputWithSearchSources(String outputText, String sourceUrl)
      throws JsonProcessingException {
    enqueueOutputWithSearchAction(
        outputText,
        Map.of(
            "type",
            "search",
            "sources",
            List.of(Map.of("type", "url", "title", "웹 검색 사용 출처", "url", sourceUrl))));
  }

  private void enqueueOutputWithSearchAction(String outputText, Map<String, Object> action)
      throws JsonProcessingException {
    String response =
        objectMapper.writeValueAsString(
            Map.of(
                "output",
                List.of(
                    Map.of("type", "web_search_call", "status", "completed", "action", action),
                    Map.of(
                        "type",
                        "message",
                        "content",
                        List.of(
                            Map.of(
                                "type",
                                "output_text",
                                "text",
                                withEvidence(outputText),
                                "annotations",
                                List.of()))))));
    enqueueResponse(
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
                    Map.of("type", "web_search_call", "status", "completed"),
                    Map.of(
                        "content",
                        List.of(
                            Map.of(
                                "type",
                                "output_text",
                                "text",
                                withEvidence(outputText),
                                "annotations",
                                List.of()))))));
    enqueueResponse(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody(response));
  }

  @Test
  void invalidResponseLogsOnlySafeMetadata() {
    enqueueResponse(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                "{\"id\":\"resp_test123\",\"output\":[],\"private\":\"secret-portfolio-content\"}"));
    assertAnalysisFailed(() -> client.analyze(targets()));
    var event = appender.list.getLast();
    assertThat(keyValues(event)).containsEntry("validationReason", "SEARCH_NOT_COMPLETED");
    assertThat(appender.list)
        .anySatisfy(
            item -> assertThat(keyValues(item)).containsEntry("responseId", "resp_test123"));
    assertThat(keyValues(event).toString() + event.getFormattedMessage())
        .doesNotContain("test-api-key", "secret-portfolio-content", "삼성전자");
    assertThat(event.getThrowableProxy()).isNull();
  }

  private void enqueueResponse(MockResponse response) {
    if (firstEnqueuedResponse == null) {
      firstEnqueuedResponse = response;
      try {
        var raw = objectMapper.readTree(response.getBody().clone().readUtf8());
        var pool = new java.util.LinkedHashMap<String, String>();
        JsonNode search = null;
        for (var item : raw.path("output")) {
          if (item.path("type").asText().equals("web_search_call")) {
            search = item;
            for (var source : item.path("action").path("sources"))
              pool.put(
                  canonicalFixtureUrl(source.path("url").asText()),
                  source.path("title").asText("웹 검색 사용 출처"));
            if (item.path("action").path("type").asText().equals("open_page"))
              pool.put(canonicalFixtureUrl(item.path("action").path("url").asText()), "웹 검색 사용 출처");
          }
          for (var content : item.path("content"))
            for (var annotation : content.path("annotations"))
              pool.put(
                  canonicalFixtureUrl(annotation.path("url").asText()),
                  annotation.path("title").asText());
        }
        if (search != null) {
          var assets = objectMapper.createArrayNode();
          var initialAssets = new java.util.LinkedHashMap<String, List<String>>();
          for (var item : raw.path("output"))
            for (var content : item.path("content")) {
              if (!content.path("type").asText().equals("output_text")) continue;
              try {
                for (var impact :
                    objectMapper.readTree(content.path("text").asText()).path("impacts"))
                  initialAssets.put(
                      impact.path("assetName").asText(),
                      fixtureUrls.getOrDefault(impact.path("assetName").asText(), List.of()));
              } catch (Exception ignored) {
              }
            }
          initialAssets.forEach(
              (name, urls) -> {
                var asset = assets.addObject().put("assetName", name);
                asset.putArray("aliases").add(name);
                var selected = asset.putArray("urls");
                urls.stream().distinct().filter(pool::containsKey).limit(3).forEach(selected::add);
              });
          pool.forEach(
              (url, title) -> {
                var doc = fixtureDocuments.get(url);
                if (doc != null)
                  fixtureDocuments.put(
                      url,
                      new org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument(
                          title, doc.body(), doc.publishedOn()));
              });
          var root = objectMapper.createObjectNode();
          root.set("assets", assets);
          firstSearchResponse =
              new MockResponse()
                  .setHeader("Content-Type", "application/json")
                  .setBody(
                      objectMapper.writeValueAsString(
                          Map.of(
                              "output",
                              List.of(
                                  Map.of(
                                      "type",
                                      "web_search_call",
                                      "status",
                                      search.path("status").asText(),
                                      "action",
                                      Map.of(
                                          "sources",
                                          pool.entrySet().stream()
                                              .map(
                                                  entry ->
                                                      Map.of(
                                                          "url",
                                                          entry.getKey(),
                                                          "title",
                                                          entry.getValue()))
                                              .toList())),
                                  Map.of(
                                      "content",
                                      List.of(
                                          Map.of(
                                              "type",
                                              "output_text",
                                              "text",
                                              root.toString(),
                                              "annotations",
                                              List.of())))))));
          server.enqueue(firstSearchResponse);
        }
      } catch (Exception ignored) {
      }
    }
    server.enqueue(response);
  }

  private MockResponse sourceResponseFor(List<String> names, String url) throws Exception {
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            objectMapper.writeValueAsString(
                Map.of(
                    "output",
                    List.of(
                        Map.of(
                            "type",
                            "web_search_call",
                            "status",
                            "completed",
                            "action",
                            Map.of("sources", List.of(Map.of("url", url, "title", "공식 발표")))),
                        Map.of(
                            "content",
                            List.of(
                                Map.of(
                                    "type",
                                    "output_text",
                                    "text",
                                    objectMapper.writeValueAsString(
                                        Map.of(
                                            "assets",
                                            names.stream()
                                                .map(
                                                    name ->
                                                        Map.of(
                                                            "assetName",
                                                            name,
                                                            "aliases",
                                                            List.of(name),
                                                            "urls",
                                                            List.of(url)))
                                                .toList())),
                                    "annotations",
                                    List.of())))))));
  }

  private RecordedRequest takeAnalysisRequest() throws InterruptedException {
    RecordedRequest request = server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS);
    try {
      var body = objectMapper.readTree(request.getBody().clone().readUtf8());
      if (body.path("text").path("format").path("name").asText().equals("portfolio_sources")) {
        var analysis = server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS);
        return analysis == null ? request : analysis;
      }
    } catch (Exception ignored) {
    }
    return request;
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

  @Test
  void returnsOnlyResultAcceptedByServerSourceVerification() throws Exception {
    enqueueOutput(validOutput());
    var withheld = new PortfolioAnalysisResult("", List.of(), List.of());
    org.mockito.Mockito.when(
            evidenceVerifier.verifyPrepared(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenReturn(withheld);
    assertThat(client.analyze(targets())).isSameAs(withheld);
    org.mockito.Mockito.verify(evidenceVerifier)
        .verifyPrepared(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void measuredFormalEndingDoesNotTriggerAnotherAiRequest() throws Exception {
    enqueueOutput(validOutput().replace("중립적인 반응이 예상돼요.", "방향을 단정하기 어렵습니다."));
    var result = client.analyze(targets());
    assertThat(result.impacts()).allMatch(impact -> impact.direction() != null);
    assertThat(result.impacts())
        .anyMatch(impact -> impact.expectedReaction().equals("방향을 단정하기 어려워요."));
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void failedCorrectionPreservesAlreadyValidOtherAsset() throws Exception {
    enqueueOutput(
        withEvidence(validOutput()).replaceFirst("확인된 사업 변화예요.", "English original.. 해요."));
    enqueueResponse(new MockResponse().setResponseCode(503));
    var result = client.analyze(targets());
    assertThat(result.impacts())
        .filteredOn(impact -> impact.assetName().equals("SK하이닉스"))
        .singleElement()
        .satisfies(impact -> assertThat(impact.direction()).isEqualTo(ImpactDirection.NEUTRAL));
    assertThat(result.impacts())
        .filteredOn(impact -> impact.assetName().equals("삼성전자"))
        .singleElement()
        .satisfies(impact -> assertThat(impact.direction()).isNull());
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  private PortfolioAnalysisEvidenceVerifier acceptingVerifier() {
    var verifier = org.mockito.Mockito.mock(PortfolioAnalysisEvidenceVerifier.class);
    org.mockito.Mockito.when(
            verifier.verifyPrepared(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    org.mockito.Mockito.when(
            verifier.prepare(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation ->
                PortfolioAnalysisEvidence.from(invocation.getArgument(0), fixtureDocuments));
    return verifier;
  }
}
