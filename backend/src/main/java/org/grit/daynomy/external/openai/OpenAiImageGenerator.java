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
  private static final String IMAGE_CONTENT_GUIDELINES =
      """
      Start by composing an entirely unoccupied scene with no people. Treat a people-free image as
      the default and strongest preference. A person may appear only as a strict exception when
      removing all people would make the central event itself visually incomprehensible because the
      event inherently depends on a human action. A workplace or industry setting, including an
      office, factory, laboratory, hospital, store, construction site, or market, is never by itself
      a reason to include a worker. Do not add people for scale, atmosphere, realism, or visual
      interest. If objects, machinery, products, documents, buildings, landscapes, or materials can
      carry the story, show no people. For articles about facilities, production, contracts, supply,
      investment, earnings, logistics, technology, or research, prefer relevant environments and
      objects alone. If and only if a person is indispensable, include exactly one anonymous,
      non-identifiable person, shown from behind or with their face fully obscured. Never include
      crowds, groups, background figures, silhouettes, reflections of people, screens depicting
      people, portraits, visible faces, or stray body parts.
      Do not add visible writing by default. Include background text or numerals only when they
      naturally belong to the setting, such as an exchange board, and keep them secondary. Any
      visible glyphs must look clean and correctly formed, never malformed, scrambled, misspelled,
      or like gibberish. Preserve the language of each text element: render Korean content in Korean
      and English content in English. Do not translate Korean names, labels, or phrases into English,
      or English names, labels, or phrases into Korean. Mixed languages are acceptable when natural
      to the setting. Never invent factual company names, ticker symbols, prices, dates, headlines,
      or claims. Do not copy the article title or context into the image. Render readable text or
      values only when exact content is explicitly supplied for rendering. Otherwise, keep necessary
      background displays softly out of focus so no inaccurate content is legible; omit the text if
      it cannot be rendered cleanly. Leave nonessential writing surfaces blank.
      """;

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
        %s
        Use abstract market, document, and business imagery suitable for a Korean financial news app.

        News title: %s
        """
        .formatted(IMAGE_CONTENT_GUIDELINES, title);
  }

  private String economicNewsImagePrompt(String title, String content, Category category) {
    return """
        Create a realistic editorial cover photograph for a Korean economic news article.
        Show a believable real-world setting directly connected to the industry's or market's
        central issue. Use objects, machinery, buildings, landscapes, or materials to carry the
        story instead of generic stock charts or abstract finance symbols. First compose the scene
        without people and apply the strict exception in the people guidelines below only when the
        central event cannot be understood otherwise.
        %s

        Style: documentary press photography with natural camera realism, authentic materials, realistic lighting, restrained colors, and subtle grain. Avoid glossy 3D rendering and conceptual illustration.
        Composition: wide horizontal landscape, safe to crop to a 16:9 banner. Keep the main subject toward the right third and leave uncluttered negative space on the left for a headline overlay.
        Accuracy: this is an illustrative cover, not evidence of the reported event. Do not invent
        or imply a specific unverified company facility or event.

        Category: %s
        Headline: %s
        Article context: %s
        """
        .formatted(IMAGE_CONTENT_GUIDELINES, category.name(), title, content);
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
