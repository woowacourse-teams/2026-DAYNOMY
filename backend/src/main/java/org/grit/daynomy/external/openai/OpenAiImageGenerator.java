package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.news.domain.Category;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OpenAiImageGenerator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String IMAGE_SIZE = "1024x1024";
  private static final String ECONOMIC_NEWS_IMAGE_SIZE = "1536x1024";
  private static final String IMAGE_QUALITY = "low";
  private static final String IMAGE_FORMAT = "webp";
  private static final String IMAGE_PROMPT = OpenAiPromptLoader.load("prompts/image-generator.txt");

  private final OpenAiProperties openAiProperties;
  private final RestClient restClient;

  public OpenAiImageGenerator(OpenAiProperties openAiProperties) {
    this.openAiProperties = openAiProperties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(toMillis(openAiProperties.connectTimeout()));
    requestFactory.setReadTimeout(toMillis(openAiProperties.readTimeout()));
    this.restClient =
        RestClient.builder()
            .baseUrl(openAiProperties.baseUrl())
            .requestFactory(requestFactory)
            .build();
  }

  public byte[] generateNewsImage(String title) {
    return generateNewsImage(title, imagePrompt(title), IMAGE_SIZE);
  }

  public byte[] generateEconomicNewsImage(String title, String content, Category category) {
    return generateNewsImage(
        title, economicNewsImagePrompt(title, content, category), ECONOMIC_NEWS_IMAGE_SIZE);
  }

  private byte[] generateNewsImage(String title, String prompt, String size) {
    try {
      log.atInfo()
          .addKeyValue("event", LogEvent.AI_IMAGE_GENERATION_REQUESTED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "image-generation")
          .addKeyValue("model", openAiProperties.imageModel())
          .addKeyValue("title", title)
          .log(LogEvent.AI_IMAGE_GENERATION_REQUESTED.message());
      String response =
          restClient
              .post()
              .uri("/images/generations")
              .header("Authorization", "Bearer " + openAiProperties.apiKey())
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody(prompt, size))
              .retrieve()
              .body(String.class);

      byte[] image = Base64.getDecoder().decode(extractImage(response));
      log.atInfo()
          .addKeyValue("event", LogEvent.AI_IMAGE_GENERATION_COMPLETED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "image-generation")
          .addKeyValue("title", title)
          .log(LogEvent.AI_IMAGE_GENERATION_COMPLETED.message());
      return image;
    } catch (HttpStatusCodeException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.AI_IMAGE_GENERATION_FAILED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "image-generation")
          .addKeyValue("httpStatus", exception.getStatusCode().value())
          .addKeyValue("title", title)
          .log(LogEvent.AI_IMAGE_GENERATION_FAILED.message());
      throw new BusinessException(ExternalErrorCode.AI_IMAGE_GENERATION_FAILED);
    } catch (RestClientException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.AI_IMAGE_GENERATION_FAILED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "image-generation")
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .addKeyValue("title", title)
          .log(LogEvent.AI_IMAGE_GENERATION_FAILED.message());
      throw new BusinessException(ExternalErrorCode.AI_IMAGE_GENERATION_FAILED);
    } catch (IllegalArgumentException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.AI_IMAGE_GENERATION_FAILED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "image-generation")
          .addKeyValue("errorType", "InvalidBase64")
          .addKeyValue("title", title)
          .log(LogEvent.AI_IMAGE_GENERATION_FAILED.message());
      throw new BusinessException(ExternalErrorCode.AI_IMAGE_GENERATION_FAILED);
    }
  }

  private Map<String, Object> requestBody(String prompt, String size) {
    return Map.of(
        "model",
        openAiProperties.imageModel(),
        "prompt",
        prompt,
        "n",
        1,
        "size",
        size,
        "quality",
        IMAGE_QUALITY,
        "output_format",
        IMAGE_FORMAT);
  }

  private String imagePrompt(String title) {
    return IMAGE_PROMPT.formatted("GENERAL_FINANCE", title, "");
  }

  private String economicNewsImagePrompt(String title, String content, Category category) {
    return IMAGE_PROMPT.formatted(category.name(), title, content);
  }

  private String extractImage(String response) {
    try {
      JsonNode data = OBJECT_MAPPER.readTree(response).path("data");
      for (JsonNode item : data) {
        String image = item.path("b64_json").asText();
        if (!image.isBlank()) {
          return image;
        }
      }
    } catch (Exception exception) {
      throw new BusinessException(ExternalErrorCode.AI_IMAGE_GENERATION_FAILED);
    }

    throw new BusinessException(ExternalErrorCode.AI_IMAGE_GENERATION_FAILED);
  }

  private int toMillis(java.time.Duration timeout) {
    return Math.toIntExact(timeout.toMillis());
  }
}
