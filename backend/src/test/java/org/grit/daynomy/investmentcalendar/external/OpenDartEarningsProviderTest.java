package org.grit.daynomy.investmentcalendar.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OpenDartEarningsProviderTest {

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
  void parsesAdvanceDisclosureDateAndPeriod() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status":"000","total_page":1,
                  "list":[{
                    "corp_name":"두산","stock_code":"000150",
                    "report_nm":"결산실적공시예고(안내공시)",
                    "rcept_no":"20261006800215","rcept_dt":"20261006"
                  }]
                }
                """));
    server.enqueue(zipResponse(advanceDocument()));

    var provider = new OpenDartEarningsProvider(properties());

    var entries = provider.fetch(LocalDate.of(2026, 10, 6), 5, 30);

    assertThat(entries).hasSize(1);
    assertThat(entries.getFirst().title()).isEqualTo("두산 3분기 실적 발표");
    assertThat(entries.getFirst().sourceKey()).isEqualTo("DART-EARNINGS-000150-2026-09-30");
    assertThat(entries.getFirst().actualValue()).isNull();
  }

  @Test
  void parsesQuarterlyOperatingProfitFromResultDisclosure() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status":"000","total_page":1,
                  "list":[{
                    "corp_name":"두산","stock_code":"000150",
                    "report_nm":"연결재무제표기준영업(잠정)실적(공정공시)",
                    "rcept_no":"20261021800215","rcept_dt":"20261021"
                  }]
                }
                """));
    server.enqueue(zipResponse(resultDocument()));

    var provider = new OpenDartEarningsProvider(properties());

    var entries = provider.fetch(LocalDate.of(2026, 10, 21), 5, 30);

    assertThat(entries).hasSize(1);
    assertThat(entries.getFirst().previousValue()).isEqualByComparingTo("120000");
    assertThat(entries.getFirst().actualValue()).isEqualByComparingTo("135000");
    assertThat(entries.getFirst().valueUnit()).isEqualTo("백만원");
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 500})
  void keepsValidDisclosuresBeforeAndAfterFailedDocument(int status) throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {"status":"000","total_page":1,"list":[
                  {"corp_name":"첫 기업","stock_code":"000001",
                   "report_nm":"결산실적공시예고","rcept_no":"first","rcept_dt":"20261006"},
                  {"corp_name":"실패 기업","stock_code":"000002",
                   "report_nm":"결산실적공시예고","rcept_no":"failed","rcept_dt":"20261006"},
                  {"corp_name":"다음 기업","stock_code":"000003",
                   "report_nm":"결산실적공시예고","rcept_no":"last","rcept_dt":"20261006"}
                ]}
                """));
    server.enqueue(zipResponse(advanceDocument()));
    server.enqueue(new MockResponse().setResponseCode(status).setBody("<result>error</result>"));
    server.enqueue(zipResponse(advanceDocument()));

    var entries =
        new OpenDartEarningsProvider(properties()).fetch(LocalDate.of(2026, 10, 6), 5, 30);

    assertThat(entries)
        .extracting(entry -> entry.relatedAssetCode())
        .containsExactly("000001", "000003");
  }

  private MockResponse zipResponse(String document) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("document.xml"));
      zip.write(document.getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    return new MockResponse()
        .setHeader("Content-Type", "application/zip")
        .setBody(new Buffer().write(bytes.toByteArray()));
  }

  private String advanceDocument() {
    return """
        <html><head><meta charset="UTF-8"></head><body><table>
          <tr><td>2. 결산대상기간</td><td>종료일</td><td>2026-09-30</td></tr>
          <tr><td>3. 결산실적 공시예정일</td><td>2026-10-21</td></tr>
        </table></body></html>
        """;
  }

  private String resultDocument() {
    return """
        <html><head><meta charset="UTF-8"></head><body><table>
          <tr><td>당기실적</td><td>2026-07-01</td><td>~</td><td>2026-09-30</td></tr>
          <tr><td>단위 : 백만원, %</td></tr>
          <tr><td>영업이익</td><td>당해실적</td><td>135,000</td><td>120,000</td></tr>
        </table></body></html>
        """;
  }

  private InvestmentCalendarExternalProperties properties() {
    String baseUrl = server.url("/").toString();
    return new InvestmentCalendarExternalProperties(
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        baseUrl,
        baseUrl,
        baseUrl,
        baseUrl,
        "ecos-key",
        baseUrl,
        "dart-key");
  }
}
