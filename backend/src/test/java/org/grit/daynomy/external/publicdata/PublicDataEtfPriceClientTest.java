package org.grit.daynomy.external.publicdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicDataEtfPriceClientTest {

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
  @DisplayName("ETF 시세 API에 기준일과 페이지 조건을 전달하고 응답을 매핑한다")
  void getEtfPrices() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(successResponse()));
    PublicDataEtfPriceClient client =
        new PublicDataEtfPriceClient(
            new PublicDataProperties(
                "encoded%2Fkey%2Bvalue%3D",
                "https://example.com/stock-prices",
                server.url("/etf-prices").toString(),
                "https://example.com/listed-stocks",
                null,
                null));

    var response = client.getEtfPrices(LocalDate.of(2026, 9, 18), 2, 1000);

    assertThat(response.body().totalCount()).isEqualTo(1);
    assertThat(response.items())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.srtnCd()).isEqualTo("069500");
              assertThat(item.itmsNm()).isEqualTo("KODEX 200");
              assertThat(item.clpr()).isEqualTo("53000");
            });

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestUrl().queryParameter("serviceKey"))
        .isEqualTo("encoded/key+value=");
    assertThat(request.getRequestUrl().queryParameter("basDt")).isEqualTo("20260918");
    assertThat(request.getRequestUrl().queryParameter("pageNo")).isEqualTo("2");
    assertThat(request.getRequestUrl().queryParameter("numOfRows")).isEqualTo("1000");
    assertThat(request.getRequestUrl().queryParameter("resultType")).isEqualTo("json");
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
                  "srtnCd": "069500",
                  "isinCd": "KR7069500007",
                  "itmsNm": "KODEX 200",
                  "clpr": "53000"
                }]
              }
            }
          }
        }
        """;
  }
}
