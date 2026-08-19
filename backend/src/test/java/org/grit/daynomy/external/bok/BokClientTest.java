package org.grit.daynomy.external.bok;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BokClientTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("한국은행 ECOS 통계자료 API를 호출하고 응답을 매핑한다")
  void getRecentData() throws Exception {
    BokClient client =
        new BokClient(new BokProperties("test-key", startServer(bokResponse()), List.of()));
    var indicator =
        new BokProperties.Indicator(
            "base-rate",
            "한국은행 기준금리",
            "722Y001",
            "M",
            Category.ECONOMY,
            "한국은행",
            "한국은행 기준금리 및 여수신금리",
            "한국은행 기준금리",
            List.of("0101000"));

    var response = client.getRecentData(indicator);

    assertThat(response).hasSize(2);
    assertThat(response.getLast().period()).isEqualTo("202607");
    assertThat(response.getLast().value()).isEqualTo("2.50");
  }

  private String startServer(String responseBody) throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/api/StatisticSearch/test-key/json/kr/1/100/722Y001/M",
        exchange -> {
          byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return "http://localhost:" + server.getAddress().getPort();
  }

  private String bokResponse() {
    return """
        {
          "StatisticSearch": {
            "list_total_count": 2,
            "row": [
              {
                "STAT_CODE": "722Y001",
                "STAT_NAME": "한국은행 기준금리 및 여수신금리",
                "ITEM_CODE1": "0101000",
                "ITEM_NAME1": "한국은행 기준금리",
                "ITEM_CODE2": "",
                "ITEM_NAME2": "",
                "UNIT_NAME": "연%",
                "TIME": "202606",
                "DATA_VALUE": "2.50",
                "UNKNOWN_FIELD": "ignored"
              },
              {
                "STAT_CODE": "722Y001",
                "STAT_NAME": "한국은행 기준금리 및 여수신금리",
                "ITEM_CODE1": "0101000",
                "ITEM_NAME1": "한국은행 기준금리",
                "ITEM_CODE2": "",
                "ITEM_NAME2": "",
                "UNIT_NAME": "연%",
                "TIME": "202607",
                "DATA_VALUE": "2.50",
                "UNKNOWN_FIELD": "ignored"
              }
            ]
          }
        }
        """;
  }
}
