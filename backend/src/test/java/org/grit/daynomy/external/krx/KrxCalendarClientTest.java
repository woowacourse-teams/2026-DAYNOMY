package org.grit.daynomy.external.krx;

import static org.assertj.core.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KrxCalendarClientTest {
  private MockWebServer server;
  private KrxCalendarClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client =
        new KrxCalendarClient(
            new KrxCalendarProperties(
                server.url("/").toString(), Duration.ofSeconds(1), Duration.ofSeconds(1)),
            org.springframework.web.client.RestClient.builder());
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void readsOnlyPublishedYearsFromTheCalendarSelector() {
    server.enqueue(
        new MockResponse()
            .setBody(
                """
        <select name="unrelated"><option value="2099">2099</option></select>
        <select name="search_bas_yy" id="year"><option value="2027">2027</option>
        <option selected="selected" value="2026">2026</option></select>
        """));
    assertThat(client.publishedYears()).containsExactlyInAnyOrder(2026, 2027);
  }

  @Test
  void usesTheOfficialKrxFormAndParsesJsonServedAsHtml() throws Exception {
    server.enqueue(new MockResponse().setBody("calendar+code=="));
    String response;
    try (var stream = getClass().getResourceAsStream("/krx/calendar-2026.json")) {
      response = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
    server.enqueue(
        new MockResponse().setHeader("Content-Type", "text/html;charset=UTF-8").setBody(response));
    assertThat(client.holidays(2026))
        .hasSize(17)
        .contains(
            LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 3),
            LocalDate.of(2026, 7, 17), LocalDate.of(2026, 12, 31));
    var otp = server.takeRequest();
    assertThat(otp.getRequestUrl().queryParameter("bld"))
        .isEqualTo("MKD/01/0110/01100305/mkd01100305_01");
    var request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/contents/OPN/99/OPN99000001.jspx");
    assertThat(request.getBody().readUtf8())
        .contains("search_bas_yy=2026", "gridTp=KRX", "code=calendar%2Bcode%3D%3D");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "{\"block1\":[]}",
        "<html>unavailable</html>",
        "{\"block1\":[{\"calnd_dd\":\"2027-12-31\"}]}",
        "{\"block1\":[{\"calnd_dd\":\"2026-12-31\"}]}",
        "{\"block1\":[{\"calnd_dd\":\"2026-02-30\"}]}",
        "{\"block1\":[{\"calnd_dd\":\"2026-01-01\"},{\"calnd_dd\":\"2026-01-01\"}]}"
      })
  void rejectsEmptyIncompleteWrongYearAndMalformedCalendars(String body) {
    server.enqueue(new MockResponse().setBody("code"));
    server.enqueue(new MockResponse().setBody(body));
    assertThatThrownBy(() -> client.holidays(2026)).isInstanceOf(BusinessException.class);
  }

  @Test
  void rejectsAnUnavailableYearSelectorWithoutAssumingTradingDays() {
    server.enqueue(new MockResponse().setBody("<html>unavailable</html>"));
    assertThatThrownBy(client::publishedYears).isInstanceOf(BusinessException.class);
  }

  @Test
  void failsOnHttpErrorsAndTimeoutsWithoutExposingTheResponse() {
    server.enqueue(new MockResponse().setResponseCode(503).setBody("private-provider-message"));
    assertThatThrownBy(client::publishedYears)
        .isInstanceOf(BusinessException.class)
        .hasMessageNotContaining("private-provider-message");
    client =
        new KrxCalendarClient(
            new KrxCalendarProperties(
                server.url("/").toString(), Duration.ofMillis(100), Duration.ofMillis(100)),
            org.springframework.web.client.RestClient.builder());
    server.enqueue(new MockResponse().setBody("late response").setBodyDelay(1, TimeUnit.SECONDS));
    assertThatThrownBy(client::publishedYears).isInstanceOf(BusinessException.class);
  }
}
