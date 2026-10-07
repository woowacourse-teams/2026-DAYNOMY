package org.grit.daynomy.external.finlife;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FinlifeProductClientTest {

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
  void callsDepositAndSavingApisAndMapsActualProducts() throws Exception {
    server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(response("예금")));
    server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(response("적금")));

    FinlifeProductClient client =
        new FinlifeProductClient(
            new FinlifeProperties("test-key", server.url("/").toString(), "020000", null, null));

    List<FinlifeProduct> products = client.getProducts();

    assertThat(products).hasSize(2);
    assertThat(products.get(0).companyName()).isEqualTo("테스트은행");
    assertThat(products.get(0).productName()).isEqualTo("테스트 예금");
    assertThat(products.get(0).baseRate()).hasToString("3.00");
    assertThat(products.get(0).maxRate()).hasToString("3.50");
    assertThat(products.get(0).termMonths()).isEqualTo(12);

    RecordedRequest depositRequest = server.takeRequest();
    assertThat(depositRequest.getPath())
        .contains("/depositProductsSearch.json")
        .contains("auth=test-key")
        .contains("topFinGrpNo=020000")
        .contains("pageNo=1");
    assertThat(server.takeRequest().getPath()).contains("/savingProductsSearch.json");
  }

  private String response(String type) {
    String productName = "예금".equals(type) ? "테스트 예금" : "테스트 적금";
    return """
        {
          "result": {
            "baseList": [{
              "dcls_month": "202610",
              "fin_co_no": "0010001",
              "kor_co_nm": "테스트은행",
              "fin_prdt_cd": "TEST-%s",
              "fin_prdt_nm": "%s",
              "join_way": "영업점, 인터넷뱅킹",
              "spcl_cnd": "급여이체 시 우대",
              "join_member": "실명의 개인",
              "join_deny": "1",
              "max_limit": "10000000"
            }],
            "optionList": [{
              "fin_co_no": "0010001",
              "fin_prdt_cd": "TEST-%s",
              "save_trm": "12",
              "intr_rate": "3.00",
              "intr_rate2": "3.50"
            }]
          }
        }
        """.formatted(type, productName, type);
  }
}
