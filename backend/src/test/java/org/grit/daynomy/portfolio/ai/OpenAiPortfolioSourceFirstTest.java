package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class OpenAiPortfolioSourceFirstTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void readsIndividualSearchResultBeforeAnalysisAndReusesOriginalWithoutAnotherFetch()
      throws Exception {
    String url = "https://example.com/results/2026-q2/";
    String first = "삼성전자의 2026년 2분기 영업이익이 10% 증가했어요.";
    String second = "삼성전자의 2026년 2분기 비용이 2% 증가했어요.";
    var documents = mock(PortfolioAnalysisDocumentClient.class);
    when(documents.fetchAll(anyList()))
        .thenReturn(
            Map.of(
                url,
                Optional.of(
                    new PortfolioAnalysisSourceDocument(
                        "삼성전자 실적",
                        first + " " + second,
                        LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(1)))));
    try (var server = new MockWebServer()) {
      server.start();
      server.enqueue(
          response(
              Map.of(
                  "assets",
                  List.of(
                      Map.of(
                          "assetName",
                          "삼성전자",
                          "aliases",
                          List.of("삼성전자", "Samsung Electronics"),
                          "urls",
                          List.of(url)))),
              List.of(
                  Map.of(
                      "type",
                      "web_search_call",
                      "status",
                      "completed",
                      "action",
                      Map.of("sources", List.of(Map.of("url", url)))))));
      server.enqueue(
          response(
              Map.of(
                  "impacts",
                  List.of(
                      Map.of(
                          "assetName",
                          "삼성전자",
                          "direction",
                          "POSITIVE",
                          "impactLevel",
                          "HIGH",
                          "issueSummary",
                          "삼성전자의 2026년 2분기 영업이익이 증가했어요.",
                          "expectedReaction",
                          "이익 증가에 유리하지만 비용 부담도 있어요.",
                          "outlook",
                          "다음 실적에서 비용 변화를 확인해야 해요.",
                          "reason",
                          "영업이익 증가를 확인했어요.",
                          "evidence",
                          List.of(
                              Map.of(
                                  "reference",
                                  1,
                                  "excerpt",
                                  "영업이익이 증가했어요.",
                                  "occurredOn",
                                  "2026-07-30"),
                              Map.of(
                                  "reference",
                                  2,
                                  "excerpt",
                                  "비용도 증가했어요.",
                                  "occurredOn",
                                  "2026-07-30"))))),
              List.of()));
      var client =
          new OpenAiPortfolioAnalysisClient(
              RestClient.builder(),
              server.url("/").toString(),
              "test-key",
              "gpt-5.4-mini",
              "gpt-5.4-mini",
              5,
              new PortfolioAnalysisEvidenceVerifier(documents));
      var result = client.analyze(List.of(new PortfolioAnalysisTarget("삼성전자")));
      assertThat(result.impacts())
          .singleElement()
          .satisfies(
              impact -> {
                assertThat(impact.direction())
                    .isEqualTo(org.grit.daynomy.market.domain.asset.ImpactDirection.POSITIVE);
                assertThat(impact.sources())
                    .extracting(PortfolioAnalysisResult.Source::factQuote)
                    .containsExactly(first, second);
              });
      assertThat(server.getRequestCount()).isEqualTo(2);
      var search = mapper.readTree(server.takeRequest().getBody().readUtf8());
      var analysis = mapper.readTree(server.takeRequest().getBody().readUtf8());
      assertThat(search.path("tool_choice").asText()).isEqualTo("required");
      assertThat(search.path("reasoning").path("effort").asText()).isEqualTo("none");
      assertThat(analysis.path("reasoning").path("effort").asText()).isEqualTo("low");
      assertThat(analysis.has("tools")).isFalse();
      assertThat(analysis.toString()).contains(first, second);
      assertThat(analysis.path("text").path("format").path("schema").toString())
          .doesNotContain("factQuote", "publishedOn");
      var schema = analysis.path("text").path("format").path("schema");
      var allowed = schema.path("$defs").path("evidence_reference");
      assertThat(allowed.path("type").asText()).isEqualTo("integer");
      assertThat(allowed.path("minimum").asInt()).isEqualTo(1);
      assertThat(allowed.path("maximum").asInt()).isEqualTo(2);
      assertThat(analysis.toString())
          .doesNotContain(PortfolioAnalysisEvidence.reference(url, first));
      var properties =
          schema
              .path("properties")
              .path("impacts")
              .path("items")
              .path("anyOf")
              .get(0)
              .path("properties");
      assertThat(properties.path("evidence").path("items").path("properties").has("excerpt"))
          .isFalse();
      verify(documents, times(1)).fetchAll(List.of(url));
    }
  }

  @Test
  void readsDiscoveredAlternativeInTheFirstSearchBeforeStartingAnalysis() throws Exception {
    String primary = "https://official.example.com/dynamic";
    String backup = "https://media.example.com/results";
    var documents = mock(PortfolioAnalysisDocumentClient.class);
    when(documents.fetchAll(anyList()))
        .thenAnswer(
            invocation -> {
              Map<String, Optional<PortfolioAnalysisSourceDocument>> result =
                  new java.util.LinkedHashMap<>();
              for (String url : invocation.<List<String>>getArgument(0))
                result.put(
                    url,
                    url.equals(backup)
                        ? Optional.of(
                            new PortfolioAnalysisSourceDocument("기아 실적", "매출이 증가했어요.", null))
                        : Optional.empty());
              return result;
            });
    try (var server = new MockWebServer()) {
      server.start();
      server.enqueue(
          response(
              Map.of(
                  "assets",
                  List.of(
                      Map.of(
                          "assetName", "기아", "urls", List.of(primary), "alternativeUrl", backup))),
              List.of(
                  Map.of(
                      "type",
                      "web_search_call",
                      "status",
                      "completed",
                      "action",
                      Map.of("sources", List.of(Map.of("url", primary), Map.of("url", backup)))))));
      server.enqueue(
          response(
              Map.of(
                  "impacts",
                  List.of(
                      Map.of(
                          "assetName",
                          "기아",
                          "direction",
                          "POSITIVE",
                          "impactLevel",
                          "MEDIUM",
                          "issueSummary",
                          "매출이 증가했어요.",
                          "reason",
                          "매출이 증가했어요.",
                          "expectedReaction",
                          "매출 증가가 사업에 유리해요.",
                          "outlook",
                          "매출 흐름을 확인해야 해요.",
                          "evidence",
                          List.of(
                              Map.of("reference", 1, "excerpt", "매출이 증가했어요.", "occurredOn", ""))))),
              List.of()));
      var client =
          new OpenAiPortfolioAnalysisClient(
              RestClient.builder(),
              server.url("/").toString(),
              "test-key",
              "test-model",
              "test-search-model",
              1,
              new PortfolioAnalysisEvidenceVerifier(documents));
      var result = client.analyze(List.of(new PortfolioAnalysisTarget("기아")));
      assertThat(result.impacts())
          .singleElement()
          .satisfies(
              impact -> {
                assertThat(impact.direction()).isNotNull();
                assertThat(impact.sources())
                    .singleElement()
                    .satisfies(source -> assertThat(source.url()).isEqualTo(backup));
              });
      assertThat(server.getRequestCount()).isEqualTo(2);
      verify(documents, times(1)).fetchAll(List.of(primary, backup));
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void retriesAnUnreadableOriginalWithFailureFeedbackAndKeepsTheOtherAssetEvidence(
      boolean repeatedHostFailure) throws Exception {
    String unreadable = "https://example.com/dynamic-article";
    var unreadableUrls =
        repeatedHostFailure ? List.of(unreadable, unreadable + "-second") : List.of(unreadable);
    String replacement = "https://reports.other-domain.com/report.pdf";
    String readable = "https://other.example.com/original";
    var documents = mock(PortfolioAnalysisDocumentClient.class);
    var original =
        new PortfolioAnalysisSourceDocument(
            "실적 발표", "매출이 증가했어요.", LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(1));
    when(documents.fetchAll(anyList()))
        .thenAnswer(
            invocation -> {
              Map<String, java.util.Optional<PortfolioAnalysisSourceDocument>> found =
                  new java.util.LinkedHashMap<>();
              for (String url : invocation.<List<String>>getArgument(0))
                found.put(
                    url,
                    unreadableUrls.contains(url)
                        ? java.util.Optional.empty()
                        : java.util.Optional.of(original));
              return found;
            });
    try (var server = new MockWebServer()) {
      server.start();
      server.enqueue(
          response(
              Map.of(
                  "assets",
                  List.of(
                      Map.of("assetName", "기아", "urls", unreadableUrls),
                      Map.of("assetName", "셀트리온", "urls", List.of(readable)))),
              List.of(
                  Map.of(
                      "type",
                      "web_search_call",
                      "status",
                      "completed",
                      "action",
                      Map.of(
                          "sources",
                          java.util.stream.Stream.concat(
                                  unreadableUrls.stream(), java.util.stream.Stream.of(readable))
                              .map(url -> Map.of("url", url))
                              .toList())))));
      server.enqueue(
          response(
              Map.of(
                  "assets",
                  List.of(Map.of("assetName", "기아", "urls", List.of(unreadable, replacement)))),
              List.of(
                  Map.of(
                      "type",
                      "web_search_call",
                      "status",
                      "completed",
                      "action",
                      Map.of(
                          "sources",
                          List.of(Map.of("url", unreadable), Map.of("url", replacement)))))));
      var impacts = new java.util.ArrayList<Map<String, Object>>();
      int reference = 0;
      for (String name : List.of("기아", "셀트리온"))
        impacts.add(
            Map.of(
                "assetName",
                name,
                "direction",
                "POSITIVE",
                "impactLevel",
                "MEDIUM",
                "issueSummary",
                "매출이 증가했어요.",
                "reason",
                "매출이 증가했어요.",
                "expectedReaction",
                "매출 증가가 사업에 유리해요.",
                "outlook",
                "매출 흐름을 확인해야 해요.",
                "evidence",
                List.of(
                    Map.of(
                        "reference",
                        ++reference,
                        "excerpt",
                        "매출이 증가했어요.",
                        "occurredOn",
                        "2026-08-07"))));
      server.enqueue(response(Map.of("impacts", impacts), List.of()));
      server.enqueue(new MockResponse().setResponseCode(500));
      var client =
          new OpenAiPortfolioAnalysisClient(
              RestClient.builder(),
              server.url("/").toString(),
              "test-key",
              "test-model",
              "test-search-model",
              2,
              new PortfolioAnalysisEvidenceVerifier(documents));
      var result =
          client.analyze(
              List.of(new PortfolioAnalysisTarget("기아"), new PortfolioAnalysisTarget("셀트리온")));
      assertThat(result.impacts())
          .hasSize(2)
          .allSatisfy(impact -> assertThat(impact.direction()).isNotNull());
      var firstSearch = mapper.readTree(server.takeRequest().getBody().readUtf8());
      assertThat(firstSearch.path("tools").get(0).has("filters")).isFalse();
      assertThat(firstSearch.path("reasoning").path("effort").asText()).isEqualTo("low");
      var retry = mapper.readTree(server.takeRequest().getBody().readUtf8());
      var filters = retry.path("tools").get(0).path("filters");
      if (repeatedHostFailure) {
        assertThat(filters.path("blocked_domains").get(0).asText()).isEqualTo("example.com");
      } else {
        assertThat(filters.isMissingNode()).isTrue();
      }
      assertThat(retry.path("input").toString()).contains("unavailableSources");
      if (repeatedHostFailure) {
        assertThat(retry.path("input").toString())
            .contains("-site:example.com")
            .doesNotContain(unreadable);
      } else {
        assertThat(retry.path("input").toString()).contains(unreadable).doesNotContain("-site:");
      }
      assertThat(retry.path("input").get(1).path("content").asText())
          .contains("기아")
          .doesNotContain("셀트리온");
      verify(documents, times(1)).fetchAll(List.of(replacement));
      assertThat(server.getRequestCount()).isEqualTo(3);
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void correctsAnUnverifiedPeriodWithoutSearchingAgainOrPreservingTheWrongDraft(
      boolean invalidProse) throws Exception {
    String url = "https://example.com/results/2026-q2";
    var documents = mock(PortfolioAnalysisDocumentClient.class);
    when(documents.fetchAll(anyList()))
        .thenReturn(
            Map.of(
                url,
                Optional.of(
                    new PortfolioAnalysisSourceDocument(
                        "2026년 2분기 실적",
                        "2026년 2분기 매출은 증가했지만 영업이익은 감소했어요.",
                        LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).minusDays(1)))));
    try (var server = new MockWebServer()) {
      server.start();
      server.enqueue(
          response(
              Map.of("assets", List.of(Map.of("assetName", "삼성전자", "urls", List.of(url)))),
              List.of(
                  Map.of(
                      "type",
                      "web_search_call",
                      "status",
                      "completed",
                      "action",
                      Map.of("sources", List.of(Map.of("url", url)))))));
      for (String quarter : List.of("1", "2")) {
        String claim = "2026년 " + quarter + "분기 매출은 증가했지만 영업이익은 감소했어요.";
        server.enqueue(
            response(
                Map.of(
                    "impacts",
                    List.of(
                        Map.of(
                            "assetName",
                            "삼성전자",
                            "direction",
                            quarter.equals("1") ? "POSITIVE" : "NEUTRAL",
                            "impactLevel",
                            "MEDIUM",
                            "issueSummary",
                            claim,
                            "expectedReaction",
                            invalidProse && quarter.equals("1") ? "English original.. 해요." : claim,
                            "outlook",
                            "이익 회복 여부를 확인해야 해요.",
                            "reason",
                            claim,
                            "evidence",
                            List.of(
                                Map.of(
                                    "reference",
                                    1,
                                    "excerpt",
                                    claim,
                                    "occurredOn",
                                    "2026-07-30"))))),
                List.of()));
      }
      server.enqueue(new MockResponse().setResponseCode(500));
      var client =
          new OpenAiPortfolioAnalysisClient(
              RestClient.builder(),
              server.url("/").toString(),
              "test-key",
              "test-model",
              "test-search-model",
              1,
              new PortfolioAnalysisEvidenceVerifier(documents));
      var result = client.analyze(List.of(new PortfolioAnalysisTarget("삼성전자")));
      assertThat(result.impacts())
          .singleElement()
          .satisfies(
              impact -> {
                assertThat(impact.direction())
                    .isEqualTo(org.grit.daynomy.market.domain.asset.ImpactDirection.NEUTRAL);
                assertThat(impact.issueSummary()).contains("2026년 2분기").doesNotContain("1분기");
              });
      assertThat(server.getRequestCount()).isEqualTo(3);
      verify(documents, times(1)).fetchAll(List.of(url));
    }
  }

  private MockResponse response(Map<String, Object> text, List<Map<String, Object>> toolOutput)
      throws Exception {
    var output = new java.util.ArrayList<>(toolOutput);
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
