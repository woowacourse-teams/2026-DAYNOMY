package org.grit.daynomy.portfolio.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
            뉴스 본문이 사용자의 포트폴리오 자산에 미치는 영향을 분석하세요.

            - 제공된 자산만 분석하세요.
            - 뉴스와 관련성이 있는 자산만 결과에 포함하세요.
            - 영향이 큰 자산부터 정렬하세요.
            - assetName은 제공된 값을 그대로 사용하세요.
            - direction은 뉴스가 자산에 유리한 직접 영향을 주면 POSITIVE, 불리한 직접 영향을 주면 NEGATIVE로 판단하세요.
            - 관련성은 있지만 긍정 또는 부정 방향을 판단할 근거가 충분하지 않으면 NEUTRAL로 판단하세요.
            - impactLevel은 HIGH, MEDIUM, LOW 중 하나로 판단하세요.
            - expectedReaction에는 예상되는 자산 반응을 작성하세요.
            - reason에는 판단 근거를 작성하세요.
            - evidenceSentence는 해당 자산의 direction과 impactLevel 판단을 직접 뒷받침하는 뉴스 원문 문장 하나여야 합니다.
            - newsContent에 문자 그대로 존재하는 완전한 문장만 복사하세요. 문장을 요약·변형·조합하거나 새로운 내용을 만들지 마세요.
            - 해당 자산과의 영향 관계를 직접 뒷받침하는 원문 문장이 없다면, 관련 없는 문장을 대신 사용하지 말고 해당 자산을 impacts 결과에서 제외하세요.
            - 뉴스에 없는 사실을 단정하지 마세요.
            """;

  private final ObjectMapper objectMapper;
  private final RestClient restClient;
  private final String apiKey;
  private final String model;

  public OpenAiPortfolioAnalysisClient(
      RestClient.Builder restClientBuilder,
      @Value("${ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
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
            targets.stream().map(target -> Map.of("assetName", target.assetName())).toList());

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
        "assetName",
        Map.of(
            "type",
            "string",
            "enum",
            targets.stream().map(PortfolioAnalysisTarget::assetName).toList()));
    impactProperties.put(
        "direction", Map.of("type", "string", "enum", enumNames(ImpactDirection.values())));
    impactProperties.put(
        "impactLevel", Map.of("type", "string", "enum", enumNames(ImpactLevel.values())));
    impactProperties.put("expectedReaction", Map.of("type", "string"));
    impactProperties.put("reason", Map.of("type", "string"));
    impactProperties.put("evidenceSentence", Map.of("type", "string"));

    Map<String, Object> impactItem = new LinkedHashMap<>();
    impactItem.put("type", "object");
    impactItem.put("additionalProperties", false);
    impactItem.put(
        "required",
        List.of(
            "assetName",
            "direction",
            "impactLevel",
            "expectedReaction",
            "reason",
            "evidenceSentence"));
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

    Map<String, PortfolioAnalysisTarget> targetByAssetName = new HashMap<>();
    for (PortfolioAnalysisTarget target : targets) {
      if (targetByAssetName.put(target.assetName(), target) != null) {
        throw analysisFailed();
      }
    }

    Set<String> analyzedAssetNames = new HashSet<>();
    List<ParsedAssetImpact> parsedImpacts = new ArrayList<>();

    for (JsonNode impactNode : impactsNode) {
      JsonNode assetNameNode = impactNode.path("assetName");
      if (!assetNameNode.isTextual()) {
        throw analysisFailed();
      }
      String assetName = assetNameNode.textValue();
      PortfolioAnalysisTarget target = targetByAssetName.get(assetName);

      if (target == null || !analyzedAssetNames.add(assetName)) {
        throw analysisFailed();
      }

      parsedImpacts.add(
          new ParsedAssetImpact(
              target.assetName(),
              ImpactDirection.valueOf(impactNode.path("direction").asText()),
              ImpactLevel.valueOf(impactNode.path("impactLevel").asText()),
              impactNode.path("expectedReaction").asText(),
              impactNode.path("reason").asText(),
              impactNode.path("evidenceSentence").asText()));
    }

    parsedImpacts.sort(
        Comparator.comparingInt(impact -> impactLevelPriority(impact.impactLevel())));

    List<PortfolioAnalysisResult.AssetImpactResult> results = new ArrayList<>();
    for (int index = 0; index < parsedImpacts.size(); index++) {
      results.add(parsedImpacts.get(index).toResult(index + 1));
    }

    return List.copyOf(results);
  }

  private int impactLevelPriority(ImpactLevel impactLevel) {
    return switch (impactLevel) {
      case HIGH -> 1;
      case MEDIUM -> 2;
      case LOW -> 3;
    };
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

  private record ParsedAssetImpact(
      String assetName,
      ImpactDirection direction,
      ImpactLevel impactLevel,
      String expectedReaction,
      String reason,
      String evidenceSentence) {

    private PortfolioAnalysisResult.AssetImpactResult toResult(int sortOrder) {
      return new PortfolioAnalysisResult.AssetImpactResult(
          assetName, direction, impactLevel, expectedReaction, reason, evidenceSentence, sortOrder);
    }
  }
}
