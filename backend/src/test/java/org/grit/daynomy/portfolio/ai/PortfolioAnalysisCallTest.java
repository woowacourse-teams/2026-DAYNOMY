package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Map;
import java.util.stream.Collectors;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class PortfolioAnalysisCallTest {
  private final Logger logger = (Logger) LoggerFactory.getLogger(PortfolioAnalysisCall.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private MockWebServer server;

  @BeforeEach
  void setUp() throws Exception {
    appender.start();
    logger.addAppender(appender);
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
    logger.detachAppender(appender);
    appender.stop();
  }

  @ParameterizedTest
  @ValueSource(strings = {"initial", "correction", "summary"})
  void logsEachHttpCallWithoutPublishingTheBodyOrCredentials(String phase) {
    String response =
        """
        {"id":"resp_test123","usage":{"input_tokens":120,"output_tokens":0},
         "output":"secret-analysis"}
        """;
    server.enqueue(new MockResponse().setBody(response));
    MDC.put("batchNumber", "2");
    MDC.put("requestId", "test-request");
    try {
      assertThat(PortfolioAnalysisCall.execute(request(), phase, "test-model")).isEqualTo(response);
      assertThat(appender.list).hasSize(1);
      var event = appender.list.getFirst();
      assertThat(fields(event))
          .containsEntry("phase", phase)
          .containsEntry("batchNumber", "2")
          .containsEntry("outcome", "SUCCESS")
          .containsEntry("responseId", "resp_test123")
          .containsEntry("inputTokens", 120L)
          .containsEntry("outputTokens", 0L);
      assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "test-request");
      assertThat((Long) fields(event).get("durationMs")).isNotNegative();
      assertThat(fields(event).toString() + event.getFormattedMessage())
          .doesNotContain("secret-analysis", "test-api-key", "secret-request");
      assertThat(event.getThrowableProxy()).isNull();
    } finally {
      MDC.remove("batchNumber");
      MDC.remove("requestId");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{}",
        "null",
        "invalid-json",
        "{\"usage\":{\"input_tokens\":-1,\"output_tokens\":\"42\"}}"
      })
  void missingOrInvalidUsageDoesNotBecomeZeroOrChangeTheResponse(String response) {
    server.enqueue(new MockResponse().setBody(response));

    assertThat(PortfolioAnalysisCall.execute(request(), "initial", "test-model"))
        .isEqualTo(response);
    assertThat(appender.list).hasSize(1);
    assertThat(fields(appender.list.getFirst()))
        .containsEntry("outcome", "SUCCESS")
        .doesNotContainKeys("inputTokens", "outputTokens");
  }

  @Test
  void httpErrorIsLoggedOnceAndRethrownWithoutExposingTheErrorBody() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("private-provider-error"));

    assertThatThrownBy(() -> PortfolioAnalysisCall.execute(request(), "correction", "test-model"))
        .isInstanceOf(HttpStatusCodeException.class);

    assertThat(appender.list).hasSize(1);
    var event = appender.list.getFirst();
    assertThat(fields(event))
        .containsEntry("phase", "correction")
        .containsEntry("httpStatus", 429)
        .containsEntry("outcome", "FAILED")
        .doesNotContainKeys("inputTokens", "outputTokens");
    assertThat(fields(event).toString() + event.getFormattedMessage())
        .doesNotContain("private-provider-error", "test-api-key");
    assertThat(event.getThrowableProxy()).isNull();
  }

  @Test
  void transportFailureKeepsTheSameExceptionAndUnknownUsage() {
    var request = mock(RestClient.RequestHeadersSpec.class);
    var failure = new ResourceAccessException("private-connection-detail");
    when(request.retrieve()).thenThrow(failure);

    assertThatThrownBy(() -> PortfolioAnalysisCall.execute(request, "summary", "test-model"))
        .isSameAs(failure);

    assertThat(appender.list).hasSize(1);
    var event = appender.list.getFirst();
    assertThat(fields(event))
        .containsEntry("outcome", "FAILED")
        .containsEntry("errorType", "ResourceAccessException")
        .doesNotContainKeys("inputTokens", "outputTokens");
    assertThat(fields(event).toString()).doesNotContain("private-connection-detail");
  }

  private RestClient.RequestHeadersSpec<?> request() {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(3000);
    return RestClient.builder()
        .baseUrl(server.url("/").toString())
        .requestFactory(factory)
        .build()
        .post()
        .uri("/responses")
        .header("Authorization", "Bearer test-api-key")
        .body("secret-request");
  }

  private Map<String, Object> fields(ILoggingEvent event) {
    return event.getKeyValuePairs().stream()
        .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
  }
}
