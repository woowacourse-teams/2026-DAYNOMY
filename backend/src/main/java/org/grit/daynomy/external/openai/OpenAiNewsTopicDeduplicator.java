package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.news.domain.NewsGenerationTopic;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OpenAiNewsTopicDeduplicator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int MAX_OUTPUT_TOKENS = 2_000;
  private static final String PROMPT =
      OpenAiPromptLoader.load("prompts/news-topic-deduplicator.txt");

  private final OpenAiProperties properties;
  private final RestClient restClient;

  public OpenAiNewsTopicDeduplicator(
      OpenAiProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
    requestFactory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    this.restClient =
        restClientBuilder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
  }

  public List<EconomicNewsResearch> removeDuplicates(
      List<EconomicNewsResearch> candidates, List<NewsGenerationTopic> recentTopics) {
    if (candidates.isEmpty()) {
      return List.of();
    }

    log.atInfo()
        .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_REQUESTED.code())
        .addKeyValue("api", "OpenAI")
        .addKeyValue("operation", "news-topic-deduplication")
        .addKeyValue("model", properties.topicDedupModel())
        .addKeyValue("candidateCount", candidates.size())
        .addKeyValue("comparisonCount", recentTopics.size())
        .log(LogEvent.AI_NEWS_GENERATION_REQUESTED.message());

    try {
      List<EconomicNewsResearch> result =
          parse(requestComparison(candidates, recentTopics), candidates, recentTopics);
      log.atInfo()
          .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_COMPLETED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "news-topic-deduplication")
          .addKeyValue("candidateCount", candidates.size())
          .addKeyValue("acceptedCount", result.size())
          .log(LogEvent.AI_NEWS_GENERATION_COMPLETED.message());
      return result;
    } catch (HttpStatusCodeException exception) {
      logFailure(exception.getClass().getSimpleName(), exception.getStatusCode().value());
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    } catch (RestClientException | IllegalArgumentException exception) {
      logFailure(exception.getClass().getSimpleName(), null);
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }
  }

  private String requestComparison(
      List<EconomicNewsResearch> candidates, List<NewsGenerationTopic> recentTopics) {
    String input = PROMPT + "\n\n비교 자료:\n" + comparisonJson(candidates, recentTopics);
    return restClient
        .post()
        .uri("/responses")
        .header("Authorization", "Bearer " + properties.apiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "model",
                properties.topicDedupModel(),
                "max_output_tokens",
                MAX_OUTPUT_TOKENS,
                "reasoning",
                Map.of("effort", "none"),
                "input",
                input,
                "text",
                Map.of("format", responseFormat())))
        .retrieve()
        .body(String.class);
  }

  private String comparisonJson(
      List<EconomicNewsResearch> candidates, List<NewsGenerationTopic> recentTopics) {
    try {
      List<Map<String, Object>> candidateValues = new ArrayList<>();
      for (int index = 0; index < candidates.size(); index++) {
        EconomicNewsResearch candidate = candidates.get(index);
        candidateValues.add(
            Map.of(
                "candidateIndex", index,
                "title", candidate.title(),
                "date", candidate.referenceDate().toString(),
                "category", candidate.category().name(),
                "facts", summaryFacts(candidate.facts())));
      }
      List<Map<String, Object>> topicValues =
          recentTopics.stream()
              .map(
                  topic ->
                      Map.<String, Object>of(
                          "topicId", topic.getId(),
                          "title", topic.getTitle(),
                          "date", topic.getReferenceDate().toString(),
                          "category", topic.getCategory().name(),
                          "facts", summaryFacts(topic.getFacts())))
              .toList();
      return OBJECT_MAPPER.writeValueAsString(
          Map.of("candidates", candidateValues, "recentTopics", topicValues));
    } catch (Exception exception) {
      throw new IllegalArgumentException("News topics could not be serialized.", exception);
    }
  }

  private List<String> summaryFacts(List<String> facts) {
    return facts.stream().limit(5).toList();
  }

  private Map<String, Object> responseFormat() {
    return Map.of(
        "type",
        "json_schema",
        "name",
        "news_topic_deduplication",
        "strict",
        true,
        "schema",
        Map.of(
            "type",
            "object",
            "additionalProperties",
            false,
            "properties",
            Map.of(
                "results",
                Map.of(
                    "type",
                    "array",
                    "items",
                    Map.of(
                        "type",
                        "object",
                        "additionalProperties",
                        false,
                        "properties",
                        Map.of(
                            "candidateIndex",
                            Map.of("type", "integer"),
                            "duplicateOf",
                            Map.of("type", List.of("string", "null"))),
                        "required",
                        List.of("candidateIndex", "duplicateOf")))),
            "required",
            List.of("results")));
  }

  private List<EconomicNewsResearch> parse(
      String response,
      List<EconomicNewsResearch> candidates,
      List<NewsGenerationTopic> recentTopics) {
    try {
      JsonNode results =
          OBJECT_MAPPER
              .readTree(extractOutputText(OBJECT_MAPPER.readTree(response)))
              .path("results");
      if (!results.isArray() || results.size() != candidates.size()) {
        throw new IllegalArgumentException("One deduplication result is required per candidate.");
      }

      Set<Long> topicIds = new HashSet<>();
      recentTopics.forEach(topic -> topicIds.add(topic.getId()));
      Set<Integer> seenIndexes = new HashSet<>();
      Set<Integer> duplicateIndexes = new HashSet<>();
      for (JsonNode result : results) {
        int candidateIndex = result.path("candidateIndex").asInt(-1);
        if (candidateIndex < 0
            || candidateIndex >= candidates.size()
            || !seenIndexes.add(candidateIndex)) {
          throw new IllegalArgumentException("Invalid candidate index in deduplication result.");
        }
        JsonNode duplicateOf = result.get("duplicateOf");
        if (duplicateOf == null || duplicateOf.isNull()) {
          continue;
        }
        validateDuplicateReference(duplicateOf.asText(), candidateIndex, topicIds);
        duplicateIndexes.add(candidateIndex);
      }

      List<EconomicNewsResearch> unique = new ArrayList<>();
      for (int index = 0; index < candidates.size(); index++) {
        if (!duplicateIndexes.contains(index)) {
          unique.add(candidates.get(index));
        }
      }
      return List.copyOf(unique);
    } catch (Exception exception) {
      throw new IllegalArgumentException("Invalid news topic deduplication response.", exception);
    }
  }

  private void validateDuplicateReference(
      String reference, int candidateIndex, Set<Long> topicIds) {
    if (reference.startsWith("topic:")) {
      long topicId = Long.parseLong(reference.substring("topic:".length()));
      if (!topicIds.contains(topicId)) {
        throw new IllegalArgumentException("Unknown duplicate topic.");
      }
      return;
    }
    if (reference.startsWith("candidate:")) {
      int duplicateIndex = Integer.parseInt(reference.substring("candidate:".length()));
      if (duplicateIndex < 0 || duplicateIndex >= candidateIndex) {
        throw new IllegalArgumentException("Candidate duplicate must reference an earlier item.");
      }
      return;
    }
    throw new IllegalArgumentException("Unknown duplicate reference.");
  }

  private String extractOutputText(JsonNode response) {
    for (JsonNode item : response.path("output")) {
      for (JsonNode content : item.path("content")) {
        if ("output_text".equals(content.path("type").asText())) {
          String text = content.path("text").asText();
          if (!text.isBlank()) {
            return text;
          }
        }
      }
    }
    throw new IllegalArgumentException("OpenAI response did not contain output text.");
  }

  private void logFailure(String errorType, Integer httpStatus) {
    var event =
        log.atError()
            .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_FAILED.code())
            .addKeyValue("api", "OpenAI")
            .addKeyValue("operation", "news-topic-deduplication")
            .addKeyValue("errorType", errorType);
    if (httpStatus != null) {
      event.addKeyValue("httpStatus", httpStatus);
    }
    event.log(LogEvent.AI_NEWS_GENERATION_FAILED.message());
  }
}
