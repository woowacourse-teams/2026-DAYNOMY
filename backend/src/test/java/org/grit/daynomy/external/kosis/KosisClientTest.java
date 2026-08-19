package org.grit.daynomy.external.kosis;

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

class KosisClientTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("KOSIS 통계자료 API를 호출하고 응답을 매핑한다")
  void getRecentData() throws Exception {
    KosisClient client =
        new KosisClient(new KosisProperties("test-key", startServer(kosisResponse()), List.of()));
    var indicator =
        new KosisProperties.Indicator(
            "consumer-price-index",
            "소비자물가지수",
            "MT_ZTITLE",
            "sample-user-stats-id",
            "M",
            Category.ECONOMY,
            "국가데이터처",
            "소비자물가조사",
            "소비자물가지수");

    var response = client.getRecentData(indicator);

    assertThat(response).hasSize(2);
    assertThat(response.getLast().period()).isEqualTo("202607");
    assertThat(response.getLast().value()).isEqualTo("113.42");
  }

  private String startServer(String responseBody) throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/statisticsData.do",
        exchange -> {
          if (!exchange.getRequestURI().getRawQuery().contains("content=json")) {
            exchange.sendResponseHeaders(400, -1);
            exchange.close();
            return;
          }
          byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "text/html;charset=UTF-8");
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return "http://localhost:" + server.getAddress().getPort();
  }

  private String kosisResponse() {
    return """
        [
          {
            "TBL_NM": "소비자물가지수",
            "C1_OBJ_NM": "지역별",
            "C1_NM": "전국",
            "ITM_NM": "총지수",
            "UNIT_NM": "2020=100",
            "PRD_DE": "202606",
            "DT": "112.40"
          },
          {
            "TBL_NM": "소비자물가지수",
            "C1_OBJ_NM": "지역별",
            "C1_NM": "전국",
            "ITM_NM": "총지수",
            "UNIT_NM": "2020=100",
            "PRD_DE": "202607",
            "DT": "113.42"
          }
        ]
        """;
  }
}
