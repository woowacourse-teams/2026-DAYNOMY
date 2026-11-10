package org.grit.daynomy.external.kis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.grit.daynomy.external.kis.KisStockMasterFixtures.archive;
import static org.grit.daynomy.external.kis.KisStockMasterFixtures.row;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.RetryableExternalRequestException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KisStockMasterClientTest {
  private MockWebServer server;
  private KisStockMasterClient client;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client =
        new KisStockMasterClient(new KisProperties(server.url("/master").toString(), null, null));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void downloadsEachMarketArchiveWithoutCredentials() throws Exception {
    for (String market : KisStockMasterClient.US_MARKETS) {
      byte[] expected =
          archive(market.toUpperCase() + "MST.COD", row(market.toUpperCase(), "AAA", "종목", "2"))
              .readByteArray();
      server.enqueue(new MockResponse().setBody(new Buffer().write(expected)));
      assertThat(client.download(market)).isEqualTo(expected);
      var request = server.takeRequest();
      assertThat(request.getRequestUrl().encodedPath())
          .isEqualTo("/master/" + market + "mst.cod.zip");
      assertThat(request.getRequestUrl().query()).isNull();
      assertThat(request.getHeader("Authorization")).isNull();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {403, 404, 429, 500})
  void distinguishesTransientHttpFailuresWithoutKeepingResponseDetails(int status) {
    server.enqueue(new MockResponse().setResponseCode(status).setBody("upstream detail"));
    assertThatThrownBy(() -> client.download("nas"))
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> {
              assertThat(exception.errorCode())
                  .isEqualTo(ExternalErrorCode.KIS_STOCK_MASTER_REQUEST_FAILED);
              assertThat(exception instanceof RetryableExternalRequestException)
                  .isEqualTo(status == 429 || status >= 500);
              assertThat(exception.getCause()).isNull();
              assertThat(exception.getMessage()).doesNotContain("upstream detail");
            });
  }

  @Test
  void rejectsOversizedArchiveEvenWithoutContentLength() {
    server.enqueue(
        new MockResponse()
            .setChunkedBody(new Buffer().write(new byte[10 * 1024 * 1024 + 1]), 8192));
    assertThatThrownBy(() -> client.download("nas"))
        .isInstanceOf(BusinessException.class)
        .isNotInstanceOf(RetryableExternalRequestException.class);
  }

  @Test
  void rejectsUnexpectedMarketBeforeRequest() {
    assertThatThrownBy(() -> client.download("../other"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void appliesReadTimeoutAsTransientFailure() {
    client =
        new KisStockMasterClient(
            new KisProperties(
                server.url("/master").toString(), Duration.ofSeconds(1), Duration.ofMillis(50)));
    server.enqueue(new MockResponse().setHeadersDelay(300, TimeUnit.MILLISECONDS));
    assertThatThrownBy(() -> client.download("nas"))
        .isInstanceOf(RetryableExternalRequestException.class);
  }
}
