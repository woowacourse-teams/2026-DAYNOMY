package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.domain.PortfolioImpactSummary;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class PortfolioAnalysisNarrativeTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private MockWebServer server;
  private PortfolioAnalysisNarrative client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client =
        new PortfolioAnalysisNarrative(
            RestClient.builder(), server.url("/").toString(), "test", "test-model");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void combinesActualHoldingsWithWeightedEvidenceAndPartialCoverage() throws Exception {
    enqueue(
        Map.of(
            "impact",
            "확인된 긍정 요인은 일부 종목에 한정돼 있어요.",
            "priority",
            "비중이 큰 삼성전자의 실적과 수요 전망부터 확인해야 해요."));
    String summary = client.summarize(analysis());
    var paragraphs = summary.split("\n\n");
    assertThat(paragraphs).hasSize(3);
    assertThat(paragraphs[0]).contains("4개 종목", "삼성전자", "60%", "절반 넘게");
    assertThat(paragraphs[1]).contains("25%", "전체 영향은 판단을 보류해요.");
    assertThat(paragraphs[2]).contains("삼성전자");
    var request = mapper.readTree(server.takeRequest().getBody().readUtf8());
    assertThat(request.has("tools")).isFalse();
    var input = mapper.readTree(request.path("input").get(1).path("content").asText());
    assertThat(input.path("assets").size()).isEqualTo(4);
    assertThat(input.path("assets").get(0).path("direction").asText()).isEqualTo("INSUFFICIENT");
    assertThat(input.path("assets").get(0).path("event").asText()).isEmpty();
    assertThat(input.path("assets").get(2).path("weight").asInt()).isEqualTo(20);
    assertThat(input.path("assets").get(2).path("impactLevel").asText()).isEqualTo("HIGH");
    assertThat(input.path("overallDirection").asText()).isEqualTo("UNDETERMINED");
  }

  @Test
  void rejectsMissingParagraphsListsCopiedAssetDescriptionsAndBrokenProse() throws Exception {
    for (var output :
        List.of(
            Map.of("impact", "전체 영향을 살펴봐야 해요."),
            Map.of("impact", "", "priority", "실적을 확인해야 해요."),
            Map.of("impact", "삼성전자: 실적을 확인해요.", "priority", "실적을 확인해야 해요."),
            Map.of("impact", "실적 개선을 기대해요.", "priority", "실적을 확인해야 해요."),
            Map.of("impact", "좋은 소식이에요.\n새 발표를 확인해요.", "priority", "실적을 확인해야 해요."),
            Map.of(
                "impact",
                "POSCO Future M signs a supply contract with SK On..요.",
                "priority",
                "실적을 확인해야 해요."))) {
      enqueue(output);
      assertThatThrownBy(() -> client.summarize(analysis())).isInstanceOf(BusinessException.class);
    }
    assertThat(server.getRequestCount()).isEqualTo(6);
  }

  @Test
  void reportsCompositionEvenWhenAllEvidenceIsInsufficientWithoutCallingAi() {
    var impacts =
        analysis().impacts().stream()
            .map(i -> impact(i.assetName(), i.weight().toPlainString(), false))
            .toList();
    String summary = client.summarize(withImpacts(impacts));
    assertThat(summary.split("\n\n")).hasSize(3);
    assertThat(summary).contains("삼성전자 비중은 60%", "전체 영향은 판단을 보류해요.");
    assertThat(summary).doesNotContain("긍정", "부정", "중립");
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void compositionHandlesSingleAssetAndEqualWeightsWithoutInventingDiversification() {
    assertThat(client.summarize(withImpacts(List.of(impact("삼성전자", "100", false)))))
        .contains("100%", "한 종목에 자산이 집중");
    assertThat(
            client.summarize(
                withImpacts(List.of(impact("삼성전자", "50", false), impact("SK하이닉스", "50", false)))))
        .contains("같은 비중")
        .doesNotContain("절반 넘게", "안전", "분산 효과");
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void completeMixedPortfolioPassesBothPositiveAndNegativeEvidenceToSynthesis() throws Exception {
    var positive = impact("삼성전자", "60", true);
    var negative =
        new PortfolioAssetImpactResponse(
            "SK하이닉스",
            new BigDecimal("40"),
            ImpactDirection.NEGATIVE,
            ImpactLevel.HIGH,
            "비용이 늘었어요.",
            "이익에 부담이 있어요.",
            "다음 실적을 확인해요.",
            "비용 증가를 확인했어요.",
            "비용 증가를 확인했어요.",
            List.of(),
            2);
    enqueue(
        Map.of(
            "impact",
            "비중이 큰 삼성전자의 긍정 요인과 다른 종목의 비용 부담이 함께 작용해요.",
            "priority",
            "주요 종목의 이익 변화부터 확인해야 해요."));
    assertThat(client.summarize(withImpacts(List.of(positive, negative)))).doesNotContain("판단을 보류");
    var request = mapper.readTree(server.takeRequest().getBody().readUtf8());
    var input = mapper.readTree(request.path("input").get(1).path("content").asText());
    assertThat(input.path("coverage").asText()).isEqualTo("COMPLETE");
    assertThat(input.path("assets").get(1).path("direction").asText()).isEqualTo("NEGATIVE");
    assertThat(input.path("overallDirection").asText()).isEqualTo("POSITIVE");
  }

  @Test
  void singleAssetMayShareItsOwnImpactWithoutFailingTheWholeAnalysis() throws Exception {
    enqueue(Map.of("impact", "실적 개선을 기대해요.", "priority", "다음 발표를 확인해 보세요."));
    assertThat(client.summarize(withImpacts(List.of(impact("삼성전자", "100", true)))))
        .contains("한 종목에 자산이 집중", "실적 개선을 기대해요.");
  }

  @Test
  void emptyPortfolioDoesNotCallAi() {
    assertThat(client.summarize(PortfolioAnalysisResponse.empty())).isEmpty();
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void apiFailureDoesNotPublishAnUnverifiedSummary() {
    server.enqueue(new MockResponse().setResponseCode(503));
    assertThatThrownBy(() -> client.summarize(analysis())).isInstanceOf(BusinessException.class);
  }

  @Test
  void selectedPortfolioKeepsFullCompositionAndDoesNotTreatExclusionsAsInsufficient()
      throws Exception {
    var selected = List.of(impact("삼성전자", "60", true), impact("SK하이닉스", "20", true));
    var value =
        PortfolioAnalysisResponse.of(
                4,
                PortfolioImpactSummary.calculate(
                    selected.stream()
                        .map(
                            i ->
                                new PortfolioImpactSummary.AssetImpact(
                                    i.weight(), i.direction(), i.impactLevel()))
                        .toList()),
                Instant.now(),
                "",
                selected,
                List.of())
            .withExcludedAssets(
                List.of(
                    new org.grit.daynomy.portfolio.dto.PortfolioAssetRequest(
                        "삼성전자", new BigDecimal("60")),
                    new org.grit.daynomy.portfolio.dto.PortfolioAssetRequest(
                        "SK하이닉스", new BigDecimal("20")),
                    new org.grit.daynomy.portfolio.dto.PortfolioAssetRequest(
                        "기타1", new BigDecimal("15")),
                    new org.grit.daynomy.portfolio.dto.PortfolioAssetRequest(
                        "기타2", new BigDecimal("5"))));
    enqueue(Map.of("impact", "확인된 실적이 선택 종목에 유리해요.", "priority", "새 발표를 확인해야 해요."));

    var summary = client.summarize(value);

    assertThat(summary)
        .contains("4개 종목", "2개 종목을 선별", "나머지 2개 종목은 분석하지 않았어요", "80%", "전체 영향은 판단을 보류해요");
    var request = mapper.readTree(server.takeRequest().getBody().readUtf8());
    var input = mapper.readTree(request.path("input").get(1).path("content").asText());
    assertThat(input.path("assets").size()).isEqualTo(2);
    assertThat(input.path("excludedAssets").size()).isEqualTo(2);
    assertThat(input.path("excludedAssets").get(0).has("direction")).isFalse();
  }

  private void enqueue(Map<String, String> output) throws Exception {
    String text = mapper.writeValueAsString(output);
    String response =
        mapper.writeValueAsString(
            Map.of(
                "status",
                "completed",
                "output",
                List.of(
                    Map.of(
                        "type",
                        "message",
                        "content",
                        List.of(Map.of("type", "output_text", "text", text))))));
    server.enqueue(
        new MockResponse().addHeader("Content-Type", "application/json").setBody(response));
  }

  private PortfolioAnalysisResponse withImpacts(List<PortfolioAssetImpactResponse> impacts) {
    return PortfolioAnalysisResponse.of(
        impacts.size(),
        PortfolioImpactSummary.calculate(
            impacts.stream()
                .map(
                    i ->
                        new PortfolioImpactSummary.AssetImpact(
                            i.weight(), i.direction(), i.impactLevel()))
                .toList()),
        Instant.now(),
        "",
        impacts,
        List.of());
  }

  private PortfolioAnalysisResponse analysis() {
    var impacts =
        List.of(
            impact("삼성전자", "60", false),
            impact("포스코인터내셔널", "15", false),
            impact("SK하이닉스", "20", true),
            impact("포스코퓨처엠", "5", true));
    return PortfolioAnalysisResponse.of(
        4,
        PortfolioImpactSummary.calculate(
            impacts.stream()
                .map(
                    i ->
                        new PortfolioImpactSummary.AssetImpact(
                            i.weight(), i.direction(), i.impactLevel()))
                .toList()),
        Instant.now(),
        "",
        impacts,
        List.of());
  }

  private PortfolioAssetImpactResponse impact(String name, String weight, boolean valid) {
    return new PortfolioAssetImpactResponse(
        name,
        new BigDecimal(weight),
        valid ? ImpactDirection.POSITIVE : null,
        valid ? ImpactLevel.HIGH : null,
        name + "의 발표를 확인했어요.",
        "실적 개선을 기대해요.",
        "다음 발표를 확인해 보세요.",
        "계약을 확인했어요.",
        "계약을 확인했어요.",
        List.of(),
        1);
  }
}
