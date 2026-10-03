package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class OpenAiNewsResearcher {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int MIN_ARTICLE_COUNT = 4;
  private static final int MAX_ARTICLE_COUNT = 5;
  private static final int MAX_ATTEMPTS = 3;
  private static final int RECENT_DAYS = 3;
  private static final String PROMPT = OpenAiPromptLoader.load("prompts/news-researcher.txt");
  private static final String RETRY_INSTRUCTION =
      """

      이전 조사 결과가 최신성, 독립 출처, 날짜 또는 기사 수 검증에 실패했다. 처음부터 다시 검색하라. 최근 3일 이내의 사건만 선택하고, 한국 출처를 우선하며, 이슈마다 독립된 출처를 2개 이상 확인하라. 본문이나 시장 분석은 작성하지 말고 검증된 facts만 반환하라.
      """;

  private final OpenAiProperties openAiProperties;
  private final RestClient restClient;

  public OpenAiNewsResearcher(OpenAiProperties openAiProperties) {
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

  public List<EconomicNewsResearch> researchEconomicNews() {
    log.atInfo()
        .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_REQUESTED.code())
        .addKeyValue("api", "OpenAI")
        .addKeyValue("operation", "economic-news-research")
        .addKeyValue("model", openAiProperties.economyNewsModel())
        .log(LogEvent.AI_NEWS_GENERATION_REQUESTED.message());

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        return parseResearch(requestResearch(attempt));
      } catch (InvalidResearchException exception) {
        log.atWarn()
            .addKeyValue("event", LogEvent.AI_NEWS_VALIDATION_FAILED.code())
            .addKeyValue("api", "OpenAI")
            .addKeyValue("operation", "economic-news-research")
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
            .addKeyValue("operation", "economic-news-research")
            .addKeyValue("httpStatus", exception.getStatusCode().value())
            .log(LogEvent.AI_NEWS_GENERATION_FAILED.message());
        throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
      } catch (RestClientException exception) {
        log.atWarn()
            .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_FAILED.code())
            .addKeyValue("api", "OpenAI")
            .addKeyValue("operation", "economic-news-research")
            .addKeyValue("errorType", exception.getClass().getSimpleName())
            .log(LogEvent.AI_NEWS_GENERATION_FAILED.message());
        throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
      }
    }
    throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
  }

  private String requestResearch(int attempt) {
    String instruction =
        PROMPT.formatted(LocalDate.now(ZoneId.of("Asia/Seoul")))
            + (attempt == 1 ? "" : RETRY_INSTRUCTION);
    return restClient
        .post()
        .uri("/responses")
        .header("Authorization", "Bearer " + openAiProperties.apiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(requestBody(instruction))
        .retrieve()
        .body(String.class);
  }

  private Map<String, Object> requestBody(String instruction) {
    return Map.of(
        "model",
        openAiProperties.economyNewsModel(),
        "tools",
        List.of(Map.of("type", "web_search")),
        "tool_choice",
        "required",
        "include",
        List.of("web_search_call.action.sources"),
        "input",
        instruction,
        "text",
        Map.of("format", responseFormat()));
  }

  private Map<String, Object> responseFormat() {
    return Map.of(
        "type",
        "json_schema",
        "name",
        "economic_news_research",
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
                    "minItems",
                    MIN_ARTICLE_COUNT,
                    "maxItems",
                    MAX_ARTICLE_COUNT,
                    "items",
                    Map.of(
                        "type",
                        "object",
                        "additionalProperties",
                        false,
                        "properties",
                        Map.of(
                            "title",
                            Map.of("type", "string"),
                            "date",
                            Map.of("type", "string"),
                            "facts",
                            Map.of(
                                "type", "array", "minItems", 3, "items", Map.of("type", "string")),
                            "category",
                            Map.of(
                                "type", "string", "enum", List.of("REAL_ESTATE", "STOCK", "ETF")),
                            "sourceUrls",
                            Map.of(
                                "type",
                                "array",
                                "minItems",
                                2,
                                "maxItems",
                                4,
                                "items",
                                Map.of("type", "string"))),
                        "required",
                        List.of("title", "date", "facts", "category", "sourceUrls")))),
            "required",
            List.of("articles")));
  }

  private List<EconomicNewsResearch> parseResearch(String response) {
    try {
      JsonNode responseJson = OBJECT_MAPPER.readTree(response);
      JsonNode articles = OBJECT_MAPPER.readTree(extractOutputText(responseJson)).path("articles");
      if (!articles.isArray()
          || articles.size() < MIN_ARTICLE_COUNT
          || articles.size() > MAX_ARTICLE_COUNT) {
        throw new IllegalArgumentException("Expected four or five economic news research items.");
      }

      Map<String, NewsSourceInfo> availableSources = new LinkedHashMap<>();
      extractSources(responseJson).forEach(source -> availableSources.put(source.url(), source));

      List<EconomicNewsResearch> research = new ArrayList<>();
      int realEstateCount = 0;
      LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
      LocalDate firstRecentDate = today.minusDays(RECENT_DAYS - 1L);
      for (JsonNode article : articles) {
        String title = article.path("title").asText();
        LocalDate date = LocalDate.parse(article.path("date").asText());
        Category category = Category.valueOf(article.path("category").asText());
        List<String> facts = new ArrayList<>();
        for (JsonNode fact : article.path("facts")) {
          if (fact.isTextual() && !fact.asText().isBlank()) {
            facts.add(fact.asText());
          }
        }
        Map<String, NewsSourceInfo> articleSources = new LinkedHashMap<>();
        for (JsonNode sourceUrl : article.path("sourceUrls")) {
          NewsSourceInfo source = availableSources.get(sourceUrl.asText());
          if (source != null) {
            articleSources.putIfAbsent(source.url(), source);
          }
        }
        if (category == Category.REAL_ESTATE) {
          realEstateCount++;
        }
        if (title.isBlank()
            || date.isBefore(firstRecentDate)
            || date.isAfter(today)
            || facts.size() < 3
            || articleSources.size() < 2) {
          throw new IllegalArgumentException("Research item failed fact validation.");
        }
        research.add(
            new EconomicNewsResearch(
                title, date, category, facts, List.copyOf(articleSources.values())));
      }
      if (articles.size() == MAX_ARTICLE_COUNT && realEstateCount == 0) {
        throw new IllegalArgumentException(
            "Five economic news research items must include real estate.");
      }
      return List.copyOf(research);
    } catch (Exception exception) {
      throw new InvalidResearchException(exception.getMessage(), exception);
    }
  }

  private List<NewsSourceInfo> extractSources(JsonNode response) {
    Map<String, NewsSourceInfo> sources = new LinkedHashMap<>();
    for (JsonNode item : response.path("output")) {
      for (JsonNode content : item.path("content")) {
        for (JsonNode annotation : content.path("annotations")) {
          if ("url_citation".equals(annotation.path("type").asText())) {
            addSource(sources, annotation.path("url").asText(), annotation.path("title").asText());
          }
        }
      }
      for (JsonNode source : item.path("action").path("sources")) {
        addSource(sources, source.path("url").asText(), source.path("title").asText());
      }
    }
    return List.copyOf(sources.values());
  }

  private void addSource(Map<String, NewsSourceInfo> sources, String url, String title) {
    if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
      return;
    }
    String name = title == null ? "" : title.strip();
    if (name.isBlank()) {
      try {
        name = URI.create(url).getHost();
      } catch (IllegalArgumentException ignored) {
        name = url;
      }
    }
    sources.putIfAbsent(url, new NewsSourceInfo(name == null || name.isBlank() ? url : name, url));
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

  private static final class InvalidResearchException extends RuntimeException {

    private InvalidResearchException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
