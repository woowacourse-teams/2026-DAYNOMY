package org.grit.daynomy.external.dart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
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

  @Test
  @DisplayName("DART 공시서류 원본파일 API에서 ZIP 바이너리를 받는다")
  void getOriginalDocument() throws Exception {
    DartClient dartClient =
        new DartClient(
            new DartProperties("test-key", startBinaryServer("/document.xml", documentZip())));

    String document = dartClient.getOriginalDocument("20260817000001");

    assertThat(document).contains("[DART 원문 파일: document.xml]", "<document/>");
  }

  @Test
  @DisplayName("DART 응답이 데이터 없음이면 빈 목록으로 처리한다")
  void getDisclosuresReturnsEmptyListWhenNoData() throws Exception {
    DartClient dartClient =
        new DartClient(new DartProperties("test-key", startServer("/list.json", noDataResponse())));

    var response =
        dartClient.getDisclosures(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 17), "B", "K");

    assertThat(response.status()).isEqualTo("013");
    assertThat(response.list()).isEmpty();
  }

  @Test
  @DisplayName("DART 응답이 잘못된 인증키이면 예외를 던진다")
  void getDisclosuresThrowsWhenInvalidKey() throws Exception {
    DartClient dartClient =
        new DartClient(
            new DartProperties("test-key", startServer("/list.json", invalidKeyResponse())));

    assertThatThrownBy(
            () ->
                dartClient.getDisclosures(
                    LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 17), "B", "K"))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ExternalErrorCode.DART_API_REQUEST_FAILED);
  }

  @Test
  @DisplayName("DART API 응답 timeout이면 요청 실패 예외를 던진다")
  void getDisclosuresThrowsWhenReadTimeout() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/list.json",
        exchange -> {
          try {
            Thread.sleep(500);
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();

    DartClient dartClient =
        new DartClient(
            new DartProperties(
                "test-key",
                "http://localhost:" + server.getAddress().getPort(),
                Duration.ofSeconds(1),
                Duration.ofMillis(50)));

    assertThatThrownBy(
            () ->
                dartClient.getDisclosures(
                    LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 17), "B", "K"))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ExternalErrorCode.DART_API_REQUEST_FAILED);
  }

  @Test
  @DisplayName("DART 응답이 요청 제한이면 예외를 던진다")
  void getMajorReportThrowsWhenRequestLimitExceeded() throws Exception {
    DartClient dartClient =
        new DartClient(
            new DartProperties("test-key", startServer("/piicDecsn.json", requestLimitResponse())));

    assertThatThrownBy(
            () ->
                dartClient.getCapitalIncreases(
                    "00126380", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 17)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ExternalErrorCode.DART_API_REQUEST_FAILED);
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

  private String startBinaryServer(String path, byte[] responseBody) throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        path,
        exchange -> {
          exchange.getResponseHeaders().add("Content-Type", "application/zip");
          exchange.sendResponseHeaders(200, responseBody.length);
          exchange.getResponseBody().write(responseBody);
          exchange.close();
        });
    server.start();
    return "http://localhost:" + server.getAddress().getPort();
  }

  private byte[] documentZip() throws IOException {
    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(output)) {
      zip.putNextEntry(new ZipEntry("document.xml"));
      zip.write("<document/>".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return output.toByteArray();
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

  private String noDataResponse() {
    return """
        {
          "status": "013",
          "message": "조회된 데이타가 없습니다."
        }
        """;
  }

  private String invalidKeyResponse() {
    return """
        {
          "status": "010",
          "message": "등록되지 않은 키입니다."
        }
        """;
  }

  private String requestLimitResponse() {
    return """
        {
          "status": "020",
          "message": "사용한도를 초과하였습니다."
        }
        """;
  }
}
