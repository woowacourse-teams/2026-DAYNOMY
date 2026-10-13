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
  void summarizesAllVerifiedAssetsWithoutSearchingAndExplainsPartialCoverage() throws Exception {
    enqueue(
        List.of(
            "SK하이닉스의 실적 발표와 포스코퓨처엠의 공급 계약을 확인했어요.",
            "실적과 수주에 유리하지만 원가와 납품 일정은 확인해야 해요.",
            "다음 실적 발표에서 이익 반영 여부를 확인해 보세요."));
    String summary = client.summarize(analysis());
    assertThat(summary.split("\n\n")).hasSize(3);
    assertThat(summary).contains("4개 중 2개(비중 25%)", "전체 영향은 판단을 보류해요.", "포스코퓨처엠");
    assertThat(summary).doesNotContain("삼성전자:", "SK하이닉스:");
    var request = mapper.readTree(server.takeRequest().getBody().readUtf8());
    assertThat(request.has("tools")).isFalse();
    assertThat(request.path("input").get(1).path("content").asText())
        .contains("SK하이닉스", "포스코퓨처엠", "POSITIVE")
        .doesNotContain("삼성전자", "포스코인터내셔널");
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void rejectsOmittedVerifiedAssetWithoutRepeatedGeneration() throws Exception {
    enqueue(List.of("SK하이닉스의 실적을 확인했어요.", "실적에 유리해요.", "다음 발표를 확인해 보세요."));
    assertThatThrownBy(() -> client.summarize(analysis())).isInstanceOf(BusinessException.class);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void rejectsColonConcatenationAndEnglishSuffix() throws Exception {
    for (String text :
        List.of(
            "SK하이닉스: 실적이 좋아요. 포스코퓨처엠: 계약이 있어요.",
            "SK하이닉스 POSCO Future M signs a supply contract with SK On..요.")) {
      enqueue(List.of(text, "실적에 유리해요.", "다음 발표를 확인해 보세요."));
      assertThatThrownBy(() -> client.summarize(analysis())).isInstanceOf(BusinessException.class);
    }
    assertThat(server.getRequestCount()).isEqualTo(2);
  }

  @Test
  void noEvidenceDoesNotGenerateFillerParagraphsOrCallAi() {
    var input = PortfolioAnalysisResponse.empty();
    assertThat(client.summarize(input)).isEqualTo("보유 종목의 판단에 필요한 근거를 확인하지 못했어요.");
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void apiFailureDoesNotPublishAnUnverifiedSummary() {
    server.enqueue(new MockResponse().setResponseCode(503));
    assertThatThrownBy(() -> client.summarize(analysis())).isInstanceOf(BusinessException.class);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  private void enqueue(List<String> paragraphs) throws Exception {
    String text = mapper.writeValueAsString(Map.of("paragraphs", paragraphs));
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
