package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private static final int MAX_ATTEMPTS = 2;
  private static final int MIN_CONTENT_LENGTH = 800;
  private static final int MAX_CONTENT_LENGTH = 1_500;
  private static final int MIN_CONTENT_PARAGRAPHS = 4;
  private static final int MAX_CONTENT_PARAGRAPHS = 6;
  private static final int MAX_OUTPUT_TOKENS = 12_000;
  private static final String PROMPT = OpenAiPromptLoader.load("prompts/news-writer.txt");
  private static final String RETRY_INSTRUCTION =
      """

      이전 요청에서 전달한 본문만 수정하라. 조사 자료에 없는 사실을 추가하지 말고, 각 본문은 공백 포함 900~1,100자를 목표로 800~1,500자 범위를 반드시 지켜라. 반환 전 각 본문의 글자 수를 확인하고, 4~6개 문단과 해라체를 정확히 지키며 시장 분석·전망·투자 의견을 제외하라.
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

    List<GeneratedEconomicNews> generatedNews = new ArrayList<>();
    List<EconomicNewsResearch> pendingResearch = research;
    List<Integer> pendingIndexes = new ArrayList<>();
    for (int index = 0; index < research.size(); index++) {
      generatedNews.add(null);
      pendingIndexes.add(index);
    }
    String retryReason = "";

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        ParsedWriting parsedWriting =
            parseContents(
                requestContent(pendingResearch, attempt, retryReason), pendingResearch);
        for (Map.Entry<Integer, GeneratedEconomicNews> entry :
            parsedWriting.generatedNews().entrySet()) {
          generatedNews.set(pendingIndexes.get(entry.getKey()), entry.getValue());
        }
        if (parsedWriting.failures().isEmpty()) {
          List<GeneratedEconomicNews> result = List.copyOf(generatedNews);
          log.atInfo()
              .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_COMPLETED.code())
              .addKeyValue("api", "OpenAI")
              .addKeyValue("operation", "economic-news-writing")
              .addKeyValue("attempt", attempt)
              .addKeyValue("articleCount", result.size())
              .log(LogEvent.AI_NEWS_GENERATION_COMPLETED.message());
          return result;
        }

        logValidationFailures(parsedWriting.failures(), attempt);
        if (attempt == MAX_ATTEMPTS) {
          return validArticlesOrFail(generatedNews, attempt);
        }
        List<Integer> currentPendingIndexes = pendingIndexes;
        retryReason =
            parsedWriting.failures().stream()
                .map(ValidationFailure::reason)
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
        pendingIndexes =
            parsedWriting.failures().stream()
                .map(failure -> currentPendingIndexes.get(failure.index()))
                .toList();
        pendingResearch =
            parsedWriting.failures().stream().map(ValidationFailure::research).toList();
      } catch (InvalidWritingException exception) {
        log.atWarn()
            .addKeyValue("event", LogEvent.AI_NEWS_VALIDATION_FAILED.code())
            .addKeyValue("api", "OpenAI")
            .addKeyValue("operation", "economic-news-writing")
            .addKeyValue("attempt", attempt)
            .addKeyValue("errorType", exception.getClass().getSimpleName())
            .log(LogEvent.AI_NEWS_VALIDATION_FAILED.message());
        if (attempt == MAX_ATTEMPTS) {
          return validArticlesOrFail(generatedNews, attempt);
        }
        retryReason = exception.getMessage();
      } catch (HttpStatusCodeException exception) {
        log.atError()
            .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_FAILED.code())
            .addKeyValue("api", "OpenAI")
            .addKeyValue("operation", "economic-news-writing")
            .addKeyValue("httpStatus", exception.getStatusCode().value())
            .log(LogEvent.AI_NEWS_GENERATION_FAILED.message());
        throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
      } catch (RestClientException exception) {
        log.atError()
            .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_FAILED.code())
            .addKeyValue("api", "OpenAI")
            .addKeyValue("operation", "economic-news-writing")
            .addKeyValue("errorType", exception.getClass().getSimpleName())
            .log(LogEvent.AI_NEWS_GENERATION_FAILED.message());
        throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
      }
    }
    return validArticlesOrFail(generatedNews, MAX_ATTEMPTS);
  }

  private String requestContent(
      List<EconomicNewsResearch> research, int attempt, String validationFailureReason) {
    String instruction = PROMPT + "\n\n조사 자료 묶음:\n" + researchJson(research);
    if (attempt > 1) {
      instruction += RETRY_INSTRUCTION + "\n이전 검증 결과: " + validationFailureReason;
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
                "max_output_tokens",
                MAX_OUTPUT_TOKENS,
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

  private ParsedWriting parseContents(
      String response, List<EconomicNewsResearch> research) {
    try {
      JsonNode responseJson = OBJECT_MAPPER.readTree(response);
      JsonNode articles = OBJECT_MAPPER.readTree(extractOutputText(responseJson)).path("articles");
      if (!articles.isArray() || articles.size() != research.size()) {
        throw new IllegalArgumentException(
            "Writer must return one article for each research item.");
      }

      Map<Integer, GeneratedEconomicNews> generatedNews = new HashMap<>();
      List<ValidationFailure> failures = new ArrayList<>();
      for (int index = 0; index < articles.size(); index++) {
        String content = articles.get(index).path("content").asText().strip();
        EconomicNewsResearch item = research.get(index);
        try {
          validateContent(content);
          generatedNews.put(
              index,
              new GeneratedEconomicNews(item.title(), content, item.category(), item.sources()));
        } catch (IllegalArgumentException exception) {
          failures.add(new ValidationFailure(index, item, exception.getMessage()));
        }
      }
      return new ParsedWriting(generatedNews, failures);
    } catch (Exception exception) {
      throw new InvalidWritingException(exception.getMessage(), exception);
    }
  }

  private void validateContent(String content) {
    if (content.isBlank()) {
      throw new IllegalArgumentException("Content must not be blank.");
    }

    int contentLength = content.codePointCount(0, content.length());
    long paragraphCount =
        Arrays.stream(content.split("\\R\\s*\\R"))
            .filter(paragraph -> !paragraph.isBlank())
            .count();
    if (contentLength < MIN_CONTENT_LENGTH || contentLength > MAX_CONTENT_LENGTH) {
      throw new IllegalArgumentException(
          "Content length was "
              + contentLength
              + "; expected between "
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

  private void logValidationFailures(List<ValidationFailure> failures, int attempt) {
    failures.forEach(
        failure ->
            log.atWarn()
                .addKeyValue("event", LogEvent.AI_NEWS_VALIDATION_FAILED.code())
                .addKeyValue("api", "OpenAI")
                .addKeyValue("operation", "economic-news-writing")
                .addKeyValue("attempt", attempt)
                .addKeyValue("articleIndex", failure.index())
                .addKeyValue("reason", failure.reason())
                .log(LogEvent.AI_NEWS_VALIDATION_FAILED.message()));
  }

  private List<GeneratedEconomicNews> validArticlesOrFail(
      List<GeneratedEconomicNews> generatedNews, int attempt) {
    List<GeneratedEconomicNews> validArticles =
        generatedNews.stream().filter(Objects::nonNull).toList();
    if (validArticles.isEmpty()) {
      log.atError()
          .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_FAILED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "economic-news-writing")
          .addKeyValue("attempt", attempt)
          .addKeyValue("errorType", "CONTENT_VALIDATION_FAILED")
          .log(LogEvent.AI_NEWS_GENERATION_FAILED.message());
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }
    log.atInfo()
        .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_COMPLETED.code())
        .addKeyValue("api", "OpenAI")
        .addKeyValue("operation", "economic-news-writing")
        .addKeyValue("attempt", attempt)
        .addKeyValue("articleCount", validArticles.size())
        .addKeyValue("partial", true)
        .log(LogEvent.AI_NEWS_GENERATION_COMPLETED.message());
    return validArticles;
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

  private record ParsedWriting(
      Map<Integer, GeneratedEconomicNews> generatedNews, List<ValidationFailure> failures) {}

  private record ValidationFailure(int index, EconomicNewsResearch research, String reason) {}

  private static final class InvalidWritingException extends RuntimeException {

    private InvalidWritingException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
