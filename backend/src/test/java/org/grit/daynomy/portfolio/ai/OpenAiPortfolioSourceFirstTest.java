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
                        "삼성전자 실적", first + " " + second, LocalDate.of(2026, 7, 30)))));
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
              "test-model",
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
      var excerpt = properties.path("evidence").path("items").path("properties").path("excerpt");
      assertThat(excerpt.path("description").asText()).contains("한국어");
      assertThat(excerpt.has("pattern")).isFalse();
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
