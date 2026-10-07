package org.grit.daynomy.external.finlife;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FinlifeProductClientErrorTest {

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
  void identifiesDepositApiFailure() {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"result\":{\"err_cd\":\"010\",\"err_msg\":\"invalid key\"}}"));

    FinlifeProductClient client = client();

    assertThatThrownBy(client::getProducts)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                org.assertj.core.api.Assertions.assertThat(exception.errorCode())
                    .isEqualTo(ExternalErrorCode.FINLIFE_DEPOSIT_API_REQUEST_FAILED));
  }

  private FinlifeProductClient client() {
    return new FinlifeProductClient(
        new FinlifeProperties("test-key", server.url("/").toString(), "020000", null, null));
  }
}
