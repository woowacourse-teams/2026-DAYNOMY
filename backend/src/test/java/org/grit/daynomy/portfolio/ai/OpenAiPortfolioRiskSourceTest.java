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
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 2, 30, 89})
  void initialAnalysisAcceptsPublicationsWithinNinetyDays(int daysAgo) {
    originals.put(
        MAIN, new PortfolioAnalysisSourceDocument("실적", FAVORABLE, today().minusDays(daysAgo)));
    assertThat(client.analyze(targets()).impacts().getFirst().direction())
        .isEqualTo(ImpactDirection.NEUTRAL);
  }

  @Test
  void oldPublicationsDoNotProduceANewJudgmentOrAnotherSummary() {
    originals.put(MAIN, new PortfolioAnalysisSourceDocument("실적", FAVORABLE, today().minusDays(2)));
    originals.put(RISK, new PortfolioAnalysisSourceDocument("위험", ADVERSE, today().minusDays(2)));
    var result = client.refresh(targets(), "[]");
    assertThat(result.impacts().getFirst().noNewNews()).isTrue();
    assertThat(result.impacts().getFirst().direction()).isNull();
    assertThat(result.impacts().getFirst().sources()).isEmpty();
    assertThat(result.impacts().getFirst().riskSearchStatus()).isEqualTo("NOT_FOUND");
    assertThat(analysisInput).isNull();
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(ints = {90, 91})
  void initialAnalysisRejectsExpiredPublications(int daysAgo) {
    originals.put(
        MAIN, new PortfolioAnalysisSourceDocument("실적", FAVORABLE, today().minusDays(daysAgo)));
    originals.put(
        RISK, new PortfolioAnalysisSourceDocument("위험", ADVERSE, today().minusDays(daysAgo)));
    var result = client.analyze(targets());
    assertThat(result.impacts().getFirst().direction()).isNull();
    assertThat(result.impacts().getFirst().noNewNews()).isFalse();
    assertThat(analysisInput).isNull();
  }

  @Test
  void initialAnalysisRejectsFuturePublications() {
    originals.put(MAIN, new PortfolioAnalysisSourceDocument("실적", FAVORABLE, today().plusDays(1)));
    assertThatThrownBy(() -> client.analyze(targets())).isInstanceOf(BusinessException.class);
    assertThat(analysisInput).isNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void refreshAcceptsTodayAndYesterday(int daysAgo) throws Exception {
    originals.put(
        MAIN, new PortfolioAnalysisSourceDocument("실적", FAVORABLE, today().minusDays(daysAgo)));
    assertThat(client.refresh(targets(), "[]").impacts().getFirst().direction())
        .isEqualTo(ImpactDirection.NEUTRAL);
    assertSearchWindow(positiveRequest, today().minusDays(1));
    assertSearchWindow(riskRequest, today().minusDays(1));
  }

  @Test
  void initialPositiveAndRiskSearchUseTheSameNinetyDayWindow() throws Exception {
    client.analyze(targets());
    assertSearchWindow(positiveRequest, today().minusDays(89));
    assertSearchWindow(riskRequest, today().minusDays(89));
  }

  private void assertSearchWindow(JsonNode request, LocalDate from) throws Exception {
    var content = mapper.readTree(request.path("input").get(1).path("content").asText());
    assertThat(content.path("recentFromDate").asText()).isEqualTo(from.toString());
    assertThat(content.path("asOfDate").asText()).isEqualTo(today().toString());
    assertThat(content.path("assets").get(0).path("recentQuery").asText())
        .contains("before:" + today().plusDays(1));
  }

  @Test
  void undatedAlternativeDoesNotRejectTheSameAssetsDatedCurrentArticle() {
    String pdf = "https://company.example/report.pdf";
    originals.put(pdf, new PortfolioAnalysisSourceDocument("보고서", FAVORABLE, null));
    mainUrls = List.of(MAIN, pdf);
    var result = client.analyze(targets());
    assertThat(result.impacts().getFirst().direction()).isEqualTo(ImpactDirection.NEUTRAL);
    assertThat(result.sources()).noneMatch(source -> source.url().equals(pdf));
  }

  @Test
  void previousAnalysisDoesNotAnchorFreshPositiveSearchToAnOldArticle() {
    client.refresh(
        targets(),
        "[{\"assetName\":\"셀트리온\",\"sources\":[{\"url\":\"https://old.example/2020-results\"}]}]");
    assertThat(positiveRequest.path("input").toString())
        .doesNotContain("old.example", "2020-results");
  }

  @Test
  void unknownPublicationDateCannotBecomeNoNewNews() {
    originals.put(MAIN, new PortfolioAnalysisSourceDocument("실적", FAVORABLE, null));
    assertThatThrownBy(() -> client.analyze(targets()))
        .isInstanceOfSatisfying(
            BusinessException.class,
            failure ->
                assertThat(failure.errorCode())
                    .isEqualTo(
                        org.grit.daynomy.portfolio.exception.PortfolioErrorCode
                            .PORTFOLIO_ANALYSIS_SOURCE_DATE_UNCONFIRMED));
    assertThat(analysisInput).isNull();
  }

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
  private boolean firstRiskTemporarilyUnavailable;
  private JsonNode riskRequest;
  private JsonNode positiveRequest;
  private JsonNode analysisInput;
  private String direction;
  private boolean unverifiedFirstRiskResponse;
  private List<String> invalidFirstRiskUrls;
  private List<String> repairedRiskUrls;
  private List<String> alternativeSearchUrls;
  private int riskRequestCount;

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
              if (name.equals("portfolio_sources")) {
                positiveRequest = body;
                return search(mainUrls, mainUrls, "completed");
              }
              if (name.equals("portfolio_risk_sources")) {
                riskRequest = body;
                riskRequestCount++;
                if (firstRiskTemporarilyUnavailable && riskRequestCount == 1)
                  return new MockResponse().setResponseCode(503);
                if (riskHttpStatus != 200)
                  return new MockResponse().setResponseCode(riskHttpStatus);
                if (unverifiedFirstRiskResponse && riskRequestCount == 1)
                  return search(
                      invalidFirstRiskUrls == null
                          ? List.of(RISK, RISK + "-1")
                          : invalidFirstRiskUrls,
                      List.of(RISK),
                      "completed");
                if (riskRequestCount == 3 && alternativeSearchUrls != null)
                  return search(alternativeSearchUrls, alternativeSearchUrls, "completed");
                if (riskRequestCount > 1 && repairedRiskUrls != null)
                  return search(repairedRiskUrls, repairedRiskUrls, "completed");
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

    assertThat(result.impacts().getFirst().riskSearchStatus()).isEqualTo("SOURCES_FOUND");
    assertThat(server.getRequestCount()).isEqualTo(3);
    assertThat(riskRequest.path("tool_choice").asText()).isEqualTo("required");
    var content = mapper.readTree(riskRequest.path("input").get(1).path("content").asText());
    assertThat(content.path("assets").get(0).path("recentQuery").asText())
        .contains("전망 하향", "계약 취소", "정정", "after:", "before:");
    assertThat(riskRequest.path("input").toString()).contains(MAIN, FAVORABLE);
    var positiveContent =
        mapper.readTree(positiveRequest.path("input").get(1).path("content").asText());
    assertThat(positiveContent.path("assets").get(0).path("perspective").asText())
        .isEqualTo("POSITIVE");
    assertThat(positiveContent.path("assets").get(0).path("newsQuery").asText())
        .contains("실적 개선", "site:n.news.naver.com");
    assertThat(content.path("assets").get(0).path("perspective").asText()).isEqualTo("NEGATIVE");
    assertThat(content.path("assets").get(0).path("newsQuery").asText())
        .contains("수익성 악화", "기대 미달");
    assertThat(riskRequest.path("tools").toString()).contains("blocked_domains", "company.example");
    var perspectives = analysisInput.path("assets").get(0).path("searchPerspectives");
    assertThat(perspectives.path("POSITIVE")).isEqualTo(mapper.valueToTree(List.of(MAIN)));
    assertThat(perspectives.path("NEGATIVE")).isEqualTo(mapper.valueToTree(List.of(RISK)));
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
    assertThat(result.impacts().getFirst().riskSearchStatus()).isEqualTo("NOT_FOUND");
    assertThat(server.getRequestCount()).isEqualTo(3);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://company.example/risk",
        "https://www.company.example/risk",
        "https://en.company.example/risk"
      })
  void thePositiveSourceDomainCannotBeReusedAsIndependentNegativeEvidence(String url) {
    riskUrls = List.of(url);
    originals.put(url, document(ADVERSE));
    assertRiskFailure();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void naverAllowsAnotherArticleButNeverReusesTheSameArticle(boolean sameArticle) {
    String positive = "https://n.news.naver.com/mnews/article/001/0000001";
    String negative = sameArticle ? positive : "https://n.news.naver.com/mnews/article/002/0000002";
    mainUrls = List.of(positive);
    originals.put(positive, document(FAVORABLE));
    riskUrls = List.of(negative);
    if (sameArticle) {
      assertRiskFailure();
    } else {
      originals.put(negative, document(ADVERSE));
      assertThat(client.analyze(targets()).impacts().getFirst().riskSearchStatus())
          .isEqualTo("SOURCES_FOUND");
      assertThat(analysisInput.toString()).contains(positive, negative, ADVERSE);
    }
  }

  @Test
  void riskCandidateDoesNotForceNegativeEither() {
    direction = "POSITIVE";
    assertThat(client.analyze(targets()).impacts().getFirst().direction())
        .isEqualTo(ImpactDirection.POSITIVE);
    assertThat(analysisInput.toString()).contains(ADVERSE, "SOURCES_FOUND");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/", "?utm_source=test", "#section"})
  void normalizedDuplicatesDoNotTriggerRepairOrDuplicateEvidence(String suffix) {
    String variant = RISK + suffix;
    originals.put(variant, document(ADVERSE));
    riskUrls = List.of(variant, RISK);

    var result = client.analyze(targets());

    assertThat(riskRequestCount).isEqualTo(1);
    assertThat(server.getRequestCount()).isEqualTo(3);
    assertThat(result.impacts().getFirst().sources())
        .extracting(PortfolioAnalysisResult.Source::factQuote)
        .containsExactly(FAVORABLE, ADVERSE);
  }

  @Test
  void retriesUnverifiedUrlVariantOnceAndUsesOnlyVerifiedOriginals() {
    unverifiedFirstRiskResponse = true;

    var result = client.analyze(targets());

    assertThat(riskRequestCount).isEqualTo(2);
    assertThat(server.getRequestCount()).isEqualTo(4);
    assertThat(riskRequest.path("input").toString()).contains("미검증 주소", "verifiedCandidateUrls");
    assertThat(riskRequest.has("tools")).isFalse();
    assertThat(
            riskRequest
                .path("text")
                .path("format")
                .path("schema")
                .path("properties")
                .path("assets")
                .path("items")
                .path("properties")
                .path("urls")
                .path("items")
                .path("enum"))
        .isEqualTo(mapper.valueToTree(List.of(RISK)));
    assertThat(result.impacts().getFirst().riskSearchStatus()).isEqualTo("SOURCES_FOUND");
    assertThat(analysisInput.toString()).contains(RISK).doesNotContain(RISK + "-1");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://media.example/news",
        "https://invented.example/article",
        "ftp://media.example/file"
      })
  void repairsInvalidSelectionFromKnownCandidatesOnly(String invalidUrl) {
    unverifiedFirstRiskResponse = true;
    invalidFirstRiskUrls = List.of(RISK, invalidUrl);

    assertThat(client.analyze(targets()).impacts().getFirst().riskSearchStatus())
        .isEqualTo("SOURCES_FOUND");
    assertThat(riskRequestCount).isEqualTo(2);
    assertThat(analysisInput.toString()).doesNotContain(invalidUrl);
  }

  @ParameterizedTest
  @ValueSource(strings = {"unverified", "index"})
  void failedRepairCannotBecomeNotFoundOrStartAnUnboundedRetry(String kind) {
    unverifiedFirstRiskResponse = true;
    repairedRiskUrls =
        switch (kind) {
          case "empty" -> List.of();
          case "index" -> List.of("https://media.example/news");
          default -> List.of("https://invented.example/article");
        };
    assertRiskFailure(3);
    assertThat(riskRequestCount).isEqualTo(2);
  }

  @Test
  void aNewCompletedSearchWithoutCandidatesAfterInvalidSelectionIsNotFound() {
    unverifiedFirstRiskResponse = true;
    repairedRiskUrls = List.of();
    direction = "POSITIVE";
    var result = client.analyze(targets());
    assertThat(riskRequestCount).isEqualTo(3);
    assertThat(result.impacts().getFirst().riskSearchStatus()).isEqualTo("NOT_FOUND");
    assertThat(result.impacts().getFirst().direction()).isEqualTo(ImpactDirection.POSITIVE);
  }

  @Test
  void emptySelectionRepairsBySearchingAnotherDomainWithoutDeclaringNotFound() {
    String alternative = "https://another.example/verified-order";
    originals.put(alternative, document(ADVERSE));
    unverifiedFirstRiskResponse = true;
    repairedRiskUrls = List.of();
    alternativeSearchUrls = List.of(alternative);
    assertThat(client.analyze(targets()).impacts().getFirst().riskSearchStatus())
        .isEqualTo("SOURCES_FOUND");
    assertThat(riskRequestCount).isEqualTo(3);
    assertThat(riskRequest.path("tools").toString())
        .contains("blocked_domains", "regulator.example");
    assertThat(analysisInput.toString()).contains(alternative, ADVERSE);
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
    assertRiskFailure(3);
  }

  @Test
  void recoversUnreadableRiskFromAnotherVerifiedOriginal() {
    String alternative = "https://media.example/verified-order";
    originals.remove(RISK);
    originals.put(alternative, document(ADVERSE));
    repairedRiskUrls = List.of(alternative);

    var result = client.analyze(targets());

    assertThat(riskRequestCount).isEqualTo(2);
    assertThat(result.impacts().getFirst().riskSearchStatus()).isEqualTo("SOURCES_FOUND");
    assertThat(analysisInput.toString()).contains(alternative, ADVERSE).doesNotContain(RISK);
    assertThat(riskRequest.path("input").toString()).contains("unreadableUrls", RISK);
  }

  @Test
  void failedOriginalFollowedByAnEmptySearchIsStillFailure() {
    originals.remove(RISK);
    repairedRiskUrls = List.of();
    assertRiskFailure(3);
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
    assertRiskFailure("http".equals(kind) ? 3 : 2);
  }

  @Test
  void refreshRiskSearchUsesCurrentOriginalsWithoutAnchoringOnPreviousJudgment() {
    client.refresh(
        targets(),
        "[{\"assetName\":\"삼성전자\",\"direction\":\"POSITIVE\",\"issueSummary\":\"기존 수주 발표\"}]");
    assertThat(riskRequest.path("input").toString())
        .contains(MAIN, FAVORABLE)
        .doesNotContain("기존 수주 발표");
  }

  @Test
  void transientRiskSearch503IsRetriedOnceBeforeSavingVerifiedResults() {
    firstRiskTemporarilyUnavailable = true;
    var result = client.analyze(targets());
    assertThat(result.impacts().getFirst().direction()).isEqualTo(ImpactDirection.NEUTRAL);
    assertThat(riskRequestCount).isEqualTo(2);
    assertThat(server.getRequestCount()).isEqualTo(4);
  }

  private void assertRiskFailure() {
    assertRiskFailure(2);
  }

  private void assertRiskFailure(int expectedRequests) {
    assertThatThrownBy(() -> client.analyze(targets())).isInstanceOf(BusinessException.class);
    assertThat(server.getRequestCount()).isEqualTo(expectedRequests);
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
