package org.grit.daynomy.portfolio.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
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
            웹 검색을 통해 오늘의 주요 경제·금융·산업 이슈를 찾고, 사용자의 포트폴리오 자산에 미치는 영향을 분석하세요.

            - 제공된 모든 자산을 impacts 결과에 한 번씩 포함하세요.
            - 각 자산과 관련성이 높고 신뢰할 수 있는 최신 검색 결과를 근거로 사용하세요.
            - 검색 결과에서 확인한 출처를 반드시 인용하세요.
            - 영향이 큰 자산부터 정렬하세요.
            - assetName은 제공된 값을 그대로 사용하세요.
            - direction은 검색 결과에서 확인한 직접적인 영향만을 근거로 판단하세요.
            - 자산의 실적, 수요, 경쟁력 또는 수급에 유리한 직접 영향이 명확하면 POSITIVE로 판단하세요.
            - 자산의 실적, 수요, 경쟁력 또는 수급에 불리한 직접 영향이 명확하면 NEGATIVE로 판단하세요.
            - 긍정 또는 부정 방향을 판단할 직접적인 근거가 부족하거나 긍정·부정 요인이 함께 존재하면 NEUTRAL로 판단하세요.
            - 시장 전반의 분위기나 일반적인 업황만으로 개별 자산의 방향을 추측하지 마세요.
            - impactLevel은 direction과 관계없이 검색 결과에서 확인한 영향의 범위, 규모, 즉시성, 확실성을 기준으로 HIGH, LOW, MEDIUM 순서로 판단하세요.
            - 기업 전반이나 주요 실적·생산·수급에 미치는 영향이 크고 구체적이면 HIGH로 판단하세요. 영향 기간이 짧더라도 규모가 크면 HIGH를 유지하세요.
            - HIGH에 해당하지 않고 영향 규모가 작거나 일시적이라고 명시된 경우에는 LOW로 우선 판단하세요.
            - HIGH와 LOW에 해당하지 않으면서 직접적인 영향은 명확하지만 범위가 일부 사업·제품에 한정되거나 규모 또는 시점이 불확실하면 MEDIUM으로 판단하세요.
            - expectedReaction에는 예상되는 자산 반응을 자연스러운 해요체로 작성하세요.
            - issueSummary에는 자산과 관련된 오늘의 주요 이슈를 요약하세요.
            - outlook에는 검색 결과를 근거로 향후 방향성을 작성하세요.
            - reason에는 판단 근거를 자연스러운 해요체로 작성하세요.
            - overallImpact는 투자 초보자도 쉽게 이해할 수 있도록 3~4문장으로 작성하세요.
            - overallImpact에는 포트폴리오에서 비중이 큰 자산과 주요 이슈를 먼저 설명하세요.
            - 긍정·부정 요인이 전체 포트폴리오에 어떻게 작용하는지 구체적으로 설명하세요.
            - overallImpact의 마지막 문장에는 앞으로 주의해서 볼 지표나 이슈를 안내하세요.
            - 어려운 금융 용어와 단정적인 투자 권유 표현은 사용하지 마세요.
            - issueSummary, expectedReaction, outlook, reason, overallImpact의 모든 문장은 '-했어요.', '-해요.', '-예요.'와 같은 해요체로 끝내세요.
            - 사용자에게 노출되는 문장에 '-하다.', '-했음.', '-함.'과 같은 문어체나 명사형 종결 표현을 사용하지 마세요.
            - evidenceSentence에는 해당 자산의 direction과 impactLevel 판단을 뒷받침하는 검색 근거를 한 문장으로 작성하세요.
            - 중요한 관련 이슈를 찾지 못한 자산은 NEUTRAL과 LOW로 판단하고, 관련 이슈가 확인되지 않았음을 명시하세요.
            - 검색 결과에서 확인할 수 없는 사실을 단정하지 마세요.
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
  public PortfolioAnalysisResult analyze(List<PortfolioAnalysisTarget> targets) {
    if (targets.isEmpty()) {
      return new PortfolioAnalysisResult("", List.of(), List.of());
    }

    if (apiKey == null || apiKey.isBlank()) {
      throw analysisFailed();
    }

    long startedAt = System.nanoTime();
    log.atDebug()
        .addKeyValue("event", LogEvent.PORTFOLIO_ANALYSIS_REQUESTED.code())
        .addKeyValue("api", "OpenAI")
        .addKeyValue("operation", "portfolio-analysis")
        .addKeyValue("targetCount", targets.size())
        .log(LogEvent.PORTFOLIO_ANALYSIS_REQUESTED.message());

    try {
      String response =
          restClient
              .post()
              .uri("/responses")
              .contentType(MediaType.APPLICATION_JSON)
              .headers(headers -> headers.setBearerAuth(apiKey))
              .body(createRequest(targets))
              .retrieve()
              .body(String.class);

      PortfolioAnalysisResult result = parseAnalysis(response, targets);
      log.atInfo()
          .addKeyValue("event", LogEvent.PORTFOLIO_ANALYSIS_COMPLETED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "portfolio-analysis")
          .addKeyValue("targetCount", targets.size())
          .addKeyValue("analyzedCount", result.impacts().size())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.PORTFOLIO_ANALYSIS_COMPLETED.message());
      return result;
    } catch (HttpStatusCodeException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.PORTFOLIO_ANALYSIS_FAILED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "portfolio-analysis")
          .addKeyValue("httpStatus", exception.getStatusCode().value())
          .addKeyValue("targetCount", targets.size())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .log(LogEvent.PORTFOLIO_ANALYSIS_FAILED.message());
      throw analysisFailed();
    } catch (RestClientException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.PORTFOLIO_ANALYSIS_FAILED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "portfolio-analysis")
          .addKeyValue("targetCount", targets.size())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .log(LogEvent.PORTFOLIO_ANALYSIS_FAILED.message());
      throw analysisFailed();
    } catch (BusinessException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.PORTFOLIO_ANALYSIS_FAILED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "portfolio-analysis")
          .addKeyValue("targetCount", targets.size())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .addKeyValue("errorType", "InvalidAiResponse")
          .log(LogEvent.PORTFOLIO_ANALYSIS_FAILED.message());
      throw exception;
    }
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  private Map<String, Object> createRequest(List<PortfolioAnalysisTarget> targets) {
    return Map.of(
        "model", model,
        "reasoning", Map.of("effort", "low"),
        "tools", List.of(Map.of("type", "web_search")),
        "tool_choice", "required",
        "input", createInput(targets),
        "text", createTextFormat(targets));
  }

  private List<Map<String, Object>> createInput(List<PortfolioAnalysisTarget> targets) {
    return List.of(
        Map.of("role", "developer", "content", PORTFOLIO_ANALYSIS_PROMPT),
        Map.of("role", "user", "content", createUserContent(targets)));
  }

  private String createUserContent(List<PortfolioAnalysisTarget> targets) {
    Map<String, Object> content =
        Map.of(
            "assets",
            targets.stream()
                .map(target -> Map.of("assetName", target.assetName(), "weight", target.weight()))
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
    properties.put("overallImpact", Map.of("type", "string"));
    properties.put("impacts", createImpactsSchema(targets));

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.put("required", List.of("overallImpact", "impacts"));
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
    impactProperties.put("issueSummary", Map.of("type", "string"));
    impactProperties.put("expectedReaction", Map.of("type", "string"));
    impactProperties.put("outlook", Map.of("type", "string"));
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
            "issueSummary",
            "expectedReaction",
            "outlook",
            "reason",
            "evidenceSentence"));
    impactItem.put("properties", impactProperties);

    return Map.of(
        "type",
        "array",
        "minItems",
        targets.size(),
        "maxItems",
        targets.size(),
        "items",
        impactItem);
  }

  private <E extends Enum<E>> List<String> enumNames(E[] values) {
    return Arrays.stream(values).map(Enum::name).toList();
  }

  private PortfolioAnalysisResult parseAnalysis(
      String response, List<PortfolioAnalysisTarget> targets) {
    OutputContent output = extractOutputContent(response);

    try {
      JsonNode root = objectMapper.readTree(output.text());
      if (root == null) {
        throw analysisFailed();
      }
      return new PortfolioAnalysisResult(
          requiredText(root, "overallImpact"),
          parseImpacts(root.path("impacts"), targets),
          output.sources());
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

      JsonNode evidenceSentenceNode = impactNode.path("evidenceSentence");
      if (!evidenceSentenceNode.isTextual()) {
        throw analysisFailed();
      }
      String evidenceSentence = evidenceSentenceNode.textValue();
      if (evidenceSentence.isBlank()) {
        throw analysisFailed();
      }

      parsedImpacts.add(
          new ParsedAssetImpact(
              target.assetName(),
              ImpactDirection.valueOf(impactNode.path("direction").asText()),
              ImpactLevel.valueOf(impactNode.path("impactLevel").asText()),
              requiredText(impactNode, "issueSummary"),
              requiredText(impactNode, "expectedReaction"),
              requiredText(impactNode, "outlook"),
              requiredText(impactNode, "reason"),
              evidenceSentence));
    }

    if (analyzedAssetNames.size() != targets.size()) {
      throw analysisFailed();
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

  private String requiredText(JsonNode node, String fieldName) {
    JsonNode field = node.path(fieldName);
    if (!field.isTextual() || field.textValue().isBlank()) {
      throw analysisFailed();
    }
    return field.textValue();
  }

  private OutputContent extractOutputContent(String response) {
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
              List<PortfolioAnalysisResult.Source> sources =
                  parseSources(contentItem.path("annotations"));
              if (sources.isEmpty()) {
                throw analysisFailed();
              }
              return new OutputContent(outputText, sources);
            }
          }
        }
      }
    } catch (JsonProcessingException | IllegalArgumentException exception) {
      throw analysisFailed();
    }

    throw analysisFailed();
  }

  private List<PortfolioAnalysisResult.Source> parseSources(JsonNode annotationsNode) {
    if (!annotationsNode.isArray()) {
      throw analysisFailed();
    }

    Map<String, PortfolioAnalysisResult.Source> sourceByUrl = new LinkedHashMap<>();
    for (JsonNode annotation : annotationsNode) {
      if (!"url_citation".equals(annotation.path("type").asText())) {
        continue;
      }

      String title = requiredText(annotation, "title");
      String url = requiredText(annotation, "url");
      if (!isHttpUrl(url)) {
        throw analysisFailed();
      }
      sourceByUrl.putIfAbsent(url, new PortfolioAnalysisResult.Source(title, url));
    }
    return List.copyOf(sourceByUrl.values());
  }

  private boolean isHttpUrl(String url) {
    try {
      String scheme = URI.create(url).getScheme();
      return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private BusinessException analysisFailed() {
    return new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
  }

  private record ParsedAssetImpact(
      String assetName,
      ImpactDirection direction,
      ImpactLevel impactLevel,
      String issueSummary,
      String expectedReaction,
      String outlook,
      String reason,
      String evidenceSentence) {

    private PortfolioAnalysisResult.AssetImpactResult toResult(int sortOrder) {
      return new PortfolioAnalysisResult.AssetImpactResult(
          assetName,
          direction,
          impactLevel,
          issueSummary,
          expectedReaction,
          outlook,
          reason,
          evidenceSentence,
          sortOrder);
    }
  }

  private record OutputContent(String text, List<PortfolioAnalysisResult.Source> sources) {}
}
