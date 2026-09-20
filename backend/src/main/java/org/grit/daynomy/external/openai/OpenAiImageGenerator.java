package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
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
        title,
        economicNewsImagePrompt(title, content, category),
        ECONOMIC_NEWS_IMAGE_SIZE);
  }

  private byte[] generateNewsImage(String title, String prompt, String size) {
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
              .body(requestBody(prompt, size))
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
    return """
        Create a clean editorial finance news thumbnail.
        Do not include text, logos, watermarks, company logos, people, or stock ticker symbols.
        Use abstract market, document, and business imagery suitable for a Korean financial news app.

        News title: %s
        """
        .formatted(title);
  }

  private String economicNewsImagePrompt(String title, String content, Category category) {
    return """
        Create a realistic editorial cover photograph for a Korean economic news article.
        Show a believable real-world setting directly connected to the industry's or market's central issue. Use concrete contextual details instead of generic stock charts or abstract finance symbols. If a person helps explain the scene, show one anonymous person from behind or with their face obscured.

        Style: documentary press photography with natural camera realism, authentic materials, realistic lighting, restrained colors, and subtle grain. Avoid glossy 3D rendering and conceptual illustration.
        Composition: wide horizontal landscape, safe to crop to a 16:9 banner. Keep the main subject toward the right third and leave uncluttered negative space on the left for a headline overlay.
        Accuracy: this is an illustrative cover, not evidence of the reported event. Do not invent or imply a specific unverified person, company facility, or event.
        Do not include readable text, labels, logos, company marks, watermarks, ticker symbols, fabricated interface elements, or sensational graphics.

        Category: %s
        Headline: %s
        Article context: %s
        """
        .formatted(category.name(), title, content);
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
