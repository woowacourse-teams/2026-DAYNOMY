package org.grit.daynomy.investmentcalendar.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BlsCpiProviderTest {

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
  void convertsOfficialReleaseScheduleAndIndexesToCpiEvent() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "text/html")
            .setBody(
                """
                <table><tr>
                  <td>Wednesday, October 14, 2026</td><td>8:30 AM</td>
                  <td>Consumer Price Index for September 2026</td>
                </tr></table>
                """));
    server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody("<html/>"));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status":"REQUEST_SUCCEEDED",
                  "Results":{"series":[{"data":[
                    {"year":"2025","period":"M10","value":"-"},
                    {"year":"2026","period":"M09","value":"310.00"},
                    {"year":"2026","period":"M08","value":"308.00"},
                    {"year":"2025","period":"M09","value":"300.00"},
                    {"year":"2025","period":"M08","value":"298.00"}
                  ]}]}
                }
                """));

    var provider = new BlsCpiProvider(properties());

    var entries = provider.fetch(LocalDate.of(2026, 10, 6), 0, 30);

    assertThat(entries).hasSize(1);
    assertThat(entries.getFirst().sourceKey()).isEqualTo("BLS-CPI-2026-09");
    assertThat(entries.getFirst().actualValue()).isEqualByComparingTo("3.33");
    assertThat(entries.getFirst().previousValue()).isEqualByComparingTo("3.36");
  }

  private InvestmentCalendarExternalProperties properties() {
    String baseUrl = server.url("/").toString();
    return new InvestmentCalendarExternalProperties(
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        baseUrl,
        server.url("/schedule/news_release/cpi.htm").toString(),
        baseUrl,
        baseUrl,
        "ecos-key",
        baseUrl,
        "dart-key");
  }
}
