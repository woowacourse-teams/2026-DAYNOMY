package org.grit.daynomy.keyword.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OpenAiKeywordClient implements KeywordAiClient {

  private static final String KEYWORD_EXTRACTION_PROMPT =
      """
      뉴스 본문에서 투자자가 이해해야 할 핵심 키워드를 3개에서 5개 추출하세요.
      각 키워드는 한국어 명사구로 작성하고, 설명은 뉴스 본문 맥락에서 1문장으로 작성하세요.
      각 키워드는 다음 기준에 따라 하나의 카테고리로 분류하세요.
      %s
      본문에 없는 내용을 추론해 만들지 마세요.
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

    String response =
        restClient
            .post()
            .uri("/v1/responses")
            .contentType(MediaType.APPLICATION_JSON)
            .headers(headers -> headers.setBearerAuth(apiKey))
            .body(createRequest(newsContent))
            .retrieve()
            .body(String.class);

    return parseKeywords(response);
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
    keywordProperties.put("keyword", Map.of("type", "string"));
    keywordProperties.put("description", Map.of("type", "string"));

    Map<String, Object> keywordItem = new LinkedHashMap<>();
    keywordItem.put("type", "object");
    keywordItem.put("additionalProperties", false);
    keywordItem.put("required", List.of("category", "keyword", "description"));
    keywordItem.put("properties", keywordProperties);

    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put(
        "keywords", Map.of("type", "array", "minItems", 1, "maxItems", 5, "items", keywordItem));

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

  private List<NewsKeyword> parseKeywords(String response) {
    String outputText = extractOutputText(response);
    try {
      JsonNode keywordsNode = objectMapper.readTree(outputText).path("keywords");
      if (!keywordsNode.isArray()) {
        throw new IllegalStateException("OpenAI keyword response must contain keywords array.");
      }

      return keywordsNode
          .valueStream()
          .map(
              node ->
                  new NewsKeyword(
                      KeywordCategory.valueOf(node.path("category").asText()),
                      node.path("keyword").asText(),
                      node.path("description").asText()))
          .toList();
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Failed to parse OpenAI keyword response.", exception);
    }
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
