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
import org.grit.daynomy.news.ai.GeneratedEconomicNews;
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
  private static final int ECONOMIC_NEWS_ARTICLE_COUNT = 3;
  private static final String ECONOMY_NEWS_INSTRUCTION =
      """
      오늘은 %s(한국 기준)입니다. 웹 검색으로 최근 국내외 경제 이슈 중 한국 주식시장이나 상장 기업에 영향을 줄 수 있는 이슈 3개를 찾아 뉴스 초안을 작성하세요. 세 이슈는 서로 달라야 하며, 영향도와 시의성이 큰 순서로 선정하세요.

      기업의 대형 수주·공급 계약, 실적·투자·생산 변화, 수출·공급망 변화, 금리·환율·물가·무역 정책 등 주가에 영향을 줄 수 있는 구체적인 사건을 우선 살피세요. 부동산이나 ETF 이슈는 해당 시장의 변화가 분명하고 기존 카테고리에 가장 잘 맞을 때 선택하세요. 공공기관 통계나 보도자료만을 주된 소재로 삼지 말고, 경제 전문 매체와 주요 언론의 최신 보도를 우선 확인하세요. 게시일과 실제 사건 발생일을 구분하고, 각 이슈의 사실관계를 가능한 한 서로 독립된 출처 2곳 이상으로 확인하세요.

      각 초안은 확인된 사건과 수치, 주식시장 또는 관련 기업에 영향을 주는 경로, 영향의 범위와 한계를 설명하세요. 확인된 사실과 분석·가능성을 구분하고, 근거가 없는 전망은 쓰지 마세요. 본문은 객관적인 한국어 신문 기사 문체인 '이다체'로 2~5개 문단을 작성하세요. '습니다체'는 쓰지 말고, '어느 언론은 전했다' 같은 출처 전달 문구를 반복하지 마세요. 원문 문장을 복사하지 말고 여러 출처의 사실을 종합해 독자적인 문장으로 작성하세요. 투자 판단이나 매수·매도 권유는 하지 마세요.

      각 기사에 제목, 본문, category, 검색 결과에서 실제 확인한 출처 URL 목록을 반환하세요. 출처 URL은 응답의 web search 결과에 포함된 URL만 쓰세요. 카테고리는 다음 기준 중 해당 이슈에 가장 가까운 하나를 고르세요.
      - REAL_ESTATE: 주택, 부동산, 전월세, 주택 대출 중심 이슈
      - ETF: 상장지수펀드 상품이나 ETF 시장 중심 이슈
      - STOCK: 상장 기업, 주식시장, 금리, 환율, 물가, 고용, 무역 등 그 밖의 경제 이슈

      이슈마다 서로 다른 사건을 다루고, 같은 사건을 제목이나 관점만 바꾸어 중복 작성하지 마세요. 근거가 부족한 이슈를 추측해 채우지 마세요. 확인 가능한 이슈가 3개 미만이면 생성에 실패하세요.
      """;

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

  public List<GeneratedEconomicNews> generateEconomicNews() {
    try {
      log.atInfo()
          .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_REQUESTED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "economic-news-generation")
          .addKeyValue("model", openAiProperties.economyNewsModel())
          .log(LogEvent.AI_NEWS_GENERATION_REQUESTED.message());
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
      log.atWarn()
          .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_FAILED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "economic-news-generation")
          .addKeyValue("httpStatus", exception.getStatusCode().value())
          .log(LogEvent.AI_NEWS_GENERATION_FAILED.message());
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    } catch (RestClientException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_FAILED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "economic-news-generation")
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .log(LogEvent.AI_NEWS_GENERATION_FAILED.message());
      throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
    }
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
                    "minItems",
                    ECONOMIC_NEWS_ARTICLE_COUNT,
                    "maxItems",
                    ECONOMIC_NEWS_ARTICLE_COUNT,
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
                            "content",
                            Map.of("type", "string"),
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
                        List.of("title", "content", "category", "sourceUrls")))),
            "required",
            List.of("articles")));
  }

  private List<GeneratedEconomicNews> parseEconomicNews(String response) {
    try {
      JsonNode responseJson = OBJECT_MAPPER.readTree(response);
      JsonNode generated = OBJECT_MAPPER.readTree(extractOutputText(responseJson));
      JsonNode articles = generated.path("articles");
      if (!articles.isArray() || articles.size() != ECONOMIC_NEWS_ARTICLE_COUNT) {
        throw new IllegalArgumentException("Expected exactly three economic news articles.");
      }

      Map<String, NewsSourceInfo> availableSources = new LinkedHashMap<>();
      extractEconomicNewsSources(responseJson)
          .forEach(source -> availableSources.put(source.url(), source));

      List<GeneratedEconomicNews> generatedNews = new ArrayList<>();
      for (JsonNode article : articles) {
        String title = article.path("title").asText();
        String content = article.path("content").asText();
        Category category = Category.valueOf(article.path("category").asText());
        Map<String, NewsSourceInfo> articleSources = new LinkedHashMap<>();
        for (JsonNode sourceUrl : article.path("sourceUrls")) {
          NewsSourceInfo source = availableSources.get(sourceUrl.asText());
          if (source != null) {
            articleSources.putIfAbsent(source.url(), source);
          }
        }
        if (title.isBlank() || content.isBlank() || articleSources.size() < 2) {
          throw new IllegalArgumentException(
              "Generated economic news is missing required content.");
        }
        generatedNews.add(
            new GeneratedEconomicNews(
                title, content, category, List.copyOf(articleSources.values())));
      }
      return List.copyOf(generatedNews);
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
