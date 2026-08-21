package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.news.ai.GeneratedNews;
import org.grit.daynomy.news.ai.NewsPrompt;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OpenAiNewsGenerator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final OpenAiProperties openAiProperties;
  private final RestClient restClient;

  public OpenAiNewsGenerator(OpenAiProperties openAiProperties) {
    this.openAiProperties = openAiProperties;
    this.restClient = RestClient.create(openAiProperties.baseUrl());
  }

  public GeneratedNews generate(NewsPrompt prompt) {
    try {
      log.info(
          "Requesting OpenAI news generation: model={}, source={}, externalId={}",
          openAiProperties.model(),
          prompt.source(),
          prompt.externalId());
      String response =
          restClient
              .post()
              .uri("/responses")
              .header("Authorization", "Bearer " + openAiProperties.apiKey())
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody(prompt.prompt()))
              .retrieve()
              .body(String.class);

      GeneratedNews generatedNews = parseGeneratedNews(response);
      log.info(
          "Received OpenAI generated news: source={}, externalId={}, title={}",
          prompt.source(),
          prompt.externalId(),
          generatedNews.title());
      return generatedNews;
    } catch (HttpStatusCodeException exception) {
      log.warn(
          "OpenAI news generation request failed: status={}, body={}, source={}, externalId={}",
          exception.getStatusCode(),
          exception.getResponseBodyAsString(),
          prompt.source(),
          prompt.externalId());
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    } catch (RestClientException exception) {
      log.warn(
          "OpenAI news generation request failed: message={}, source={}, externalId={}",
          exception.getMessage(),
          prompt.source(),
          prompt.externalId());
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }
  }

  private Map<String, Object> requestBody(String prompt) {
    return Map.of(
        "model",
        openAiProperties.model(),
        "input",
        prompt,
        "text",
        Map.of("format", responseFormat()));
  }

  private Map<String, Object> responseFormat() {
    return Map.of(
        "type",
        "json_schema",
        "name",
        "news_article",
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
                "title",
                Map.of("type", "string"),
                "description",
                Map.of("type", "string"),
                "content",
                Map.of("type", "string")),
            "required",
            List.of("title", "description", "content")));
  }

  private GeneratedNews parseGeneratedNews(String response) {
    try {
      String outputText = extractOutputText(OBJECT_MAPPER.readTree(response));
      return OBJECT_MAPPER.readValue(outputText, GeneratedNews.class);
    } catch (Exception exception) {
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }
  }

  private String extractOutputText(JsonNode response) {
    JsonNode output = response.path("output");
    for (JsonNode item : output) {
      for (JsonNode content : item.path("content")) {
        if ("output_text".equals(content.path("type").asText())) {
          String text = content.path("text").asText();
          if (!text.isBlank()) {
            return text;
          }
        }
      }
    }

    throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
  }
}
