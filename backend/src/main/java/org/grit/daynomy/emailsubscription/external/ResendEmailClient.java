package org.grit.daynomy.emailsubscription.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.grit.daynomy.emailsubscription.service.EmailDeliveryService.Attempt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(name = "email.delivery.enabled", havingValue = "true")
public class ResendEmailClient {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final RestClient client;
  private final HttpComponentsClientHttpRequestFactory factory;
  private final String apiKey;
  private final String from;

  public enum Status {
    ACCEPTED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE,
    UNKNOWN
  }

  public record Result(Status status, String messageId) {}

  @Autowired
  public ResendEmailClient(
      RestClient.Builder builder,
      @Value("${email.delivery.api-key:}") String apiKey,
      @Value("${email.delivery.from:}") String from) {
    this(builder, apiKey, from, "https://api.resend.com", Duration.ofSeconds(10));
  }

  ResendEmailClient(
      RestClient.Builder builder, String apiKey, String from, String baseUrl, Duration timeout) {
    if (apiKey == null || apiKey.isBlank() || from == null || from.isBlank())
      throw new IllegalArgumentException("이메일 발송에는 API 키와 발신 주소 설정이 필요합니다.");
    this.apiKey = apiKey;
    this.from = from;
    // HTTP 내부 재시도·리다이렉트는 끄고 발송 이력의 실행권과 재시도 규칙만 사용한다.
    this.factory =
        new HttpComponentsClientHttpRequestFactory(
            HttpClients.custom()
                .setConnectionManager(
                    PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(
                            ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(3))
                                .setSocketTimeout(Timeout.ofMilliseconds(timeout.toMillis()))
                                .build())
                        .build())
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement()
                .build());
    factory.setConnectionRequestTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(timeout);
    this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
  }

  @PreDestroy
  public void close() throws Exception {
    factory.destroy();
  }

  public Result send(Attempt attempt) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("from", from);
    payload.put("to", List.of(attempt.recipient()));
    payload.put("subject", attempt.subject());
    payload.put("text", attempt.body());
    if (attempt.htmlBody() != null && !attempt.htmlBody().isBlank())
      payload.put("html", attempt.htmlBody());
    payload.put(
        "headers",
        Map.of(
            "List-Unsubscribe",
            "<" + attempt.unsubscribeUrl() + ">",
            "List-Unsubscribe-Post",
            "List-Unsubscribe=One-Click"));
    try {
      return client
          .post()
          .uri("/emails")
          .header("Authorization", "Bearer " + apiKey)
          .header("Idempotency-Key", "daynomy/" + attempt.deliveryId())
          .contentType(MediaType.APPLICATION_JSON)
          .body(payload)
          .exchange(
              (request, response) -> {
                int status = response.getStatusCode().value();
                if (status == 200) {
                  var body = JSON.readTree(response.getBody());
                  var id = body == null ? null : body.get("id");
                  if (id != null
                      && id.isTextual()
                      && !id.asText().isBlank()
                      && id.asText().length() <= 255)
                    return new Result(Status.ACCEPTED, id.asText());
                  return new Result(Status.UNKNOWN, null);
                }
                if (status == 429) {
                  var body = JSON.readTree(response.getBody());
                  String name = body == null ? "" : body.path("name").asText();
                  if ("rate_limit_exceeded".equals(name))
                    return new Result(Status.RETRYABLE_FAILURE, null);
                  if ("daily_quota_exceeded".equals(name) || "monthly_quota_exceeded".equals(name))
                    return new Result(Status.PERMANENT_FAILURE, null);
                  return new Result(Status.UNKNOWN, null);
                }
                // 타임아웃·중복 요청 충돌·5xx는 제공자가 접수했을 가능성을 배제할 수 없다.
                if (status >= 400 && status < 500 && status != 408 && status != 409)
                  return new Result(Status.PERMANENT_FAILURE, null);
                return new Result(Status.UNKNOWN, null);
              });
    } catch (RestClientException failure) {
      // 예외에 포함될 수 있는 키·이메일·본문·제공자 응답을 보관하거나 로깅하지 않는다.
      return new Result(Status.UNKNOWN, null);
    }
  }
}
