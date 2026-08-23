package org.grit.daynomy.portfolio.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OpenAiPortfolioAnalysisClient implements PortfolioAnalysisAiClient {

  private static final String PORTFOLIO_ANALYSIS_PROMPT =
      """
            뉴스 본문이 사용자가 북마크한 자산에 미치는 영향을 분석하세요.

            - 제공된 자산만 분석하세요.
            - 뉴스와 관련성이 있는 자산만 결과에 포함하세요.
            - 영향이 큰 자산부터 정렬하세요.
            - direction은 POSITIVE 또는 NEGATIVE로 판단하세요.
            - impactLevel은 HIGH, MEDIUM, LOW 중 하나로 판단하세요.
            - expectedReaction에는 예상되는 자산 반응을 작성하세요.
            - reason에는 판단 근거를 작성하세요.
            - 뉴스에 없는 사실을 단정하지 마세요.
            """;

  private final ObjectMapper objectMapper;
  private final RestClient restClient;
  private final String apiKey;
  private final String model;

  public OpenAiPortfolioAnalysisClient(
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
  public PortfolioAnalysisResult analyze(
      String newsContent, List<PortfolioAnalysisTarget> targets) {
    if (targets.isEmpty()) {
      return new PortfolioAnalysisResult(List.of());
    }

    if (apiKey == null || apiKey.isBlank()) {
      throw analysisFailed();
    }

    try {
      String response =
          restClient
              .post()
              .uri("/responses")
              .contentType(MediaType.APPLICATION_JSON)
              .headers(headers -> headers.setBearerAuth(apiKey))
              .body(createRequest(newsContent, targets))
              .retrieve()
              .body(String.class);

      return parseAnalysis(response, targets);
    } catch (HttpStatusCodeException exception) {
      log.warn(
          "OpenAI portfolio analysis request failed: status={}, body={}, targetCount={}",
          exception.getStatusCode(),
          exception.getResponseBodyAsString(),
          targets.size());
      throw analysisFailed();
    } catch (RestClientException exception) {
      log.warn(
          "OpenAI portfolio analysis request failed: message={}, targetCount={}",
          exception.getMessage(),
          targets.size());
      throw analysisFailed();
    }
  }

  private Map<String, Object> createRequest(
      String newsContent, List<PortfolioAnalysisTarget> targets) {
    return Map.of(
        "model", model,
        "input", createInput(newsContent, targets),
        "text", createTextFormat(targets));
  }

  private List<Map<String, Object>> createInput(
      String newsContent, List<PortfolioAnalysisTarget> targets) {
    return List.of(
        Map.of("role", "developer", "content", PORTFOLIO_ANALYSIS_PROMPT),
        Map.of("role", "user", "content", createUserContent(newsContent, targets)));
  }

  private String createUserContent(String newsContent, List<PortfolioAnalysisTarget> targets) {
    Map<String, Object> content =
        Map.of(
            "newsContent",
            newsContent,
            "assets",
            targets.stream()
                .map(
                    target ->
                        Map.of(
                            "name", target.name(),
                            "category", target.category(),
                            "assetCode", target.assetCode()))
                .toList());

    try {
      return objectMapper.writeValueAsString(content);
    } catch (JsonProcessingException exception) {
      throw analysisFailed();
    }
  }

  private Map<String, Object> createTextFormat(List<PortfolioAnalysisTarget> targets) {
    return Map.of(
        "format",
        Map.of(
            "type",
            "json_schema",
            "name",
            "portfolio_analysis",
            "strict",
            true,
            "schema",
            createSchema(targets)));
  }

  private Map<String, Object> createSchema(List<PortfolioAnalysisTarget> targets) {
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("impacts", createImpactsSchema(targets));

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.put("required", List.of("impacts"));
    schema.put("properties", properties);

    return schema;
  }

  private Map<String, Object> createImpactsSchema(List<PortfolioAnalysisTarget> targets) {
    Map<String, Object> impactProperties = new LinkedHashMap<>();
    impactProperties.put(
        "assetCode",
        Map.of(
            "type",
            "string",
            "enum",
            targets.stream().map(PortfolioAnalysisTarget::assetCode).toList()));
    impactProperties.put(
        "direction", Map.of("type", "string", "enum", enumNames(ImpactDirection.values())));
    impactProperties.put(
        "impactLevel", Map.of("type", "string", "enum", enumNames(ImpactLevel.values())));
    impactProperties.put("expectedReaction", Map.of("type", "string"));
    impactProperties.put("reason", Map.of("type", "string"));

    Map<String, Object> impactItem = new LinkedHashMap<>();
    impactItem.put("type", "object");
    impactItem.put("additionalProperties", false);
    impactItem.put(
        "required", List.of("assetCode", "direction", "impactLevel", "expectedReaction", "reason"));
    impactItem.put("properties", impactProperties);

    return Map.of("type", "array", "maxItems", targets.size(), "items", impactItem);
  }

  private <E extends Enum<E>> List<String> enumNames(E[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }

  private PortfolioAnalysisResult parseAnalysis(
      String response, List<PortfolioAnalysisTarget> targets) {
    String outputText = extractOutputText(response);

    try {
      JsonNode root = objectMapper.readTree(outputText);
      if (root == null) {
        throw analysisFailed();
      }
      return new PortfolioAnalysisResult(parseImpacts(root.path("impacts"), targets));
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw analysisFailed();
    }
  }

  private List<PortfolioAnalysisResult.AssetImpactResult> parseImpacts(
      JsonNode impactsNode, List<PortfolioAnalysisTarget> targets) {
    if (!impactsNode.isArray()) {
      throw analysisFailed();
    }

    Map<String, PortfolioAnalysisTarget> targetByCode = new HashMap<>();
    for (PortfolioAnalysisTarget target : targets) {
      targetByCode.put(target.assetCode(), target);
    }

    Set<String> analyzedCodes = new HashSet<>();
    List<PortfolioAnalysisResult.AssetImpactResult> results = new ArrayList<>();
    int sortOrder = 1;

    for (JsonNode impactNode : impactsNode) {
      String assetCode = impactNode.path("assetCode").asText();
      PortfolioAnalysisTarget target = targetByCode.get(assetCode);

      if (target == null || !analyzedCodes.add(assetCode)) {
        throw analysisFailed();
      }

      results.add(
          new PortfolioAnalysisResult.AssetImpactResult(
              target.assetId(),
              target.bookmarkId(),
              ImpactDirection.valueOf(impactNode.path("direction").asText()),
              ImpactLevel.valueOf(impactNode.path("impactLevel").asText()),
              impactNode.path("expectedReaction").asText(),
              impactNode.path("reason").asText(),
              sortOrder++));
    }

    return List.copyOf(results);
  }

  private String extractOutputText(String response) {
    try {
      JsonNode root = objectMapper.readTree(response);
      if (root == null) {
        throw analysisFailed();
      }
      JsonNode output = root.path("output");

      if (!output.isArray()) {
        throw analysisFailed();
      }

      for (JsonNode item : output) {
        JsonNode content = item.path("content");

        if (!content.isArray()) {
          continue;
        }

        for (JsonNode contentItem : content) {
          if ("output_text".equals(contentItem.path("type").asText())) {
            String outputText = contentItem.path("text").asText();
            if (!outputText.isBlank()) {
              return outputText;
            }
          }
        }
      }
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw analysisFailed();
    }

    throw analysisFailed();
  }

  private BusinessException analysisFailed() {
    return new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
  }
}
