package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
import org.grit.daynomy.news.ai.GeneratedNews;
import org.grit.daynomy.news.ai.NewsPrompt;
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
public class OpenAiNewsGenerator {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String ECONOMY_NEWS_INSTRUCTION =
      """
      오늘은 %s(한국 기준)입니다. 웹 검색으로 최근 국내외 경제 이슈를 찾아 한국 독자에게 의미 있는 이슈 한 가지를 선정하고, 한국어 뉴스 초안을 작성하세요.

      경제 전문 매체와 주요 언론의 최신 보도를 우선 확인하고, 게시일과 실제 사건 발생일을 구분하세요. 공공기관 통계나 보도자료만을 주된 소재로 삼지 말고, 언론 보도를 통해 실제로 주목받는 이슈를 고르세요. 중요한 사실과 수치는 검색 결과로 확인되는 범위에서만 쓰고, 기사 문장을 그대로 옮기지 말고 여러 출처의 내용을 종합해 새롭게 서술하세요.

      본문은 객관적인 보도 문체로 2~5개 문단으로 작성하세요. 투자 판단이나 매수·매도 권유는 하지 마세요. 제목과 본문 외에 JSON 스키마의 category도 반환하세요. 카테고리는 다음 기준 중 가장 가까운 하나를 고르세요.
      - REAL_ESTATE: 주택, 부동산, 전월세, 주택 대출 중심 이슈
      - ETF: 상장지수펀드 상품이나 ETF 시장 중심 이슈
      - STOCK: 주식시장, 기업, 금리, 환율, 물가, 고용, 무역 등 그 밖의 경제 이슈

      근거를 확인할 수 있는 이슈가 없으면 추측해 만들지 말고 생성에 실패하세요.
      """;
  private static final Pattern BULLET_LINE_PATTERN =
      Pattern.compile("(?m)^\\s*(?:[-*•·]|\\d+[.)])\\s+");
  private static final Pattern IDENTIFIER_PATTERN =
      Pattern.compile(
          "(?i)(접수번호|DART\\s*회사\\s*코드|회사\\s*코드|법인구분|종목\\s*코드|통계표\\s*코드|항목\\s*코드|userStatsId|stat[_ ]?code|item[_ ]?code|corp[_ ]?code|rcept[_ ]?no|stock[_ ]?code)");
  private static final Pattern SOURCE_ATTRIBUTION_PATTERN =
      Pattern.compile("(DART\\s*공시|전자공시시스템|공시에 따르면|공시된 내용에 따르면|공시에는)");
  private static final Pattern KOSIS_SOURCE_ATTRIBUTION_PATTERN =
      Pattern.compile(
          "(?i)(KOSIS(?:\\s+(?:통계|자료))?에 따르면|KOSIS에서 (?:발표|제공)한|국가통계포털(?:\\s+자료)?에 따르면)");
  private static final Pattern BOK_SOURCE_ATTRIBUTION_PATTERN =
      Pattern.compile(
          "(?i)(한국은행(?:\\s*ECOS)?(?:\\s+(?:통계|자료))?에 따르면|ECOS(?:\\s+(?:통계|자료))?에 따르면|한국은행이 발표한)");
  private static final Pattern AWKWARD_ATTRIBUTION_PATTERN =
      Pattern.compile("(?s)(?:에 따르면|따르면)[^.!?。！？\\n]{0,40}(?:밝혔다|전했다)");
  private static final List<String> FORBIDDEN_PHRASES =
      List.of("매수", "매도", "투자 권유", "급등", "급락", "주가 상승", "주가 하락");

  private final OpenAiProperties openAiProperties;
  private final RestClient restClient;

  public OpenAiNewsGenerator(OpenAiProperties openAiProperties) {
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

  public GeneratedNews generate(NewsPrompt prompt) {
    List<String> sourceNames = sourceNames(prompt);
    String sourceName = String.join(", ", sourceNames);
    try {
      log.info(
          "Requesting OpenAI news generation: model={}, source={}",
          openAiProperties.model(),
          sourceName);
      GeneratedNews generatedNews = requestNews(prompt, "");
      if (shouldValidate(prompt)) {
        ValidationResult validation = validateNews(sourceNames, generatedNews);
        if (!validation.valid()) {
          log.warn(
              "Generated news failed content validation: source={}, violations={}",
              sourceName,
              validation.violations());
          generatedNews = requestNews(prompt, validation.correctionInstruction());
          ValidationResult retryValidation = validateNews(sourceNames, generatedNews);
          if (!retryValidation.valid()) {
            log.warn(
                "Regenerated news failed content validation: source={}, violations={}",
                sourceName,
                retryValidation.violations());
            throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
          }
        }
      }
      log.info(
          "Received OpenAI generated news: source={}, title={}", sourceName, generatedNews.title());
      return generatedNews;
    } catch (HttpStatusCodeException exception) {
      log.warn(
          "OpenAI news generation request failed: status={}, body={}, source={}",
          exception.getStatusCode(),
          exception.getResponseBodyAsString(),
          sourceName);
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    } catch (RestClientException exception) {
      log.warn(
          "OpenAI news generation request failed: message={}, source={}",
          exception.getMessage(),
          sourceName);
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }
  }

  public GeneratedEconomicNews generateEconomicNews() {
    try {
      log.info(
          "Requesting OpenAI economic news generation: model={}",
          openAiProperties.economyNewsModel());
      String response =
          restClient
              .post()
              .uri("/responses")
              .header("Authorization", "Bearer " + openAiProperties.apiKey())
              .contentType(MediaType.APPLICATION_JSON)
              .body(economicNewsRequestBody())
              .retrieve()
              .body(String.class);
      return parseEconomicNews(response);
    } catch (HttpStatusCodeException exception) {
      log.warn(
          "OpenAI economic news generation failed: status={}", exception.getStatusCode());
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    } catch (RestClientException exception) {
      log.warn("OpenAI economic news generation failed: message={}", exception.getMessage());
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }
  }

  private GeneratedNews requestNews(NewsPrompt prompt, String correctionInstruction) {
    String response =
        restClient
            .post()
            .uri("/responses")
            .header("Authorization", "Bearer " + openAiProperties.apiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestBody(prompt, correctionInstruction))
            .retrieve()
            .body(String.class);
    return parseGeneratedNews(response);
  }

  private Map<String, Object> requestBody(NewsPrompt prompt, String correctionInstruction) {
    return Map.of(
        "model",
        openAiProperties.model(),
        "input",
        input(prompt, correctionInstruction),
        "text",
        Map.of("format", responseFormat()));
  }

  private Map<String, Object> economicNewsRequestBody() {
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
        ECONOMY_NEWS_INSTRUCTION.formatted(LocalDate.now(ZoneId.of("Asia/Seoul"))),
        "text",
        Map.of("format", economicResponseFormat()));
  }

  private Map<String, Object> economicResponseFormat() {
    return Map.of(
        "type",
        "json_schema",
        "name",
        "economic_news_article",
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
                "title", Map.of("type", "string"),
                "content", Map.of("type", "string"),
                "category",
                    Map.of(
                        "type",
                        "string",
                        "enum",
                        List.of("REAL_ESTATE", "STOCK", "ETF"))),
            "required",
            List.of("title", "content", "category")));
  }

  private GeneratedEconomicNews parseEconomicNews(String response) {
    try {
      JsonNode responseJson = OBJECT_MAPPER.readTree(response);
      JsonNode generated = OBJECT_MAPPER.readTree(extractOutputText(responseJson));
      String title = generated.path("title").asText();
      String content = generated.path("content").asText();
      Category category = Category.valueOf(generated.path("category").asText());
      List<NewsSourceInfo> sources = extractEconomicNewsSources(responseJson);
      if (title.isBlank() || content.isBlank() || sources.isEmpty()) {
        throw new IllegalArgumentException("Generated economic news is missing required content.");
      }
      return new GeneratedEconomicNews(title, content, category, sources);
    } catch (Exception exception) {
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }
  }

  private List<NewsSourceInfo> extractEconomicNewsSources(JsonNode response) {
    Map<String, NewsSourceInfo> sources = new LinkedHashMap<>();
    for (JsonNode item : response.path("output")) {
      for (JsonNode content : item.path("content")) {
        for (JsonNode annotation : content.path("annotations")) {
          if ("url_citation".equals(annotation.path("type").asText())) {
            addEconomicNewsSource(
                sources, annotation.path("url").asText(), annotation.path("title").asText());
          }
        }
      }
      for (JsonNode source : item.path("action").path("sources")) {
        addEconomicNewsSource(sources, source.path("url").asText(), source.path("title").asText());
      }
    }
    return List.copyOf(sources.values());
  }

  private void addEconomicNewsSource(
      Map<String, NewsSourceInfo> sources, String url, String title) {
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
    if (name == null || name.isBlank()) {
      name = url;
    }
    sources.putIfAbsent(url, new NewsSourceInfo(name, url));
  }

  private Object input(NewsPrompt prompt, String correctionInstruction) {
    if (!prompt.hasStructuredInput()) {
      return prompt.prompt();
    }

    return List.of(
        Map.of("role", "developer", "content", prompt.instruction() + correctionInstruction),
        Map.of("role", "user", "content", prompt.sourceData()));
  }

  private boolean shouldValidate(NewsPrompt prompt) {
    return prompt.hasStructuredInput();
  }

  private List<String> sourceNames(NewsPrompt prompt) {
    List<String> sourceNames =
        prompt.sourceNames().stream()
            .map(OpenAiNewsGenerator::displaySourceName)
            .distinct()
            .toList();
    return sourceNames.isEmpty() ? List.of("직접 입력") : sourceNames;
  }

  private ValidationResult validateNews(List<String> sourceNames, GeneratedNews generatedNews) {
    List<String> violations = new ArrayList<>();
    String title = value(generatedNews.title());
    String content = value(generatedNews.content());
    String allText = title + "\n" + content;

    if (title.isBlank()) {
      violations.add("title은 비어 있지 않아야 함");
    }
    if (content.isBlank()) {
      violations.add("content는 비어 있지 않아야 함");
    } else {
      int paragraphCount = paragraphCount(content);
      if (paragraphCount < 2 || paragraphCount > 5) {
        violations.add("content는 2~5개 문단이어야 함");
      }
      if (BULLET_LINE_PATTERN.matcher(content).find()) {
        violations.add("content에 불릿 또는 목록 형식이 없어야 함");
      }
      int sourceAttributionCount =
          sourceNames.stream()
              .map(this::sourceAttributionPattern)
              .distinct()
              .mapToInt(pattern -> countMatches(pattern, content))
              .sum();
      if (sourceAttributionCount != 1) {
        violations.add("content에 " + String.join(", ", sourceNames) + " 출처 표현이 한 번만 있어야 함");
      }
      if (AWKWARD_ATTRIBUTION_PATTERN.matcher(content).find()) {
        violations.add("출처 표현과 전달 동사를 중복해서 쓰지 않아야 함");
      }
    }
    if (IDENTIFIER_PATTERN.matcher(allText).find()) {
      violations.add("식별 코드성 정보가 없어야 함");
    }
    FORBIDDEN_PHRASES.stream()
        .filter(allText::contains)
        .forEach(phrase -> violations.add("금지 표현이 없어야 함: " + phrase));

    return new ValidationResult(List.copyOf(violations), List.copyOf(sourceNames));
  }

  private Pattern sourceAttributionPattern(String sourceName) {
    if ("KOSIS".equals(sourceName)) {
      return KOSIS_SOURCE_ATTRIBUTION_PATTERN;
    }
    if ("한국은행".equals(sourceName)) {
      return BOK_SOURCE_ATTRIBUTION_PATTERN;
    }
    return SOURCE_ATTRIBUTION_PATTERN;
  }

  private static String displaySourceName(String sourceName) {
    return sourceName == null || sourceName.isBlank() ? "직접 입력" : sourceName;
  }

  private int paragraphCount(String content) {
    return (int)
        Arrays.stream(content.strip().split("\\R\\s*\\R"))
            .map(String::strip)
            .filter(paragraph -> !paragraph.isBlank())
            .count();
  }

  private int countMatches(Pattern pattern, String text) {
    int count = 0;
    var matcher = pattern.matcher(text);
    while (matcher.find()) {
      count++;
    }
    return count;
  }

  private String value(String text) {
    return text == null ? "" : text.strip();
  }

  private record ValidationResult(List<String> violations, List<String> sourceNames) {

    private boolean valid() {
      return violations.isEmpty();
    }

    private String correctionInstruction() {
      return "\n\n[재작성 지침]\n"
          + "이전 출력이 다음 검수 항목을 위반했습니다: "
          + String.join(", ", violations)
          + ". 위반 사항을 모두 수정한 기사만 출력하세요. "
          + "content는 반드시 2~5개 문단으로 작성하고 문단 사이에는 \\n\\n을 사용하세요. "
          + String.join(", ", sourceNames)
          + " 출처 표현은 본문에 한 번만 넣고 '따르면 밝혔다'처럼 중복하지 마세요. "
          + "정보가 부족해도 사실을 반복하거나 추측하지 마세요. 참고 데이터에 없는 사실은 추가하지 마세요.";
    }
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
            Map.of("title", Map.of("type", "string"), "content", Map.of("type", "string")),
            "required",
            List.of("title", "content")));
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

  private int toMillis(java.time.Duration timeout) {
    return Math.toIntExact(timeout.toMillis());
  }
}
