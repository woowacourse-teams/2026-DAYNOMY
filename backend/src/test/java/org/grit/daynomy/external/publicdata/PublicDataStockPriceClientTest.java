package org.grit.daynomy.external.publicdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PublicDataStockPriceClientTest {

  private MockWebServer server;
  private final Logger logger = (Logger) LoggerFactory.getLogger(PublicDataStockPriceClient.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private Level originalLevel;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() throws Exception {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
    server.shutdown();
  }

  @Test
  @DisplayName("주식 시세 API에 시장·기준일·페이지 조건을 전달하고 응답을 매핑한다")
  void getStockPrices() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(successResponse()));
    PublicDataStockPriceClient client =
        new PublicDataStockPriceClient(
            new PublicDataProperties(
                "encoded%2Fkey%2Bvalue%3D",
                server.url("/stock-prices").toString(),
                "https://example.com/etf-prices",
                "https://example.com/listed-stocks",
                null,
                null));

    var response = client.getStockPrices(LocalDate.of(2026, 9, 18), StockMarket.KOSPI, 2, 1000);

    assertThat(response.body().totalCount()).isEqualTo(1);
    assertThat(response.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.srtnCd()).isEqualTo("005930");
              assertThat(item.clpr()).isEqualTo("82000");
            });

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestUrl().queryParameter("serviceKey"))
        .isEqualTo("encoded/key+value=");
    assertThat(request.getRequestUrl().queryParameter("basDt")).isEqualTo("20260918");
    assertThat(request.getRequestUrl().queryParameter("mrktCls")).isEqualTo("KOSPI");
    assertThat(request.getRequestUrl().queryParameter("pageNo")).isEqualTo("2");
    assertThat(request.getRequestUrl().queryParameter("numOfRows")).isEqualTo("1000");
    assertThat(request.getRequestUrl().queryParameter("resultType")).isEqualTo("json");

    assertThat(appender.list).hasSize(2);
    ILoggingEvent completedLog = appender.list.get(1);
    assertThat(completedLog.getLevel()).isEqualTo(Level.INFO);
    assertThat(completedLog.getFormattedMessage())
        .isEqualTo(LogEvent.EXTERNAL_API_COMPLETED.message());
    assertThat(keyValues(completedLog))
        .containsEntry("event", LogEvent.EXTERNAL_API_COMPLETED.code())
        .containsEntry("api", "PUBLIC_DATA_STOCK_PRICE")
        .containsEntry("operation", "getStockPrices")
        .containsEntry("market", StockMarket.KOSPI)
        .containsEntry("baseDate", LocalDate.of(2026, 9, 18))
        .containsEntry("pageNo", 2)
        .containsEntry("responseCount", 1)
        .containsEntry("responseStatus", "00")
        .containsKey("durationMs");
  }

  @Test
  @DisplayName("주식 시세 API 실패를 응답 본문 없이 구조화 로그로 기록한다")
  void logsHttpFailureWithoutResponseBody() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("secret-response-body"));
    PublicDataStockPriceClient client =
        new PublicDataStockPriceClient(
            new PublicDataProperties(
                "service-key",
                server.url("/stock-prices").toString(),
                "https://example.com/etf-prices",
                "https://example.com/listed-stocks",
                null,
                null));

    assertThatThrownBy(
            () -> client.getStockPrices(LocalDate.of(2026, 9, 18), StockMarket.KOSPI, 1, 1000))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);

    assertThat(appender.list).hasSize(2);
    ILoggingEvent failedLog = appender.list.get(1);
    assertThat(failedLog.getLevel()).isEqualTo(Level.ERROR);
    assertThat(failedLog.getFormattedMessage())
        .isEqualTo(LogEvent.EXTERNAL_API_FAILED.message())
        .doesNotContain("secret-response-body");
    assertThat(keyValues(failedLog))
        .containsEntry("event", LogEvent.EXTERNAL_API_FAILED.code())
        .containsEntry("api", "PUBLIC_DATA_STOCK_PRICE")
        .containsEntry("responseStatus", "500")
        .containsEntry("errorCode", ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED.code())
        .containsKey("durationMs")
        .doesNotContainKeys("serviceKey", "responseBody");
  }

  @Test
  void decodesEncodedServiceKeyBeforeBuildingQueryParam() {
    PublicDataStockPriceClient client =
        new PublicDataStockPriceClient(
            new PublicDataProperties(
                "abc%2Fdef%2Bghi%3D%3D",
                "https://example.com", "https://example.com", "https://example.com", null, null));

    assertThat(client.normalizedServiceKey()).isEqualTo("abc/def+ghi==");
  }

  @Test
  void keepsRawServiceKeyAsIs() {
    PublicDataStockPriceClient client =
        new PublicDataStockPriceClient(
            new PublicDataProperties(
                "abc/def+ghi==",
                "https://example.com",
                "https://example.com",
                "https://example.com",
                null,
                null));

    assertThat(client.normalizedServiceKey()).isEqualTo("abc/def+ghi==");
  }

  @Test
  void usesDefaultTimeouts() {
    PublicDataProperties properties =
        new PublicDataProperties(
            "service-key",
            "https://example.com",
            "https://example.com",
            "https://example.com",
            null,
            null);

    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
    assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
  }

  private String successResponse() {
    return """
        {
          "response": {
            "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
            "body": {
              "numOfRows": 1000,
              "pageNo": 2,
              "totalCount": 1,
              "items": {
                "item": [{
                  "basDt": "20260918",
                  "srtnCd": "005930",
                  "itmsNm": "삼성전자",
                  "mrktCtg": "KOSPI",
                  "clpr": "82000",
                  "mrktTotAmt": "489522093910000"
                }]
              }
            }
          }
        }
        """;
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }
}
