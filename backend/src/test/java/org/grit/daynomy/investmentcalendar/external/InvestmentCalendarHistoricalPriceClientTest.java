package org.grit.daynomy.investmentcalendar.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.external.publicdata.PublicDataProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InvestmentCalendarHistoricalPriceClientTest {

  private MockWebServer server;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void fetchesStockPricesForRequestedAssetAndPeriod() throws Exception {
    server.enqueue(successResponse("005930", "20261001", "69000", 1001));
    server.enqueue(successResponse("005930", "20260930", "67000", 1001));
    var client = client();

    var prices =
        client.fetch(
            AssetCategory.STOCK, "005930", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));

    assertThat(prices).hasSize(2);
    assertThat(prices.getFirst().baseDate()).isEqualTo(LocalDate.of(2026, 9, 30));
    assertThat(prices.getLast().closePrice()).isEqualByComparingTo("69000");
    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestUrl().queryParameter("likeSrtnCd")).isEqualTo("005930");
    assertThat(request.getRequestUrl().queryParameter("beginBasDt")).isEqualTo("20260901");
    assertThat(request.getRequestUrl().queryParameter("endBasDt")).isEqualTo("20261001");
    assertThat(server.takeRequest().getRequestUrl().queryParameter("pageNo")).isEqualTo("2");
  }

  @Test
  void fetchesEtfPrices() {
    server.enqueue(successResponse("069500", "20261001", "48750", 1));
    var client = client();

    var prices =
        client.fetch(
            AssetCategory.ETF, "069500", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));

    assertThat(prices).hasSize(1);
    assertThat(prices.getFirst().closePrice()).isEqualByComparingTo("48750");
  }

  private InvestmentCalendarHistoricalPriceClient client() {
    String baseUrl = server.url("/").toString();
    PublicDataProperties publicDataProperties =
        new PublicDataProperties(
            "service-key",
            server.url("/stock").toString(),
            server.url("/etf").toString(),
            baseUrl,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1));
    InvestmentCalendarExternalProperties calendarProperties =
        new InvestmentCalendarExternalProperties(
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            baseUrl,
            baseUrl,
            baseUrl,
            baseUrl,
            "ecos-key",
            baseUrl,
            "dart-key");
    return new InvestmentCalendarHistoricalPriceClient(publicDataProperties, calendarProperties);
  }

  private MockResponse successResponse(
      String assetCode, String baseDate, String closePrice, int totalCount) {
    return new MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(
            """
            {
              "response": {
                "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                "body": {
                  "numOfRows": 1000,
                  "pageNo": 1,
                  "totalCount": %d,
                  "items": {"item": [
                    {"basDt": "%s", "srtnCd": "%s", "clpr": "%s"}
                  ]}
                }
              }
            }
            """
                .formatted(totalCount, baseDate, assetCode, closePrice));
  }
}
