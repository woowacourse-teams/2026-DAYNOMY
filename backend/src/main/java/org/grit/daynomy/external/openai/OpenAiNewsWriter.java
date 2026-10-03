package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OpenAiNewsWriter {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int MAX_ATTEMPTS = 3;
  private static final int MIN_CONTENT_LENGTH = 1_000;
  private static final int MAX_CONTENT_LENGTH = 1_800;
  private static final int MIN_CONTENT_PARAGRAPHS = 5;
  private static final int MAX_CONTENT_PARAGRAPHS = 9;
  private static final String PROMPT = OpenAiPromptLoader.load("prompts/news-writer.txt");
  private static final String RETRY_INSTRUCTION =
      """

      이전 본문 묶음이 형식 검증에 실패했다. 조사 자료에 없는 사실을 추가하지 말고 전체 기사를 처음부터 다시 작성하라. 기사 수를 유지하고, 각 본문은 공백 포함 1,000~1,800자, 5~9개 문단, 해라체를 정확히 지키며 시장 분석·전망·투자 의견을 제외하라.
      """;

  private final OpenAiProperties openAiProperties;
  private final RestClient restClient;

  public OpenAiNewsWriter(OpenAiProperties openAiProperties) {
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

  public List<GeneratedEconomicNews> write(List<EconomicNewsResearch> research) {
    log.atInfo()
        .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_REQUESTED.code())
        .addKeyValue("api", "OpenAI")
        .addKeyValue("operation", "economic-news-writing")
        .addKeyValue("model", openAiProperties.economyNewsModel())
        .addKeyValue("articleCount", research.size())
        .log(LogEvent.AI_NEWS_GENERATION_REQUESTED.message());

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return parseContents(requestContent(research, attempt), research);
      } catch (InvalidWritingException exception) {
        log.atWarn()
            .addKeyValue("event", LogEvent.AI_NEWS_VALIDATION_FAILED.code())
            .addKeyValue("api", "OpenAI")
            .addKeyValue("operation", "economic-news-writing")
            .addKeyValue("attempt", attempt)
            .addKeyValue("reason", exception.getMessage())
            .log(LogEvent.AI_NEWS_VALIDATION_FAILED.message());
        if (attempt == MAX_ATTEMPTS) {
          throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
        }
      } catch (HttpStatusCodeException exception) {
        log.atWarn()
            .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_FAILED.code())
            .addKeyValue("api", "OpenAI")
            .addKeyValue("operation", "economic-news-writing")
            .addKeyValue("httpStatus", exception.getStatusCode().value())
            .log(LogEvent.AI_NEWS_GENERATION_FAILED.message());
        throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
      } catch (RestClientException exception) {
        log.atWarn()
            .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_FAILED.code())
            .addKeyValue("api", "OpenAI")
            .addKeyValue("operation", "economic-news-writing")
            .addKeyValue("errorType", exception.getClass().getSimpleName())
            .log(LogEvent.AI_NEWS_GENERATION_FAILED.message());
        throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
      }
    }
    throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
  }

  private String requestContent(List<EconomicNewsResearch> research, int attempt) {
    String instruction = PROMPT + "\n\n조사 자료 묶음:\n" + researchJson(research);
    if (attempt > 1) {
      instruction += RETRY_INSTRUCTION;
    }
    return restClient
        .post()
        .uri("/responses")
        .header("Authorization", "Bearer " + openAiProperties.apiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            Map.of(
                "model",
                openAiProperties.economyNewsModel(),
                "input",
                instruction,
                "text",
                Map.of("format", responseFormat())))
        .retrieve()
        .body(String.class);
  }

  private String researchJson(List<EconomicNewsResearch> research) {
    try {
      List<Map<String, Object>> articles =
          research.stream()
              .map(
                  item ->
                      Map.of(
                          "title",
                          item.title(),
                          "date",
                          item.referenceDate().toString(),
                          "category",
                          item.category().name(),
                          "facts",
                          item.facts()))
              .toList();
      return OBJECT_MAPPER.writeValueAsString(Map.of("articles", articles));
    } catch (Exception exception) {
      throw new IllegalStateException("Research data could not be serialized.", exception);
    }
  }

  private Map<String, Object> responseFormat() {
    return Map.of(
        "type",
        "json_schema",
        "name",
        "economic_news_articles",
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
                "articles",
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
                        Map.of("content", Map.of("type", "string")),
                        "required",
                        List.of("content")))),
            "required",
            List.of("articles")));
  }

  private List<GeneratedEconomicNews> parseContents(
      String response, List<EconomicNewsResearch> research) {
    try {
      JsonNode responseJson = OBJECT_MAPPER.readTree(response);
      JsonNode articles = OBJECT_MAPPER.readTree(extractOutputText(responseJson)).path("articles");
      if (!articles.isArray() || articles.size() != research.size()) {
        throw new IllegalArgumentException(
            "Writer must return one article for each research item.");
      }

      List<GeneratedEconomicNews> generatedNews = new ArrayList<>();
      for (int index = 0; index < articles.size(); index++) {
        String content = articles.get(index).path("content").asText().strip();
        validateContent(content);
        EconomicNewsResearch item = research.get(index);
        generatedNews.add(
            new GeneratedEconomicNews(item.title(), content, item.category(), item.sources()));
      }
      return List.copyOf(generatedNews);
    } catch (Exception exception) {
      throw new InvalidWritingException(exception.getMessage(), exception);
    }
  }

  private void validateContent(String content) {
    int contentLength = content.codePointCount(0, content.length());
    int paragraphCount = content.split("\\R\\s*\\R").length;
    if (content.isBlank()
        || contentLength < MIN_CONTENT_LENGTH
        || contentLength > MAX_CONTENT_LENGTH) {
      throw new IllegalArgumentException(
          "Content length must be between "
              + MIN_CONTENT_LENGTH
              + " and "
              + MAX_CONTENT_LENGTH
              + " characters.");
    }
    if (paragraphCount < MIN_CONTENT_PARAGRAPHS || paragraphCount > MAX_CONTENT_PARAGRAPHS) {
      throw new IllegalArgumentException(
          "Content paragraph count must be between "
              + MIN_CONTENT_PARAGRAPHS
              + " and "
              + MAX_CONTENT_PARAGRAPHS
              + ".");
    }
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

  private int toMillis(java.time.Duration timeout) {
    return Math.toIntExact(timeout.toMillis());
  }

  private static final class InvalidWritingException extends RuntimeException {

    private InvalidWritingException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
