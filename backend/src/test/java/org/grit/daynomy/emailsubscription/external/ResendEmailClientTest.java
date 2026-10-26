package org.grit.daynomy.emailsubscription.external;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.emailsubscription.service.EmailDeliveryService;
import org.grit.daynomy.emailsubscription.service.EmailDeliveryService.Attempt;
import org.grit.daynomy.emailsubscription.service.EmailSendingService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class ResendEmailClientTest {
  private MockWebServer server;
  private ResendEmailClient client;
  private final Attempt attempt =
      new Attempt(UUID.randomUUID(), "member@example.invalid", "오늘 주목할 종목", "검증된 근거");

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    client = client(Duration.ofSeconds(2));
  }

  @AfterEach
  void tearDown() throws Exception {
    client.close();
    server.shutdown();
  }

  @Test
  void sendsExistingTemplateWithSameIdempotencyKeyAndMapsAcceptance() throws Exception {
    json(200, "{\"id\":\"provider-id\"}");
    json(200, "{\"id\":\"provider-id\"}");
    assertThat(client.send(attempt))
        .isEqualTo(new ResendEmailClient.Result(ResendEmailClient.Status.ACCEPTED, "provider-id"));
    client.send(attempt);
    var request = server.takeRequest();
    var retry = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/emails");
    assertThat(request.getHeader("Authorization")).isEqualTo("Bearer fake-key");
    assertThat(request.getHeader("Idempotency-Key")).isEqualTo("daynomy/" + attempt.deliveryId());
    assertThat(retry.getHeader("Idempotency-Key")).isEqualTo(request.getHeader("Idempotency-Key"));
    String body = request.getBody().readUtf8();
    assertThat(retry.getBody().readUtf8()).isEqualTo(body);
    var content = new ObjectMapper().readTree(body);
    assertThat(content.path("from").asText()).isEqualTo("DAYNOMY <notifications@mail.daynomy.com>");
    assertThat(content.path("to").get(0).asText()).isEqualTo(attempt.recipient());
    assertThat(content.path("subject").asText()).isEqualTo(attempt.subject());
    assertThat(content.path("text").asText()).isEqualTo(attempt.body());
    assertThat(content.size()).isEqualTo(4);
  }

  @ParameterizedTest
  @CsvSource({
    "rate_limit_exceeded, RETRYABLE_FAILURE",
    "daily_quota_exceeded, PERMANENT_FAILURE",
    "monthly_quota_exceeded, PERMANENT_FAILURE",
    "unexpected_error, UNKNOWN"
  })
  void distinguishesRateLimitsFromExhaustedQuotas(String name, ResendEmailClient.Status expected) {
    json(429, "{\"name\":\"" + name + "\"}");
    assertThat(client.send(attempt).status()).isEqualTo(expected);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 422})
  void doesNotRetryRejectedRequests(int status) {
    json(status, "{\"message\":\"private-provider-response\"}");
    assertThat(client.send(attempt))
        .isEqualTo(new ResendEmailClient.Result(ResendEmailClient.Status.PERMANENT_FAILURE, null));
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(ints = {408, 409, 500, 503})
  void keepsUncertainResponsesUnknownWithoutHiddenRetries(int status) {
    json(status, "{\"message\":\"private-provider-response\"}");
    assertThat(client.send(attempt).status()).isEqualTo(ResendEmailClient.Status.UNKNOWN);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "null", "{\"id\":12}", "{\"id\":\" \"}", "invalid-json"})
  void doesNotTreatMalformedSuccessAsAccepted(String body) {
    json(200, body);
    assertThat(client.send(attempt).status()).isEqualTo(ResendEmailClient.Status.UNKNOWN);
  }

  @Test
  void doesNotFollowRedirectsOrExposeAuthorizationToAnotherEndpoint() throws Exception {
    server.enqueue(
        new MockResponse().setResponseCode(307).setHeader("Location", server.url("/other")));
    assertThat(client.send(attempt).status()).isEqualTo(ResendEmailClient.Status.UNKNOWN);
    assertThat(server.getRequestCount()).isEqualTo(1);
    assertThat(server.takeRequest().getPath()).isEqualTo("/emails");
  }

  @Test
  void timeoutLeavesAcceptanceUnknownAndDoesNotRetry() throws Exception {
    client.close();
    client = client(Duration.ofMillis(100));
    server.enqueue(
        new MockResponse()
            .setHeadersDelay(400, TimeUnit.MILLISECONDS)
            .setBody("{\"id\":\"provider-id\"}"));
    assertThat(client.send(attempt).status()).isEqualTo(ResendEmailClient.Status.UNKNOWN);
    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void rejectsMissingConfigurationWithoutIncludingSecretInException() {
    assertThatThrownBy(
            () ->
                new ResendEmailClient(
                    RestClient.builder(),
                    "fake-secret",
                    "",
                    server.url("/").toString(),
                    Duration.ofSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageNotContaining("fake-secret");
  }

  @Test
  void apiKeyAloneDoesNotEnableSending() {
    context()
        .withPropertyValues("email.delivery.api-key=fake-key")
        .run(
            context -> {
              assertThat(context)
                  .doesNotHaveBean(ResendEmailClient.class)
                  .doesNotHaveBean(EmailSendingService.class);
            });
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void enabledSendingRequiresKeyAndCreatesNoRequestsOnStartup() {
    context()
        .withPropertyValues(
            "email.delivery.enabled=true", "email.delivery.from=notifications@mail.daynomy.com")
        .run(context -> assertThat(context).hasFailed());
    context()
        .withPropertyValues(
            "email.delivery.enabled=true",
            "email.delivery.api-key=fake-key",
            "email.delivery.from=notifications@mail.daynomy.com")
        .run(
            context ->
                assertThat(context)
                    .hasSingleBean(ResendEmailClient.class)
                    .hasSingleBean(EmailSendingService.class));
    assertThat(server.getRequestCount()).isZero();
  }

  private ApplicationContextRunner context() {
    return new ApplicationContextRunner()
        .withUserConfiguration(ResendEmailClient.class, EmailSendingService.class)
        .withBean(RestClient.Builder.class, RestClient::builder)
        .withBean(EmailDeliveryService.class, () -> mock(EmailDeliveryService.class));
  }

  private ResendEmailClient client(Duration timeout) {
    return new ResendEmailClient(
        RestClient.builder(),
        "fake-key",
        "DAYNOMY <notifications@mail.daynomy.com>",
        server.url("/").toString(),
        timeout);
  }

  private void json(int status, String body) {
    server.enqueue(
        new MockResponse()
            .setResponseCode(status)
            .setHeader("Content-Type", "application/json")
            .setBody(body));
  }
}
