package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
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
  private static final String IMAGE_QUALITY = "low";
  private static final String IMAGE_FORMAT = "webp";

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

  public byte[] generateNewsImage(String title, String description) {
    try {
      log.info(
          "Requesting OpenAI image generation: model={}, title={}",
          openAiProperties.imageModel(),
          title);
      String response =
          restClient
              .post()
              .uri("/images/generations")
              .header("Authorization", "Bearer " + openAiProperties.apiKey())
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody(imagePrompt(title, description)))
              .retrieve()
              .body(String.class);

      byte[] image = Base64.getDecoder().decode(extractImage(response));
      log.info("Received OpenAI generated image: title={}", title);
      return image;
    } catch (HttpStatusCodeException exception) {
      log.warn(
          "OpenAI image generation request failed: status={}, body={}, title={}",
          exception.getStatusCode(),
          exception.getResponseBodyAsString(),
          title);
      throw new BusinessException(ExternalErrorCode.AI_IMAGE_GENERATION_FAILED);
    } catch (RestClientException exception) {
      log.warn(
          "OpenAI image generation request failed: message={}, title={}",
          exception.getMessage(),
          title);
      throw new BusinessException(ExternalErrorCode.AI_IMAGE_GENERATION_FAILED);
    } catch (IllegalArgumentException exception) {
      log.warn("OpenAI image response contained invalid Base64 data: title={}", title);
      throw new BusinessException(ExternalErrorCode.AI_IMAGE_GENERATION_FAILED);
    }
  }

  private Map<String, Object> requestBody(String prompt) {
    return Map.of(
        "model",
        openAiProperties.imageModel(),
        "prompt",
        prompt,
        "n",
        1,
        "size",
        IMAGE_SIZE,
        "quality",
        IMAGE_QUALITY,
        "output_format",
        IMAGE_FORMAT);
  }

  private String imagePrompt(String title, String description) {
    return """
        Create a clean editorial finance news thumbnail.
        Do not include text, logos, watermarks, company logos, people, or stock ticker symbols.
        Use abstract market, document, and business imagery suitable for a Korean financial news app.

        News title: %s
        News summary: %s
        """
        .formatted(title, description);
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
