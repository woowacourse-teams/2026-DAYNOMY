package org.grit.daynomy.investmentcalendar.external;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BokBaseRateProviderTest {

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
  void combinesMeetingScheduleWithEcosRate() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "text/html; charset=UTF-8")
            .setBody("<table><tr><th scope='row'>10월 22일(목)</th><td></td></tr></table>"));
    server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody("<html/>"));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {"StatisticSearch":{"row":[
                  {"TIME":"20261021","DATA_VALUE":"2.50"},
                  {"TIME":"20261022","DATA_VALUE":"2.75"}
                ]}}
                """));

    var provider = new BokBaseRateProvider(properties());

    var entries = provider.fetch(LocalDate.of(2026, 10, 6), 0, 30);

    assertThat(server.getRequestCount()).isEqualTo(3);
    assertThat(entries).hasSize(1);
    assertThat(entries.getFirst().sourceKey()).isEqualTo("BOK-BASE-RATE-2026-10-22");
    assertThat(entries.getFirst().previousValue()).isEqualByComparingTo("2.50");
    assertThat(entries.getFirst().actualValue()).isEqualByComparingTo("2.75");
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
