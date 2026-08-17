package org.grit.daynomy.external.dart;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DartClientTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  @DisplayName("DART 공시검색 API를 호출하고 응답을 매핑한다")
  void getDisclosures() throws Exception {
    DartClient dartClient =
        new DartClient(
            new DartProperties("test-key", startServer("/list.json", disclosureResponse())));

    var response =
        dartClient.getDisclosures(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 17), "B", "K");

    assertThat(response.status()).isEqualTo("000");
    assertThat(response.list()).hasSize(1);
    assertThat(response.list().getFirst().corpName()).isEqualTo("카카오");
    assertThat(response.list().getFirst().rceptNo()).isEqualTo("20260817000001");
  }

  @Test
  @DisplayName("DART 유상증자 결정 API를 호출하고 응답을 매핑한다")
  void getCapitalIncreases() throws Exception {
    DartClient dartClient =
        new DartClient(
            new DartProperties(
                "test-key", startServer("/piicDecsn.json", capitalIncreaseResponse())));

    var response =
        dartClient.getCapitalIncreases(
            "00126380", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 17));

    assertThat(response.status()).isEqualTo("000");
    assertThat(response.list().getFirst().increaseMethod()).isEqualTo("주주배정증자");
  }

  @Test
  @DisplayName("DART 전환사채권 발행결정 API를 호출하고 응답을 매핑한다")
  void getConvertibleBonds() throws Exception {
    DartClient dartClient =
        new DartClient(
            new DartProperties(
                "test-key", startServer("/cvbdIsDecsn.json", convertibleBondResponse())));

    var response =
        dartClient.getConvertibleBonds(
            "00126380", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 17));

    assertThat(response.status()).isEqualTo("000");
    assertThat(response.list().getFirst().conversionPrice()).isEqualTo("50000");
  }

  @Test
  @DisplayName("DART 회사합병 결정 API를 호출하고 응답을 매핑한다")
  void getMergerDecisions() throws Exception {
    DartClient dartClient =
        new DartClient(
            new DartProperties(
                "test-key", startServer("/cmpMgDecsn.json", mergerDecisionResponse())));

    var response =
        dartClient.getMergerDecisions(
            "00126380", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 17));

    assertThat(response.status()).isEqualTo("000");
    assertThat(response.list().getFirst().counterpartyCompanyName()).isEqualTo("합병대상");
  }

  private String startServer(String path, String responseBody) throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        path,
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

  private String disclosureResponse() {
    return """
        {
          "status": "000",
          "message": "정상",
          "list": [
            {
              "corp_cls": "K",
              "corp_name": "카카오",
              "corp_code": "00258801",
              "stock_code": "035720",
              "report_nm": "주요사항보고서",
              "rcept_no": "20260817000001",
              "flr_nm": "카카오",
              "rcept_dt": "20260817",
              "rm": ""
            }
          ]
        }
        """;
  }

  private String capitalIncreaseResponse() {
    return """
        {
          "status": "000",
          "message": "정상",
          "list": [
            {
              "rcept_no": "20260817000002",
              "corp_cls": "Y",
              "corp_code": "00126380",
              "corp_name": "삼성전자",
              "ic_mthn": "주주배정증자"
            }
          ]
        }
        """;
  }

  private String convertibleBondResponse() {
    return """
        {
          "status": "000",
          "message": "정상",
          "list": [
            {
              "rcept_no": "20260817000003",
              "corp_cls": "K",
              "corp_code": "00126380",
              "corp_name": "테스트",
              "cv_prc": "50000"
            }
          ]
        }
        """;
  }

  private String mergerDecisionResponse() {
    return """
        {
          "status": "000",
          "message": "정상",
          "list": [
            {
              "rcept_no": "20260817000004",
              "corp_cls": "Y",
              "corp_code": "00126380",
              "corp_name": "테스트",
              "mgptncmp_cmpnm": "합병대상"
            }
          ]
        }
        """;
  }
}
