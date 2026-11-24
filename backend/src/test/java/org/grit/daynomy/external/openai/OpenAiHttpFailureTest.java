package org.grit.daynomy.external.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;

class OpenAiHttpFailureTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void classifiesExhaustedCreditAsNonRetryableWithoutKeepingMessage() {
    OpenAiHttpFailure failure =
        OpenAiHttpFailure.from(
            exception(
                HttpStatus.TOO_MANY_REQUESTS,
                """
                {"error":{"message":"sensitive billing detail","type":"insufficient_quota","code":"credit_balance_exhausted"}}
                """,
                null),
            CLOCK);

    assertThat(failure.category()).isEqualTo(OpenAiHttpFailure.Category.QUOTA_EXHAUSTED);
    assertThat(failure.providerErrorCode()).isEqualTo("credit_balance_exhausted");
    assertThat(failure.providerErrorType()).isEqualTo("insufficient_quota");
    assertThat(failure.retryable()).isFalse();
    assertThat(failure.toString()).doesNotContain("sensitive billing detail");
  }

  @Test
  void usesSpecificSpendLimitCodeBeforeBroadQuotaType() {
    OpenAiHttpFailure failure =
        OpenAiHttpFailure.from(
            exception(
                HttpStatus.TOO_MANY_REQUESTS,
                """
                {"error":{"type":"insufficient_quota","code":"organization_spend_limit_exceeded"}}
                """,
                null),
            CLOCK);

    assertThat(failure.category()).isEqualTo(OpenAiHttpFailure.Category.SPEND_LIMIT_EXCEEDED);
    assertThat(failure.retryable()).isFalse();
  }

  @Test
  void classifiesTemporaryRateLimitAsRetryableAndKeepsServerDelay() {
    OpenAiHttpFailure failure =
        OpenAiHttpFailure.from(
            exception(
                HttpStatus.TOO_MANY_REQUESTS,
                """
                {"error":{"type":"rate_limit_error","code":"rate_limit_exceeded"}}
                """,
                "7"),
            CLOCK);

    assertThat(failure.category()).isEqualTo(OpenAiHttpFailure.Category.RATE_LIMITED);
    assertThat(failure.retryable()).isTrue();
    assertThat(failure.retryAfterSeconds()).isEqualTo(7L);
  }

  @Test
  void classifiesOverloadedServerAsRetryable() {
    OpenAiHttpFailure failure =
        OpenAiHttpFailure.from(
            exception(
                HttpStatus.SERVICE_UNAVAILABLE,
                """
                {"error":{"type":"service_unavailable_error","code":"server_is_overloaded"}}
                """,
                "Tue, 24 Nov 2026 00:00:30 GMT"),
            CLOCK);

    assertThat(failure.category()).isEqualTo(OpenAiHttpFailure.Category.SERVER_ERROR);
    assertThat(failure.retryable()).isTrue();
    assertThat(failure.retryAfterSeconds()).isEqualTo(30L);
  }

  @Test
  void fallsBackToHttpStatusWhenProviderBodyIsMalformed() {
    OpenAiHttpFailure failure =
        OpenAiHttpFailure.from(exception(HttpStatus.TOO_MANY_REQUESTS, "not-json", null), CLOCK);

    assertThat(failure.category()).isEqualTo(OpenAiHttpFailure.Category.RATE_LIMITED);
    assertThat(failure.providerErrorCode()).isNull();
    assertThat(failure.providerErrorType()).isNull();
    assertThat(failure.retryable()).isTrue();
  }

  @Test
  void dropsUnsafeProviderIdentifiers() {
    OpenAiHttpFailure failure =
        OpenAiHttpFailure.from(
            exception(
                HttpStatus.BAD_REQUEST,
                """
                {"error":{"type":"invalid request with spaces","code":"bad code"}}
                """,
                null),
            CLOCK);

    assertThat(failure.category()).isEqualTo(OpenAiHttpFailure.Category.INVALID_REQUEST);
    assertThat(failure.providerErrorCode()).isNull();
    assertThat(failure.providerErrorType()).isNull();
    assertThat(failure.retryable()).isFalse();
  }

  private HttpStatusCodeException exception(
      HttpStatus status, String responseBody, String retryAfter) {
    HttpHeaders headers = new HttpHeaders();
    if (retryAfter != null) {
      headers.set(HttpHeaders.RETRY_AFTER, retryAfter);
    }
    byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
    if (status.is4xxClientError()) {
      return HttpClientErrorException.create(
          status, status.getReasonPhrase(), headers, body, StandardCharsets.UTF_8);
    }
    return HttpServerErrorException.create(
        status, status.getReasonPhrase(), headers, body, StandardCharsets.UTF_8);
  }
}
