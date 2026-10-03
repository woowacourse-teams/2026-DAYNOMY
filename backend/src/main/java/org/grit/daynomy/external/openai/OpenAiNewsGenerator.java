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
  private static final int BASE_ECONOMIC_NEWS_ARTICLE_COUNT = 4;
  private static final int MAX_ECONOMIC_NEWS_ARTICLE_COUNT = 5;
  private static final int MAX_GENERATION_ATTEMPTS = 3;
  private static final int MIN_CONTENT_LENGTH = 1_000;
  private static final int MAX_CONTENT_LENGTH = 1_800;
  private static final int MIN_CONTENT_PARAGRAPHS = 5;
  private static final int MAX_CONTENT_PARAGRAPHS = 9;
  private static final String ECONOMY_NEWS_INSTRUCTION =
      """
      오늘은 %s(한국 기준)입니다. 웹 검색으로 최근 국내외 경제 이슈 중 한국 주식시장이나 상장 기업과 관련된 기본 뉴스 4개를 찾아 사실을 전달하는 뉴스 본문을 작성하세요. 네 이슈는 서로 다른 사건과 주제를 다뤄야 하며, 특정 대기업이나 인기 종목의 뉴스만 반복하지 마세요.

      기업의 수주·공급 계약, 실적·투자·생산 변화, 산업별 수요 변화, 수출·공급망 변화, 금리·환율·물가·고용·무역 정책 등 다양한 유형의 구체적인 사건을 폭넓게 살피세요. 기업을 다룰 때는 삼성전자·SK하이닉스 같은 초대형주에 검색 결과가 편중되지 않도록 코스피·코스닥의 다양한 업종과 규모의 상장 기업을 함께 검색하세요. 가능한 경우 대형주와 중소형주, 코스피와 코스닥, 서로 다른 산업을 섞어 선정하고, 기업 규모나 인지도보다 사건의 구체성·최신성·경제적 연관성을 기준으로 판단하세요.

      기본 4개를 작성한 뒤, 기본 4개에 REAL_ESTATE 기사가 하나도 없을 때만 주택가격·주택 거래량·분양·공급·전월세·주택담보대출·부동산 금융·관련 정책 중 최근의 구체적인 사건이 있는지 별도로 검색하세요. 서로 독립된 출처 2곳 이상으로 확인할 수 있고 한국 경제나 부동산 시장에 영향을 줄 만한 이슈가 있을 때만 REAL_ESTATE 카테고리의 부동산 뉴스 1개를 추가하세요. 기본 4개에 부동산 기사가 이미 하나 이상 있거나 추가로 확인 가능한 부동산 이슈가 없으면 기본 4개만 반환하세요. 부동산 기사가 여러 개 포함되는 것은 허용하되, 각 기사는 서로 다른 사건을 다뤄야 합니다. ETF 이슈는 해당 시장의 변화가 분명하고 기존 카테고리에 가장 잘 맞을 때 기본 4개 중 하나로 선택하세요.

      공공기관 통계나 보도자료만을 주된 소재로 삼지 말고, 경제 전문 매체와 주요 언론의 최신 보도를 우선 확인하세요. 다만 계약·공시·통계처럼 1차 자료가 핵심인 사건은 금융감독원 전자공시, 정부·공공기관 자료를 함께 확인하세요. 게시일과 실제 사건 발생일을 구분하고, 각 이슈의 사실관계를 서로 독립된 출처 2곳 이상으로 확인하세요. 가능한 경우 한국에서 발행된 주요 언론사·경제 전문지·공식기관 자료를 우선 사용하세요.

      뉴스 본문은 시장 분석이나 투자 의견이 아니라 확인된 사건을 설명하는 기사여야 합니다. 본문은 공백 포함 1,000~1,800자, 5~9개 문단으로 작성하고 문단 사이는 빈 줄 하나로 구분하세요. 첫 문단은 누가, 언제, 무엇을 했는지 요약하는 리드문으로 작성하세요. 이후 문단에는 계약금액·수량·기간·지역·계약 상대방·공급 제품·통계 수치·사건 경과·기업과 기관의 현재 사업 현황 등 확인된 세부 사실을 작성하세요. 회사나 기관의 공식 발표와 관계자 발언은 발언 주체를 명확히 밝히세요. 계약 조건, 매출 인식 시점, 정책 시행일처럼 이미 확인된 일정과 조건은 작성할 수 있습니다. 마지막 부분에는 확인된 사실의 범위나 미확정 사항을 중립적으로 작성하고, 확인된 사실을 전달하는 것으로 본문을 끝내세요.

      본문은 객관적인 한국어 신문 기사 문체인 해라체로 작성하세요. '이다', '했다', '밝혔다', '전했다'와 같은 종결을 사용하고, 합쇼체인 '합니다', '하십시오'와 해요체인 '해요', '이에요'는 사용하지 마세요. 원문 문장을 복사하지 말고 여러 출처의 사실을 종합해 독자적인 문장으로 작성하세요.

      본문에는 주가 상승·하락 전망, 특정 기업이나 업종의 수혜 가능성, 추가 수주나 실적 개선 전망, 시장 파급효과에 대한 결론, 투자자에게 유리하거나 불리하다는 판단, 매수·매도·보유 등 투자 행동에 대한 의견을 작성하지 마세요. '관심이 커질 전망이다', '수혜가 예상된다', '주목할 필요가 있다'와 같은 전망도 쓰지 마세요. 회사·정부기관·증권사 연구원 등의 실제 전망을 소개해야 할 때만 발언 주체와 근거를 명확히 밝힌 인용 또는 간접 인용으로 작성하세요.

      한국시간 기준 생성일을 기준으로 최근 3일 이내에 실제로 발생했거나 최초 보도된 사건을 다루세요. 사건 발생일을 확인할 수 있으면 보도일보다 사건 발생일을 우선하고, 본문에는 가능한 경우 정확한 날짜를 작성하세요. 3일보다 오래된 자료는 최근 사건의 배경 설명으로만 사용하세요. 오래된 사건을 최근에 다시 보도한 기사만으로 새로운 이슈를 만들지 말고, 사건일이나 보도일을 확인할 수 없는 자료는 핵심 근거로 사용하지 마세요.

      각 기사에 제목, 본문, category, 검색 결과에서 실제 확인한 출처 URL 목록을 반환하세요. 출처 URL은 응답의 web search 결과에 포함된 URL만 쓰세요. 카테고리는 다음 기준 중 해당 이슈에 가장 가까운 하나를 고르세요.
      - REAL_ESTATE: 주택, 부동산, 전월세, 주택 대출 중심 이슈
      - ETF: 상장지수펀드 상품이나 ETF 시장 중심 이슈
      - STOCK: 상장 기업, 주식시장, 금리, 환율, 물가, 고용, 무역 등 그 밖의 경제 이슈

      sourceUrls에는 서로 독립된 출처를 최소 2개, 최대 4개 반환하세요. 가능한 경우 한국에서 발행된 주요 언론사·경제 전문지·공식기관 자료를 우선 사용하고, 국내 기업과 국내 시장에 관한 이슈는 한국어로 발행된 국내 출처를 우선 사용하세요. 해외 출처는 국내 출처만으로 사실을 확인하기 어렵거나 해외 사건을 다루는 경우에만 사용하세요. 본문 안에 URL을 직접 삽입하지 마세요.

      이슈마다 서로 다른 사건을 다루고, 같은 사건을 제목이나 관점만 바꾸어 중복 작성하지 마세요. 근거가 부족한 이슈를 추측해 채우지 마세요. 기본 뉴스가 4개 미만이면 생성에 실패하세요. 부동산 추가 뉴스가 없거나 기본 뉴스에 부동산 기사가 이미 포함되어 있으면 4개, 부동산 추가 뉴스가 있으면 5개를 반환하세요.
      """;
  private static final String RETRY_INSTRUCTION =
      """

      이전 뉴스 초안이 본문 규칙 검증에 실패했습니다. 실패한 초안을 보완하는 대신 처음부터 다시 검색하고 작성하세요. 본문은 사실 전달만 포함하고 시장 분석·전망·투자 의견을 제외해야 합니다. 공백 포함 1,000~1,800자, 5~9개 문단을 정확히 지키고, 문단 사이는 빈 줄 하나로 구분하세요. 최근 3일 이내의 사건만 선택하고, 출처가 부족하거나 날짜를 확인할 수 없는 이슈는 선택하지 마세요.
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
    log.atInfo()
        .addKeyValue("event", LogEvent.AI_NEWS_GENERATION_REQUESTED.code())
        .addKeyValue("api", "OpenAI")
        .addKeyValue("operation", "economic-news-generation")
        .addKeyValue("model", openAiProperties.economyNewsModel())
        .log(LogEvent.AI_NEWS_GENERATION_REQUESTED.message());

    for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
      try {
        String response = requestEconomicNews(attempt);
        return parseEconomicNews(response);
      } catch (InvalidEconomicNewsException exception) {
        log.atWarn()
            .addKeyValue("event", LogEvent.AI_NEWS_VALIDATION_FAILED.code())
            .addKeyValue("api", "OpenAI")
            .addKeyValue("operation", "economic-news-generation")
            .addKeyValue("attempt", attempt)
            .addKeyValue("reason", exception.getMessage())
            .log(LogEvent.AI_NEWS_VALIDATION_FAILED.message());
        if (attempt == MAX_GENERATION_ATTEMPTS) {
          throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
        }
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

    throw new BusinessException(ExternalErrorCode.AI_NEWS_GENERATION_FAILED);
  }

  private String requestEconomicNews(int attempt) {
    String instruction =
        ECONOMY_NEWS_INSTRUCTION.formatted(LocalDate.now(ZoneId.of("Asia/Seoul")))
            + (attempt == 1 ? "" : RETRY_INSTRUCTION);
    return restClient
        .post()
        .uri("/responses")
        .header("Authorization", "Bearer " + openAiProperties.apiKey())
        .contentType(MediaType.APPLICATION_JSON)
        .body(economicNewsRequestBody(instruction))
        .retrieve()
        .body(String.class);
  }

  private Map<String, Object> economicNewsRequestBody(String instruction) {
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
                    BASE_ECONOMIC_NEWS_ARTICLE_COUNT,
                    "maxItems",
                    MAX_ECONOMIC_NEWS_ARTICLE_COUNT,
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
      if (!articles.isArray()
          || articles.size() < BASE_ECONOMIC_NEWS_ARTICLE_COUNT
          || articles.size() > MAX_ECONOMIC_NEWS_ARTICLE_COUNT) {
        throw new IllegalArgumentException("Expected four or five economic news articles.");
      }

      Map<String, NewsSourceInfo> availableSources = new LinkedHashMap<>();
      extractEconomicNewsSources(responseJson)
          .forEach(source -> availableSources.put(source.url(), source));

      List<GeneratedEconomicNews> generatedNews = new ArrayList<>();
      int realEstateArticleCount = 0;
      for (JsonNode article : articles) {
        String title = article.path("title").asText();
        String content = article.path("content").asText();
        Category category = Category.valueOf(article.path("category").asText());
        if (category == Category.REAL_ESTATE) {
          realEstateArticleCount++;
        }
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
        validateContent(content);
        generatedNews.add(
            new GeneratedEconomicNews(
                title, content, category, List.copyOf(articleSources.values())));
      }
      if (articles.size() == MAX_ECONOMIC_NEWS_ARTICLE_COUNT && realEstateArticleCount == 0) {
        throw new IllegalArgumentException(
            "Five economic news articles must include at least one real estate article.");
      }
      return List.copyOf(generatedNews);
    } catch (Exception exception) {
      throw new InvalidEconomicNewsException(exception.getMessage(), exception);
    }
  }

  private void validateContent(String content) {
    String normalizedContent = content.strip();
    int contentLength = normalizedContent.codePointCount(0, normalizedContent.length());
    int paragraphCount = normalizedContent.split("\\R\\s*\\R").length;
    if (contentLength < MIN_CONTENT_LENGTH || contentLength > MAX_CONTENT_LENGTH) {
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

    throw new IllegalArgumentException("OpenAI response did not contain output text.");
  }

  private int toMillis(java.time.Duration timeout) {
    return Math.toIntExact(timeout.toMillis());
  }

  private static final class InvalidEconomicNewsException extends RuntimeException {

    private InvalidEconomicNewsException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
