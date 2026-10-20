package org.grit.daynomy.external.eodhd;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EodhdClientTest {
  private MockWebServer server;
  private EodhdClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client =
        new EodhdClient(new EodhdProperties("test-key", server.url("/api").toString(), null, null));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void mapsSymbolsAndSendsToken() throws Exception {
    json(
        """
        [{"Code":"AAPL","Name":"Apple Inc","Exchange":"NASDAQ","Currency":"USD","Type":"Common Stock","Isin":null,"Country":"USA"}]
        """);
    assertThat(client.getUsSymbols())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.code()).isEqualTo("AAPL");
              assertThat(s.type()).isEqualTo("Common Stock");
              assertThat(s.isin()).isNull();
            });
    var request = server.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/api/exchange-symbol-list/US");
    assertThat(request.getRequestUrl().queryParameter("api_token")).isEqualTo("test-key");
  }

  @Test
  void mapsActualCloseAndDateWithFourDecimals() throws Exception {
    json(
        """
        [{"code":"VTI","exchange":"US","date":"2026-10-16","close":301.1234,"adjusted_close":290.1}]
        """);
    var prices = client.getUsDailyPrices(LocalDate.of(2026, 10, 16));
    assertThat(prices)
        .singleElement()
        .satisfies(
            p -> {
              assertThat(p.close()).isEqualByComparingTo("301.1234");
              assertThat(p.date()).isEqualTo(LocalDate.of(2026, 10, 16));
            });
    var request = server.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/api/eod-bulk-last-day/US");
    assertThat(request.getRequestUrl().queryParameter("date")).isEqualTo("2026-10-16");
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 429, 500})
  void hidesTokenAndBodyWhenRequestFails(int status) {
    server.enqueue(new MockResponse().setResponseCode(status).setBody("test-key"));
    assertThatThrownBy(() -> client.getUsSymbols())
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.errorCode()).isEqualTo(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
              assertThat(e.getCause()).isNull();
              assertThat(e.getMessage()).doesNotContain("test-key");
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "null", "[null]", "not-json"})
  void rejectsMalformedResponses(String body) {
    json(body);
    assertThatThrownBy(() -> client.getUsSymbols()).isInstanceOf(BusinessException.class);
  }

  @Test
  void timesOutWithoutLeakingRequestUrl() {
    client =
        new EodhdClient(
            new EodhdProperties(
                "test-key",
                server.url("/api").toString(),
                Duration.ofSeconds(1),
                Duration.ofMillis(50)));
    server.enqueue(new MockResponse().setHeadersDelay(300, TimeUnit.MILLISECONDS).setBody("[]"));
    assertThatThrownBy(() -> client.getUsSymbols())
        .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCause()).isNull());
  }

  @Test
  void missingTokenDoesNotMakeRequest() {
    client = new EodhdClient(new EodhdProperties("", server.url("/api").toString(), null, null));
    assertThatThrownBy(() -> client.getUsSymbols())
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.errorCode()).isEqualTo(ExternalErrorCode.EODHD_API_NOT_CONFIGURED));
    assertThat(server.getRequestCount()).isZero();
  }

  private void json(String body) {
    server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));
  }
}
