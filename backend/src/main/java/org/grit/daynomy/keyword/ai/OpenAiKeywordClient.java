package org.grit.daynomy.keyword.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OpenAiKeywordClient implements KeywordAiClient {

  private static final int MIN_KEYWORD_COUNT = 3;
  private static final int MIN_KEYWORD_LENGTH = 2;
  private static final int MAX_KEYWORD_LENGTH = 5;

  private static final String KEYWORD_EXTRACTION_PROMPT =
      """
      뉴스 본문에서 투자자가 이슈를 이해하는 데 필요한 핵심 키워드를 3개에서 5개 추출하세요.
      keyword는 공백과 문장부호를 포함해 2글자 이상 5글자 이하로 작성하세요.
      keyword는 뉴스 본문에 연속으로 존재하는 표현을 글자 그대로 복사하세요.
      keyword를 요약·변형·조합하거나, 단어·공백·문장부호·괄호 설명을 추가·삭제하지 마세요.
      단순 수치, 조사까지 포함한 문장 조각, 다른 기사에서 재사용하기 어려운 긴 표현은 선택하지 마세요.
      동일한 keyword를 중복해서 반환하지 마세요.
      definition은 키워드의 일반적인 뜻을 처음 접하는 사용자도 이해할 수 있게 1문장으로 작성하세요.
      context는 해당 키워드가 이 기사에서 갖는 의미를 본문에 근거해 1문장으로 작성하세요.
      watchPoint는 키워드를 해석할 때 주의할 점이나 이후 확인할 사항을 1문장으로 작성하세요.
      definition, context, watchPoint가 같은 내용을 반복하지 않게 작성하세요.
      각 키워드는 다음 기준에 따라 하나의 카테고리로 분류하세요.
      %s
      definition의 일반적인 용어 설명을 제외하고 본문에 없는 사실을 만들어 내지 마세요.
      """
          .formatted(KeywordCategory.DEFINITION);

  private final ObjectMapper objectMapper;
  private final RestClient restClient;
  private final String apiKey;
  private final String model;

  public OpenAiKeywordClient(
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
  public List<NewsKeyword> extractKeywords(String newsContent) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("OPENAI_API_KEY is required to extract news keywords.");
    }

    long startedAt = System.nanoTime();
    log.atDebug()
        .addKeyValue("event", LogEvent.KEYWORD_EXTRACTION_REQUESTED.code())
        .addKeyValue("api", "OpenAI")
        .addKeyValue("operation", "keyword-extraction")
        .log(LogEvent.KEYWORD_EXTRACTION_REQUESTED.message());

    try {
      String response =
          restClient
              .post()
              .uri("/responses")
              .contentType(MediaType.APPLICATION_JSON)
              .headers(headers -> headers.setBearerAuth(apiKey))
              .body(createRequest(newsContent))
              .retrieve()
              .body(String.class);

      List<NewsKeyword> keywords = parseKeywords(response, newsContent);
      log.atInfo()
          .addKeyValue("event", LogEvent.KEYWORD_EXTRACTION_COMPLETED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "keyword-extraction")
          .addKeyValue("keywordCount", keywords.size())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.KEYWORD_EXTRACTION_COMPLETED.message());
      return keywords;
    } catch (HttpStatusCodeException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.KEYWORD_EXTRACTION_FAILED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "keyword-extraction")
          .addKeyValue("httpStatus", exception.getStatusCode().value())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .log(LogEvent.KEYWORD_EXTRACTION_FAILED.message());
      throw exception;
    } catch (RestClientException exception) {
      logKeywordExtractionFailure(startedAt, exception.getClass().getSimpleName());
      throw exception;
    } catch (IllegalStateException | IllegalArgumentException exception) {
      logKeywordExtractionFailure(startedAt, "InvalidAiResponse");
      throw exception;
    }
  }

  private void logKeywordExtractionFailure(long startedAt, String errorType) {
    log.atWarn()
        .addKeyValue("event", LogEvent.KEYWORD_EXTRACTION_FAILED.code())
        .addKeyValue("api", "OpenAI")
        .addKeyValue("operation", "keyword-extraction")
        .addKeyValue("durationMs", elapsedMillis(startedAt))
        .addKeyValue("errorType", errorType)
        .log(LogEvent.KEYWORD_EXTRACTION_FAILED.message());
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  private Map<String, Object> createRequest(String newsContent) {
    return Map.of(
        "model", model,
        "input", createInput(newsContent),
        "text", createTextFormat());
  }

  private List<Map<String, Object>> createInput(String newsContent) {
    return List.of(
        Map.of("role", "developer", "content", KEYWORD_EXTRACTION_PROMPT),
        Map.of("role", "user", "content", newsContent));
  }

  private Map<String, Object> createTextFormat() {
    return Map.of(
        "format",
        Map.of(
            "type",
            "json_schema",
            "name",
            "news_keywords",
            "strict",
            true,
            "schema",
            createKeywordSchema()));
  }

  private Map<String, Object> createKeywordSchema() {
    Map<String, Object> keywordProperties = new LinkedHashMap<>();
    keywordProperties.put(
        "category",
        Map.of(
            "type",
            "string",
            "description",
            KeywordCategory.DEFINITION,
            "enum",
            enumNames(KeywordCategory.values())));
    keywordProperties.put(
        "keyword",
        Map.of("type", "string", "minLength", MIN_KEYWORD_LENGTH, "maxLength", MAX_KEYWORD_LENGTH));
    keywordProperties.put("definition", Map.of("type", "string"));
    keywordProperties.put("context", Map.of("type", "string"));
    keywordProperties.put("watchPoint", Map.of("type", "string"));

    Map<String, Object> keywordItem = new LinkedHashMap<>();
    keywordItem.put("type", "object");
    keywordItem.put("additionalProperties", false);
    keywordItem.put(
        "required", List.of("category", "keyword", "definition", "context", "watchPoint"));
    keywordItem.put("properties", keywordProperties);

    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put(
        "keywords",
        Map.of(
            "type", "array", "minItems", MIN_KEYWORD_COUNT, "maxItems", 5, "items", keywordItem));

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.put("required", List.of("keywords"));
    schema.put("properties", properties);
    return schema;
  }

  private <E extends Enum<E>> List<String> enumNames(E[] values) {
    return List.of(values).stream().map(Enum::name).toList();
  }

  private List<NewsKeyword> parseKeywords(String response, String newsContent) {
    String outputText = extractOutputText(response);
    try {
      JsonNode keywordsNode = objectMapper.readTree(outputText).path("keywords");
      if (!keywordsNode.isArray()) {
        throw new IllegalStateException("OpenAI keyword response must contain keywords array.");
      }

      List<NewsKeyword> keywords = keywordsNode.valueStream().map(this::parseKeyword).toList();
      return validateKeywords(keywords, newsContent);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to parse OpenAI keyword response.", exception);
    }
  }

  private List<NewsKeyword> validateKeywords(List<NewsKeyword> keywords, String newsContent) {
    Set<String> distinctKeywords = new HashSet<>();
    List<NewsKeyword> validKeywords =
        keywords.stream()
            .filter(keyword -> !keyword.getKeyword().isBlank())
            .filter(keyword -> isValidLength(keyword.getKeyword()))
            .filter(keyword -> newsContent.contains(keyword.getKeyword()))
            .filter(keyword -> distinctKeywords.add(keyword.getKeyword()))
            .toList();

    if (validKeywords.size() < MIN_KEYWORD_COUNT) {
      throw new IllegalStateException(
          "OpenAI keyword response must contain at least 3 valid keywords.");
    }

    return validKeywords;
  }

  private boolean isValidLength(String keyword) {
    int length = keyword.codePointCount(0, keyword.length());
    return length >= MIN_KEYWORD_LENGTH && length <= MAX_KEYWORD_LENGTH;
  }

  private NewsKeyword parseKeyword(JsonNode keywordNode) {
    return new NewsKeyword(
        KeywordCategory.valueOf(keywordNode.path("category").asText()),
        keywordNode.path("keyword").asText().trim(),
        keywordNode.path("definition").asText(),
        keywordNode.path("context").asText(),
        keywordNode.path("watchPoint").asText());
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
