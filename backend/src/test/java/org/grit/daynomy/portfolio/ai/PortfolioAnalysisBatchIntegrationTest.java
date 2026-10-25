package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.v1.ai.OpenAiPortfolioAnalysisClientV1;
import org.grit.daynomy.portfolio.v1.ai.PortfolioAnalysisTargetV1;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class PortfolioAnalysisBatchIntegrationTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @ParameterizedTest
  @CsvSource({"true,false", "false,false", "true,true", "false,true"})
  void sendsAtMostFiveAssetsAndMatchingPreviousEvidenceForBothVersions(boolean v1, boolean refresh)
      throws Exception {
    var names = IntStream.range(0, 50).mapToObj(i -> "종목" + i).toList();
    var batches = new ConcurrentLinkedQueue<List<String>>();
    try (var server = new MockWebServer()) {
      server.start();
      server.setDispatcher(
          new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
              try {
                var input = mapper.readTree(request.getBody().clone().readUtf8()).path("input");
                var assets = mapper.readTree(input.get(1).path("content").asText()).path("assets");
                var requested = new java.util.ArrayList<String>();
                assets.forEach(asset -> requested.add(asset.path("assetName").asText()));
                assertThat(requested).hasSize(5);
                var body = mapper.readTree(request.getBody().clone().readUtf8());
                if (!v1
                    && body.path("text")
                        .path("format")
                        .path("name")
                        .asText()
                        .equals("portfolio_sources")) {
                  if (refresh)
                    assertThat(mapper.readTree(input.get(2).path("content").asText())).hasSize(5);
                  return sourceResponse(requested, List.of("https://example.com/announcement"));
                }
                batches.add(requested);
                if (v1)
                  assets.forEach(
                      asset ->
                          assertThat(asset.path("weight").decimalValue())
                              .isEqualByComparingTo("2"));
                if (refresh) {
                  var previous = mapper.readTree(input.get(3).path("content").asText());
                  assertThat(previous.size()).isEqualTo(5);
                  previous.forEach(
                      asset -> assertThat(requested).contains(asset.path("assetName").asText()));
                }
                var output = mapper.createObjectNode();
                if (v1) {
                  var impacts = output.putObject("impacts");
                  requested.forEach(name -> impacts.set(name, validImpact()));
                } else {
                  var impacts = output.putArray("impacts");
                  requested.forEach(
                      name -> {
                        var impact = validImpact().put("assetName", name);
                        impact.remove("sourceUrls");
                        useServerReference(impact);
                        impacts.add(impact);
                      });
                }
                return new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
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
                                                output.toString(),
                                                "annotations",
                                                List.of(
                                                    Map.of(
                                                        "type",
                                                        "url_citation",
                                                        "title",
                                                        "공식 발표",
                                                        "url",
                                                        "https://example.com/announcement")))))))));
              } catch (Throwable failure) {
                return new MockResponse().setResponseCode(500);
              }
            }
          });
      var previous = mapper.createArrayNode();
      names.forEach(name -> previous.addObject().put("assetName", name));
      PortfolioAnalysisResult result;
      if (v1) {
        var client =
            new OpenAiPortfolioAnalysisClientV1(
                RestClient.builder(), server.url("/").toString(), "test-key", "test-model");
        var targets =
            names.stream()
                .map(name -> new PortfolioAnalysisTargetV1(name, new BigDecimal("2")))
                .toList();
        result = refresh ? client.refresh(targets, previous.toString()) : client.analyze(targets);
      } else {
        var client =
            new OpenAiPortfolioAnalysisClient(
                RestClient.builder(),
                server.url("/").toString(),
                "test-key",
                "test-model",
                5,
                acceptingVerifier());
        var targets = names.stream().map(PortfolioAnalysisTarget::new).toList();
        result = refresh ? client.refresh(targets, previous.toString()) : client.analyze(targets);
      }
      assertThat(server.getRequestCount()).isEqualTo(v1 ? 10 : 20);
      assertThat(result.impacts())
          .extracting(PortfolioAnalysisResult.AssetImpactResult::assetName)
          .containsExactlyInAnyOrderElementsOf(names);
      assertThat(batches.stream().flatMap(List::stream).toList())
          .containsExactlyInAnyOrderElementsOf(names);
      assertThat(result.impacts())
          .allSatisfy(
              impact -> {
                assertThat(impact.direction()).isNotNull();
                assertThat(impact.sources()).isNotEmpty();
              });
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void rejectsDuplicateAssetsAcrossBatchBoundariesBeforeCallingOpenAi(boolean v1) throws Exception {
    var names = List.of("삼성전자", "현대차", "NAVER", "LG전자", "SK하이닉스", "삼성전자");
    try (var server = new MockWebServer()) {
      server.start();
      ((okhttp3.mockwebserver.QueueDispatcher) server.getDispatcher()).setFailFast(true);
      if (v1) {
        var client =
            new OpenAiPortfolioAnalysisClientV1(
                RestClient.builder(), server.url("/").toString(), "test-key", "test-model");
        var targets =
            names.stream()
                .map(name -> new PortfolioAnalysisTargetV1(name, BigDecimal.TEN))
                .toList();
        assertThatThrownBy(() -> client.analyze(targets)).isInstanceOf(BusinessException.class);
      } else {
        var client =
            new OpenAiPortfolioAnalysisClient(
                RestClient.builder(),
                server.url("/").toString(),
                "test-key",
                "test-model",
                5,
                acceptingVerifier());
        var targets = names.stream().map(PortfolioAnalysisTarget::new).toList();
        assertThatThrownBy(() -> client.analyze(targets)).isInstanceOf(BusinessException.class);
      }
      assertThat(server.getRequestCount()).isZero();
    }
  }

  @ParameterizedTest
  @CsvSource({"true,true", "false,true", "true,false", "false,false"})
  void correctionAcceptsOnlySourcesVerifiedInEitherSearch(boolean v1, boolean originallyVerified)
      throws Exception {
    try (var server = new MockWebServer()) {
      server.start();
      ((okhttp3.mockwebserver.QueueDispatcher) server.getDispatcher()).setFailFast(true);
      var initial = validImpact();
      initial.putArray("sourceUrls").add("https://example.com/category/ir");
      if (!v1)
        ((ObjectNode) initial.path("evidence").get(0))
            .put("url", "https://example.com/category/ir");
      var corrected = validImpact();
      // 첫 검색에서 확인한 개별 기사는 보정 검색 결과에 다시 나오지 않을 수 있다.
      if (!v1)
        server.enqueue(
            sourceResponse(
                List.of("SK하이닉스"),
                originallyVerified
                    ? List.of("https://example.com/announcement", "https://example.com/category/ir")
                    : List.of("https://example.com/category/ir")));
      server.enqueue(
          searchResponse(
              v1,
              initial,
              originallyVerified
                  ? List.of("https://example.com/announcement", "https://example.com/category/ir")
                  : List.of("https://example.com/category/ir")));
      server.enqueue(searchResponse(v1, corrected, List.of("https://example.com/other-article")));
      java.util.function.Supplier<PortfolioAnalysisResult> analyze;
      if (v1) {
        var client =
            new OpenAiPortfolioAnalysisClientV1(
                RestClient.builder(), server.url("/").toString(), "test-key", "test-model");
        analyze =
            () -> client.analyze(List.of(new PortfolioAnalysisTargetV1("SK하이닉스", BigDecimal.TEN)));
      } else {
        var client =
            new OpenAiPortfolioAnalysisClient(
                RestClient.builder(),
                server.url("/").toString(),
                "test-key",
                "test-model",
                5,
                acceptingVerifier());
        analyze = () -> client.analyze(List.of(new PortfolioAnalysisTarget("SK하이닉스")));
      }
      if (originallyVerified) {
        var result = analyze.get();
        assertThat(result.impacts())
            .singleElement()
            .satisfies(
                impact -> {
                  assertThat(impact.direction()).isNotNull();
                  assertThat(impact.sources())
                      .singleElement()
                      .extracting(PortfolioAnalysisResult.Source::url)
                      .isEqualTo("https://example.com/announcement");
                });
      } else {
        assertThatThrownBy(analyze::get).isInstanceOf(BusinessException.class);
      }
      assertThat(server.getRequestCount()).isEqualTo(v1 ? 2 : 3);
    }
  }

  private MockResponse searchResponse(boolean v1, ObjectNode impact, List<String> urls)
      throws Exception {
    var text = mapper.createObjectNode();
    if (v1) text.putObject("impacts").set("SK하이닉스", impact);
    else {
      var asset = impact.deepCopy().put("assetName", "SK하이닉스");
      asset.remove("sourceUrls");
      useServerReference(asset);
      text.putArray("impacts").add(asset);
    }
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            mapper.writeValueAsString(
                Map.of(
                    "output",
                    List.of(
                        Map.of(
                            "type",
                            "web_search_call",
                            "status",
                            "completed",
                            "action",
                            Map.of(
                                "sources",
                                urls.stream()
                                    .map(url -> Map.of("type", "url", "url", url))
                                    .toList())),
                        Map.of(
                            "content",
                            List.of(
                                Map.of(
                                    "type",
                                    "output_text",
                                    "text",
                                    text.toString(),
                                    "annotations",
                                    List.of())))))));
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

  private void useServerReference(ObjectNode impact) {
    for (var evidence : impact.path("evidence")) {
      var node = (ObjectNode) evidence;
      node.put(
          "reference",
          PortfolioAnalysisEvidence.reference(
              node.path("url").asText(), node.path("factQuote").asText()));
      node.remove(List.of("url", "publishedOn", "factQuote"));
    }
  }

  private MockResponse sourceResponse(List<String> names, List<String> urls) throws Exception {
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            mapper.writeValueAsString(
                Map.of(
                    "output",
                    List.of(
                        Map.of(
                            "type",
                            "web_search_call",
                            "status",
                            "completed",
                            "action",
                            Map.of(
                                "sources", urls.stream().map(url -> Map.of("url", url)).toList())),
                        Map.of(
                            "content",
                            List.of(
                                Map.of(
                                    "type",
                                    "output_text",
                                    "text",
                                    mapper.writeValueAsString(
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
                                                            urls))
                                                .toList())),
                                    "annotations",
                                    List.of())))))));
  }

  private static PortfolioAnalysisEvidenceVerifier acceptingVerifier() {
    var verifier = org.mockito.Mockito.mock(PortfolioAnalysisEvidenceVerifier.class);
    org.mockito.Mockito.when(
            verifier.verifyPrepared(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
    org.mockito.Mockito.when(
            verifier.prepare(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
        .thenAnswer(
            invocation -> {
              Map<String, List<String>> urls = invocation.getArgument(0);
              Map<String, org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument>
                  documents = new java.util.LinkedHashMap<>();
              urls.values().stream()
                  .flatMap(List::stream)
                  .forEach(
                      url ->
                          documents.put(
                              url,
                              new org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument(
                                  "공식 발표",
                                  "Operating profit increased by 10%.\n2026-10-12",
                                  java.time.LocalDate.of(2026, 10, 12))));
              return PortfolioAnalysisEvidence.from(urls, documents);
            });
    return verifier;
  }
}
