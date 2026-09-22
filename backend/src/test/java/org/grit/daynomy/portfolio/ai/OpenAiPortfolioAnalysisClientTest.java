package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

class OpenAiPortfolioAnalysisClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockWebServer server;
  private OpenAiPortfolioAnalysisClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    client =
        new OpenAiPortfolioAnalysisClient(
            RestClient.builder(), server.url("/").toString(), "test-api-key", "test-model");
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  @DisplayName("사용자가 입력한 종목명을 전달하고 AI 분석 결과를 영향도순으로 반환한다")
  void analyzeParsesAndSortsImpacts() throws Exception {
    enqueueOutput(
        """
        {
          "impacts": [
            {
              "assetName": "삼성전자",
              "direction": "POSITIVE",
              "impactLevel": "LOW",
              "expectedReaction": "장기적으로 긍정적일 수 있습니다.",
              "reason": "신규 수요가 예상됩니다.",
              "evidenceSentence": "삼성전자의 신규 수요가 증가할 전망입니다."
            },
            {
              "assetName": "SK하이닉스",
              "direction": "NEUTRAL",
              "impactLevel": "HIGH",
              "expectedReaction": "방향은 불분명합니다.",
              "reason": "상반된 요인이 존재합니다.",
              "evidenceSentence": "SK하이닉스의 수요와 비용이 모두 증가했습니다."
            }
          ]
        }
        """);

    PortfolioAnalysisResult result =
        client.analyze("삼성전자의 신규 수요가 증가할 전망입니다. SK하이닉스의 수요와 비용이 모두 증가했습니다.", targets());

    assertThat(result.impacts())
        .extracting(PortfolioAnalysisResult.AssetImpactResult::assetName)
        .containsExactly("SK하이닉스", "삼성전자");
    assertThat(result.impacts())
        .extracting(PortfolioAnalysisResult.AssetImpactResult::sortOrder)
        .containsExactly(1, 2);
    assertThat(result.impacts().getFirst().direction()).isEqualTo(ImpactDirection.NEUTRAL);
    assertThat(result.impacts().getFirst().impactLevel()).isEqualTo(ImpactLevel.HIGH);
    assertThat(result.impacts().getFirst().evidenceSentence())
        .isEqualTo("SK하이닉스의 수요와 비용이 모두 증가했습니다.");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/responses");
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-api-key");

    JsonNode requestBody = objectMapper.readTree(request.getBody().readUtf8());
    JsonNode userContent =
        objectMapper.readTree(requestBody.path("input").get(1).path("content").asText());
    assertThat(userContent.path("assets").get(0).path("assetName").asText()).isEqualTo("삼성전자");
    assertThat(userContent.toString()).doesNotContain("assetId");
  }

  @Test
  @DisplayName("요청하지 않은 종목명이 AI 응답에 포함되면 분석 실패로 처리한다")
  void analyzeRejectsUnknownAssetName() throws Exception {
    enqueueOutput(
        """
        {
          "impacts": [
            {
              "assetName": "현대차",
              "direction": "POSITIVE",
              "impactLevel": "HIGH",
              "expectedReaction": "긍정적입니다.",
              "reason": "수요가 증가했습니다.",
              "evidenceSentence": "현대차 수요가 증가했습니다."
            }
          ]
        }
        """);

    assertAnalysisFailed(() -> client.analyze("뉴스 본문", targets()));
  }

  @Test
  @DisplayName("근거 문장이 비어 있으면 분석 실패로 처리한다")
  void analyzeRejectsBlankEvidenceSentence() throws Exception {
    enqueueOutput(
        """
        {
          "impacts": [
            {
              "assetName": "삼성전자",
              "direction": "POSITIVE",
              "impactLevel": "HIGH",
              "expectedReaction": "긍정적입니다.",
              "reason": "수요가 증가했습니다.",
              "evidenceSentence": "   "
            }
          ]
        }
        """);

    assertAnalysisFailed(() -> client.analyze("삼성전자의 수요가 증가했습니다.", targets()));
  }

  @Test
  @DisplayName("근거 문장이 뉴스 원문에 없으면 분석 실패로 처리한다")
  void analyzeRejectsEvidenceSentenceNotInNewsContent() throws Exception {
    enqueueOutput(
        """
        {
          "impacts": [
            {
              "assetName": "삼성전자",
              "direction": "POSITIVE",
              "impactLevel": "HIGH",
              "expectedReaction": "긍정적입니다.",
              "reason": "수요가 증가했습니다.",
              "evidenceSentence": "삼성전자의 신규 수요가 크게 증가했습니다."
            }
          ]
        }
        """);

    assertAnalysisFailed(() -> client.analyze("삼성전자의 수요가 증가했습니다.", targets()));
  }

  @Test
  @DisplayName("AI 응답 형식이 올바르지 않으면 분석 실패로 처리한다")
  void analyzeRejectsMalformedOutput() throws Exception {
    enqueueOutput("{\"invalid\":[]}");

    assertAnalysisFailed(() -> client.analyze("뉴스 본문", targets()));
  }

  @Test
  @DisplayName("OpenAI가 HTTP 오류를 반환하면 분석 실패로 처리한다")
  void analyzeHandlesHttpError() {
    server.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .setBody("{\"error\":\"failed\"}"));

    assertAnalysisFailed(() -> client.analyze("뉴스 본문", targets()));
  }

  private List<PortfolioAnalysisTarget> targets() {
    return List.of(new PortfolioAnalysisTarget("삼성전자"), new PortfolioAnalysisTarget("SK하이닉스"));
  }

  private void enqueueOutput(String outputText) throws JsonProcessingException {
    String response =
        objectMapper.writeValueAsString(
            Map.of(
                "output",
                List.of(
                    Map.of(
                        "content", List.of(Map.of("type", "output_text", "text", outputText))))));
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
}
