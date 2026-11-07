package org.grit.daynomy.investmentcalendar.external;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.web.client.*;

class InvestmentCalendarProviderFailureTest {
  enum Provider {
    BOK,
    BLS,
    DART
  }

  private MockWebServer server;
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

  @BeforeEach
  void start() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void stop() throws Exception {
    server.shutdown();
  }

  @ParameterizedTest
  @EnumSource(Provider.class)
  void propagatesHttpFailureInsteadOfReportingNoEvents(Provider kind) {
    server.enqueue(new MockResponse().setResponseCode(503));
    assertThatThrownBy(() -> provider(kind, Duration.ofSeconds(1)).fetch(TODAY, 0, 30))
        .isInstanceOf(HttpServerErrorException.class);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(Provider.class)
  void boundsResponseWaitAndPropagatesTimeout(Provider kind) {
    server.enqueue(new MockResponse().setHeadersDelay(500, TimeUnit.MILLISECONDS).setBody("{}"));
    assertThatThrownBy(() -> provider(kind, Duration.ofMillis(100)).fetch(TODAY, 0, 30))
        .isInstanceOf(ResourceAccessException.class);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @EnumSource(Provider.class)
  void malformedDataResponseIsNotReportedAsAnEmptyCalendar(Provider kind) {
    scheduleBeforeData(kind);
    json("invalid-json");
    assertThatThrownBy(() -> provider(kind, Duration.ofSeconds(1)).fetch(TODAY, 0, 30))
        .isInstanceOf(RestClientException.class);
  }

  @ParameterizedTest
  @EnumSource(Provider.class)
  void providerErrorPayloadIsRejectedEvenWithHttpSuccess(Provider kind) {
    scheduleBeforeData(kind);
    json(
        switch (kind) {
          case BOK -> "{\"RESULT\":{\"CODE\":\"ERROR-100\"}}";
          case BLS -> "{\"status\":\"REQUEST_FAILED\"}";
          case DART ->
              "{\"status\":\"010\",\"message\":\"invalid key\",\"total_page\":0,\"list\":[]}";
        });
    assertThatThrownBy(() -> provider(kind, Duration.ofSeconds(1)).fetch(TODAY, 0, 30))
        .isInstanceOf(IllegalStateException.class);
  }

  private void scheduleBeforeData(Provider kind) {
    if (kind == Provider.DART) return;
    String html =
        kind == Provider.BOK
            ? "<table><tr><th scope='row'>10월 22일(목)</th></tr></table>"
            : "<table><tr><td>Wednesday, October 14, 2026</td><td>8:30 AM</td><td>Consumer Price Index for September 2026</td></tr></table>";
    server.enqueue(
        new MockResponse().setHeader("Content-Type", "text/html; charset=UTF-8").setBody(html));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "text/html; charset=UTF-8")
            .setBody("<html/>"));
  }

  private void json(String body) {
    server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));
  }

  private InvestmentCalendarProvider provider(Provider kind, Duration timeout) {
    String url = server.url("/").toString();
    var properties =
        new InvestmentCalendarExternalProperties(
            Duration.ofSeconds(1), timeout, url, url, url, url, "test-ecos", url, "test-dart");
    return switch (kind) {
      case BOK -> new BokBaseRateProvider(properties, RestClient.builder());
      case BLS -> new BlsCpiProvider(properties, RestClient.builder());
      case DART -> new OpenDartEarningsProvider(properties, RestClient.builder());
    };
  }
}
