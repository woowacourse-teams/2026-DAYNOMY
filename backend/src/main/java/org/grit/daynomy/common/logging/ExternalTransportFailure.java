package org.grit.daynomy.common.logging;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.net.ssl.SSLException;
import org.slf4j.spi.LoggingEventBuilder;

/** 외부 통신 예외의 원인 체인에서 로그에 안전한 전송 실패 유형만 추출한다. */
public record ExternalTransportFailure(Category category, boolean retryable) {

  public static ExternalTransportFailure from(Throwable exception) {
    Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    Throwable current = exception;
    while (current != null && visited.add(current)) {
      if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
        return new ExternalTransportFailure(Category.TIMEOUT, true);
      }
      if (current instanceof UnknownHostException) {
        return new ExternalTransportFailure(Category.DNS_FAILURE, true);
      }
      if (current instanceof ConnectException) {
        return new ExternalTransportFailure(Category.CONNECTION_FAILED, true);
      }
      if (current instanceof SSLException) {
        return new ExternalTransportFailure(Category.TLS_FAILURE, false);
      }
      current = current.getCause();
    }
    return new ExternalTransportFailure(Category.TRANSPORT_ERROR, false);
  }

  public void addTo(LoggingEventBuilder event) {
    event.addKeyValue("failureCategory", category.name()).addKeyValue("retryable", retryable);
  }

  public enum Category {
    TIMEOUT,
    DNS_FAILURE,
    CONNECTION_FAILED,
    TLS_FAILURE,
    TRANSPORT_ERROR
  }
}
