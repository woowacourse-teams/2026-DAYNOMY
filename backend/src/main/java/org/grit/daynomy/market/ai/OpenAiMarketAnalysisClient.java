package org.grit.daynomy.market.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.grit.daynomy.market.domain.asset.AssetImpact;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.market.domain.scenario.Scenario;
import org.grit.daynomy.market.domain.scenario.TimeHorizon;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiMarketAnalysisClient implements MarketAnalysisAiClient {

  private static final String MARKET_ANALYSIS_PROMPT =
      """
      뉴스 본문을 바탕으로 시장 분석 요약을 작성하세요.
      발생 원인과 이 이슈가 시장에서 중요한 이유를 자연스럽게 연결해 하나의 내용으로 2~4문장 안에 설명하세요.
      본문에 없는 사실을 단정하지 말고, 불확실한 내용은 불확실하다고 명시하세요.
      """;

  private final ObjectMapper objectMapper;
  private final RestClient restClient;
  private final String apiKey;
  private final String model;

  public OpenAiMarketAnalysisClient(
      RestClient.Builder restClientBuilder,
      @Value("${ai.openai.base-url:https://api.openai.com}") String baseUrl,
      @Value("${ai.openai.api-key:}") String apiKey,
      @Value("${ai.openai.model:gpt-5-mini}") String model) {
    this.objectMapper = new ObjectMapper();
    this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    this.apiKey = apiKey;
    this.model = model;
  }

  @Override
  public NewsMarketAnalysis analyze(String newsContent) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY is required to analyze news market impact.");
    }

    String response =
        restClient
            .post()
            .uri("/responses")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(apiKey))
            .body(createRequest(newsContent))
            .retrieve()
            .body(String.class);

    return parseMarketAnalysis(response);
  }

  private Map<String, Object> createRequest(String newsContent) {
    return Map.of(
        "model", model,
        "input", createInput(newsContent),
        "text", createTextFormat());
  }

  private List<Map<String, Object>> createInput(String newsContent) {
    return List.of(
        Map.of("role", "developer", "content", MARKET_ANALYSIS_PROMPT),
        Map.of("role", "user", "content", newsContent));
  }

  private Map<String, Object> createTextFormat() {
    return Map.of(
        "format",
        Map.of(
            "type",
            "json_schema",
            "name",
            "market_analysis",
            "strict",
            true,
            "schema",
            createMarketAnalysisSchema()));
  }

  private Map<String, Object> createMarketAnalysisSchema() {
    return Map.of(
        "type",
        "object",
        "additionalProperties",
        false,
        "required",
        List.of("summary"),
        "properties",
        Map.of("summary", Map.of("type", "string")));
  }

  private NewsMarketAnalysis parseMarketAnalysis(String response) {
    String outputText = extractOutputText(response);
    try {
      JsonNode root = objectMapper.readTree(outputText);
      return new NewsMarketAnalysis(
          root.path("cause").asText(),
          root.path("importance").asText(),
          parseAssets(root.path("assets")),
          parseScenarios(root.path("scenarios")));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Failed to parse OpenAI market analysis response.", exception);
    }
  }

  private List<AssetImpact> parseAssets(JsonNode assetsNode) {
    if (!assetsNode.isArray()) {
      throw new IllegalStateException("OpenAI market analysis response must contain assets array.");
    }

    return assetsNode
        .valueStream()
        .map(
            node ->
                new AssetImpact(
                    AssetCategory.valueOf(node.path("category").asText()),
                    ImpactDirection.valueOf(node.path("direction").asText()),
                    ImpactLevel.valueOf(node.path("impactLevel").asText()),
                    node.path("reason").asText()))
        .toList();
  }

  private List<Scenario> parseScenarios(JsonNode scenariosNode) {
    if (!scenariosNode.isArray()) {
      throw new IllegalStateException(
          "OpenAI market analysis response must contain scenarios array.");
    }

    return scenariosNode
        .valueStream()
        .map(
            node ->
                new Scenario(
                    TimeHorizon.valueOf(node.path("timeHorizon").asText()),
                    node.path("prediction").asText(),
                    node.path("probability").asInt(),
                    node.path("reason").asText()))
        .toList();
  }

  private String extractOutputText(String response) {
    try {
      JsonNode output = objectMapper.readTree(response).path("output");
      if (!output.isArray()) {
        throw new IllegalStateException("OpenAI response must contain output array.");
      }

      for (JsonNode item : output) {
        JsonNode content = item.path("content");
        if (!content.isArray()) {
          continue;
        }

        for (JsonNode contentItem : content) {
          if ("output_text".equals(contentItem.path("type").asText())) {
            return contentItem.path("text").asText();
          }
        }
      }
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to parse OpenAI response.", exception);
    }

    throw new IllegalStateException("OpenAI response does not contain output_text.");
  }
}
