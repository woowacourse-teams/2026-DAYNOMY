package org.grit.daynomy.external.youtube;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

class YouTubeHttpFailureTest {

  @Test
  void classifiesQuotaReasonWithoutRetainingProviderMessage() {
    var exception =
        HttpClientErrorException.create(
            HttpStatus.FORBIDDEN,
            "Forbidden",
            HttpHeaders.EMPTY,
            """
            {"error":{"message":"private quota detail","errors":[{"reason":"quotaExceeded"}],"status":"RESOURCE_EXHAUSTED"}}
            """
                .getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);

    YouTubeHttpFailure failure = YouTubeHttpFailure.from(exception);

    assertThat(failure.category()).isEqualTo(YouTubeHttpFailure.Category.QUOTA_EXHAUSTED);
    assertThat(failure.providerErrorReason()).isEqualTo("quotaExceeded");
    assertThat(failure.providerErrorStatus()).isEqualTo("RESOURCE_EXHAUSTED");
    assertThat(failure.retryable()).isFalse();
    assertThat(failure.toString()).doesNotContain("private quota detail");
  }

  @Test
  void rejectsUnsafeProviderIdentifiers() {
    var exception =
        HttpClientErrorException.create(
            HttpStatus.FORBIDDEN,
            "Forbidden",
            HttpHeaders.EMPTY,
            """
            {"error":{"errors":[{"reason":"secret value with spaces"}],"status":"PERMISSION DENIED"}}
            """
                .getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8);

    YouTubeHttpFailure failure = YouTubeHttpFailure.from(exception);

    assertThat(failure.providerErrorReason()).isNull();
    assertThat(failure.providerErrorStatus()).isNull();
    assertThat(failure.category()).isEqualTo(YouTubeHttpFailure.Category.PERMISSION_DENIED);
  }
}
