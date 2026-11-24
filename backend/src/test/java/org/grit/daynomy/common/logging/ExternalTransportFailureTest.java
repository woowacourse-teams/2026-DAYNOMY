package org.grit.daynomy.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class ExternalTransportFailureTest {

  @Test
  void classifiesKnownFailuresFromNestedCauses() {
    assertFailure(new SocketTimeoutException(), ExternalTransportFailure.Category.TIMEOUT, true);
    assertFailure(new UnknownHostException(), ExternalTransportFailure.Category.DNS_FAILURE, true);
    assertFailure(
        new ConnectException(), ExternalTransportFailure.Category.CONNECTION_FAILED, true);
    assertFailure(
        new SSLHandshakeException("private-detail"),
        ExternalTransportFailure.Category.TLS_FAILURE,
        false);
  }

  @Test
  void classifiesUnknownTransportFailureWithoutReadingItsMessage() {
    var exception = new RuntimeException("private-detail");

    ExternalTransportFailure failure = ExternalTransportFailure.from(exception);

    assertThat(failure.category()).isEqualTo(ExternalTransportFailure.Category.TRANSPORT_ERROR);
    assertThat(failure.retryable()).isFalse();
  }

  private void assertFailure(
      Throwable cause, ExternalTransportFailure.Category category, boolean retryable) {
    ExternalTransportFailure failure =
        ExternalTransportFailure.from(new RuntimeException("outer-private-detail", cause));

    assertThat(failure.category()).isEqualTo(category);
    assertThat(failure.retryable()).isEqualTo(retryable);
  }
}
