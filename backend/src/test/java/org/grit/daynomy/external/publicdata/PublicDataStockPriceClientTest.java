package org.grit.daynomy.external.publicdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grit.daynomy.asset.domain.StockMarket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicDataStockPriceClientTest {

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
  }

  @Test
  void decodesEncodedServiceKeyBeforeBuildingQueryParam() {
    PublicDataStockPriceClient client =
        new PublicDataStockPriceClient(
            new PublicDataProperties(
                "abc%2Fdef%2Bghi%3D%3D", "https://example.com", "https://example.com", null, null));

    assertThat(client.normalizedServiceKey()).isEqualTo("abc/def+ghi==");
  }

  @Test
  void keepsRawServiceKeyAsIs() {
    PublicDataStockPriceClient client =
        new PublicDataStockPriceClient(
            new PublicDataProperties(
                "abc/def+ghi==", "https://example.com", "https://example.com", null, null));

    assertThat(client.normalizedServiceKey()).isEqualTo("abc/def+ghi==");
  }

  @Test
  void usesDefaultTimeouts() {
    PublicDataProperties properties =
        new PublicDataProperties(
            "service-key", "https://example.com", "https://example.com", null, null);

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
}
