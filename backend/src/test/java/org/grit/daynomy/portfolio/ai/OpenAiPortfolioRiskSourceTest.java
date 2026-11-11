package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class OpenAiPortfolioRiskSourceTest {
  private static final String MAIN = "https://company.example/results";
  private static final String RISK = "https://regulator.example/order";
  private static final String FAVORABLE = "매출이 증가했어요.";
  private static final String ADVERSE = "영업이익이 감소했어요.";
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<String, PortfolioAnalysisSourceDocument> originals = new LinkedHashMap<>();
  private MockWebServer server;
  private OpenAiPortfolioAnalysisClient client;
  private List<String> mainUrls;
  private List<String> riskUrls;
  private List<String> discoveredRiskUrls;
  private String riskToolStatus;
  private int riskHttpStatus;
  private JsonNode riskRequest;
  private JsonNode analysisInput;
  private String direction;

  @BeforeEach
  void setUp() throws Exception {
    mainUrls = List.of(MAIN);
    riskUrls = List.of(RISK);
    discoveredRiskUrls = null;
    riskToolStatus = "completed";
    riskHttpStatus = 200;
    direction = "NEUTRAL";
    originals.put(MAIN, document(FAVORABLE));
    originals.put(RISK, document(ADVERSE));
    var documents = mock(PortfolioAnalysisDocumentClient.class);
    when(documents.fetchAll(anyList()))
        .thenAnswer(
            invocation -> {
              Map<String, Optional<PortfolioAnalysisSourceDocument>> found = new LinkedHashMap<>();
              for (String url : invocation.<List<String>>getArgument(0))
                found.put(url, Optional.ofNullable(originals.get(url)));
              return found;
            });
    server = new MockWebServer();
    server.setDispatcher(
        new Dispatcher() {
          @Override
          public MockResponse dispatch(RecordedRequest request) {
            try {
              var body = mapper.readTree(request.getBody().clone().readUtf8());
              String name = body.path("text").path("format").path("name").asText();
              if (name.equals("portfolio_sources")) return search(mainUrls, mainUrls, "completed");
              if (name.equals("portfolio_risk_sources")) {
                riskRequest = body;
                if (riskHttpStatus != 200)
                  return new MockResponse().setResponseCode(riskHttpStatus);
                return search(
                    riskUrls,
                    discoveredRiskUrls == null ? riskUrls : discoveredRiskUrls,
                    riskToolStatus);
              }
              analysisInput =
                  mapper.readTree(
                      body.path("input")
                          .get(body.path("input").size() - 1)
                          .path("content")
                          .asText());
              var evidence = new ArrayList<Map<String, Object>>();
              for (var source : analysisInput.path("sources"))
                source
                    .path("quotes")
                    .fieldNames()
                    .forEachRemaining(
                        reference -> {
                          Map<String, Object> fact = new LinkedHashMap<>();
                          fact.put("reference", Integer.parseInt(reference));
                          fact.put("occurredOn", null);
                          evidence.add(fact);
                        });
              return response(
                  Map.of(
                      "impacts",
                      List.of(
                          Map.of(
                              "assetName",
                              "삼성전자",
                              "direction",
                              direction,
                              "impactLevel",
                              "MEDIUM",
                              "issueSummary",
                              FAVORABLE,
                              "reason",
                              FAVORABLE,
                              "expectedReaction",
                              "확인한 사업 변화를 함께 고려해야 해요.",
                              "outlook",
                              "다음 실적에서 변화를 확인해야 해요.",
                              "evidence",
                              evidence))),
                  List.of());
            } catch (Exception failure) {
              return new MockResponse().setResponseCode(500);
            }
          }
        });
    server.start();
    client =
        new OpenAiPortfolioAnalysisClient(
            RestClient.builder(),
            server.url("/").toString(),
            "test-key",
            "test-model",
            "test-search-model",
            1,
            new PortfolioAnalysisEvidenceVerifier(documents));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.close();
  }

  @Test
  void independentlySearchesRisksAndProvidesBothOriginalsBeforeJudgment() throws Exception {
    var result = client.analyze(targets());

    assertThat(server.getRequestCount()).isEqualTo(3);
    assertThat(riskRequest.path("tool_choice").asText()).isEqualTo("required");
    var content = mapper.readTree(riskRequest.path("input").get(1).path("content").asText());
    assertThat(content.path("assets").get(0).path("recentQuery").asText())
        .contains("전망 하향", "계약 취소", "정정", "after:", "before:");
    assertThat(riskRequest.path("input").toString()).contains(MAIN, FAVORABLE);
    assertThat(analysisInput.path("sources")).hasSize(2);
    assertThat(analysisInput.toString()).contains(FAVORABLE, ADVERSE, "SOURCES_FOUND");
    assertThat(result.impacts().getFirst().sources())
        .extracting(PortfolioAnalysisResult.Source::factQuote)
        .containsExactly(FAVORABLE, ADVERSE);
  }

  @Test
  void completedSearchWithoutCandidatesIsNotFoundAndDoesNotForceNegative() {
    riskUrls = List.of();
    direction = "POSITIVE";
    var result = client.analyze(targets());

    assertThat(analysisInput.toString()).contains("NOT_FOUND").doesNotContain("SOURCES_FOUND");
    assertThat(result.impacts().getFirst().direction()).isEqualTo(ImpactDirection.POSITIVE);
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @Test
  void riskCandidateDoesNotForceNegativeEither() {
    direction = "POSITIVE";
    assertThat(client.analyze(targets()).impacts().getFirst().direction())
        .isEqualTo(ImpactDirection.POSITIVE);
    assertThat(analysisInput.toString()).contains(ADVERSE, "SOURCES_FOUND");
  }

  @Test
  void deduplicatesAnIdenticalReprintWithoutDroppingOtherFactsFromTheSameDay() {
    String reprint = "https://media.example/reprint";
    originals.put(reprint, document("  매출이   증가했어요.  "));
    riskUrls = List.of(reprint, RISK);
    client.analyze(targets());

    assertThat(analysisInput.path("sources")).hasSize(2);
    assertThat(analysisInput.toString()).contains(MAIN, RISK, ADVERSE).doesNotContain(reprint);
  }

  @Test
  void preservesIndependentRiskSourcesBeyondThePrimaryThreeDocumentBudget() {
    mainUrls = List.of(MAIN, "https://company.example/sales", "https://partner.example/contract");
    riskUrls = List.of(RISK, "https://court.example/decision", "https://media.example/forecast");
    for (int i = 1; i < 3; i++) {
      originals.put(mainUrls.get(i), document("사업 발표 " + i + " 내용을 확인했어요."));
      originals.put(riskUrls.get(i), document("위험 요인 " + i + " 내용을 확인했어요."));
    }
    client.analyze(targets());
    assertThat(analysisInput.path("sources")).hasSize(6);
    assertThat(analysisInput.toString()).contains("https://media.example/forecast", ADVERSE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"unavailable", "empty", "announcement", "future", "partial"})
  void unreadableRiskEvidenceFailsInsteadOfBecomingNotFound(String kind) {
    switch (kind) {
      case "unavailable" -> originals.remove(RISK);
      case "empty" -> originals.put(RISK, document(""));
      case "announcement" -> originals.put(RISK, document("결산실적공시예고"));
      case "future" ->
          originals.put(
              RISK, new PortfolioAnalysisSourceDocument("발표", ADVERSE, today().plusDays(1)));
      case "partial" -> riskUrls = List.of(RISK, "https://media.example/unreadable");
      default -> throw new AssertionError(kind);
    }
    assertRiskFailure();
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "incomplete", "unverified", "index"})
  void failedSearchOrInvalidCandidateCannotBecomeNotFound(String kind) {
    switch (kind) {
      case "http" -> riskHttpStatus = 503;
      case "incomplete" -> riskToolStatus = "failed";
      case "unverified" -> discoveredRiskUrls = List.of();
      case "index" -> riskUrls = List.of("https://media.example/news");
      default -> throw new AssertionError(kind);
    }
    assertRiskFailure();
  }

  @Test
  void refreshSearchReceivesPreviousJudgmentAndCurrentOriginals() {
    client.refresh(
        targets(),
        "[{\"assetName\":\"삼성전자\",\"direction\":\"POSITIVE\",\"issueSummary\":\"기존 수주 발표\"}]");
    assertThat(riskRequest.path("input").toString()).contains("기존 수주 발표", MAIN, FAVORABLE);
  }

  private void assertRiskFailure() {
    assertThatThrownBy(() -> client.analyze(targets())).isInstanceOf(BusinessException.class);
    assertThat(server.getRequestCount()).isEqualTo(2);
    assertThat(analysisInput).isNull();
  }

  private List<PortfolioAnalysisTarget> targets() {
    return List.of(new PortfolioAnalysisTarget("삼성전자"));
  }

  private PortfolioAnalysisSourceDocument document(String body) {
    return new PortfolioAnalysisSourceDocument("삼성전자 사업 발표", body, today().minusDays(1));
  }

  private LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Seoul"));
  }

  private MockResponse search(List<String> urls, List<String> discovered, String status)
      throws Exception {
    Map<String, Object> asset = new LinkedHashMap<>();
    asset.put("assetName", "삼성전자");
    asset.put("urls", urls.stream().limit(2).toList());
    asset.put("alternativeUrl", urls.size() > 2 ? urls.get(2) : null);
    return response(
        Map.of("assets", List.of(asset)),
        List.of(
            Map.of(
                "type",
                "web_search_call",
                "status",
                status,
                "action",
                Map.of("sources", discovered.stream().map(url -> Map.of("url", url)).toList()))));
  }

  private MockResponse response(Map<String, Object> text, List<Map<String, Object>> tools)
      throws Exception {
    var output = new ArrayList<>(tools);
    output.add(
        Map.of(
            "type",
            "message",
            "content",
            List.of(
                Map.of(
                    "type",
                    "output_text",
                    "text",
                    mapper.writeValueAsString(text),
                    "annotations",
                    List.of()))));
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(mapper.writeValueAsString(Map.of("output", output)));
  }
}
