package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpStatusCodeException;

/** OpenAI 오류 응답에서 로그에 안전한 식별자와 재시도 판단만 추출한다. */
public record OpenAiHttpFailure(
    Category category,
    int httpStatus,
    String providerErrorCode,
    String providerErrorType,
    boolean retryable,
    Long retryAfterSeconds) {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Pattern SAFE_PROVIDER_IDENTIFIER =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
  private static final Set<String> QUOTA_ERROR_CODES =
      Set.of("credit_balance_exhausted", "insufficient_quota", "billing_hard_limit_reached");
  private static final Set<String> SPEND_LIMIT_ERROR_CODES =
      Set.of("organization_spend_limit_exceeded", "project_spend_limit_exceeded");
  private static final Set<String> USAGE_LIMIT_ERROR_CODES =
      Set.of("organization_usage_limit_exceeded");

  public static OpenAiHttpFailure from(HttpStatusCodeException exception) {
    return from(exception, Clock.systemUTC());
  }

  static OpenAiHttpFailure from(HttpStatusCodeException exception, Clock clock) {
    int status = exception.getStatusCode().value();
    JsonNode error = errorNode(exception);
    String providerErrorCode = safeIdentifier(error.path("code"));
    String providerErrorType = safeIdentifier(error.path("type"));
    Category category = category(status, providerErrorCode, providerErrorType);
    boolean retryable = retryable(category, status);
    Long retryAfterSeconds = retryAfterSeconds(exception.getResponseHeaders(), clock);
    return new OpenAiHttpFailure(
        category, status, providerErrorCode, providerErrorType, retryable, retryAfterSeconds);
  }

  public void addTo(LoggingEventBuilder event) {
    event
        .addKeyValue("httpStatus", httpStatus)
        .addKeyValue("failureCategory", category.name())
        .addKeyValue("retryable", retryable);
    if (providerErrorCode != null) {
      event.addKeyValue("providerErrorCode", providerErrorCode);
    }
    if (providerErrorType != null) {
      event.addKeyValue("providerErrorType", providerErrorType);
    }
    if (retryAfterSeconds != null) {
      event.addKeyValue("retryAfterSeconds", retryAfterSeconds);
    }
  }

  private static JsonNode errorNode(HttpStatusCodeException exception) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(exception.getResponseBodyAsByteArray());
      JsonNode error = root == null ? null : root.get("error");
      return error == null || !error.isObject() ? OBJECT_MAPPER.createObjectNode() : error;
    } catch (Exception ignored) {
      return OBJECT_MAPPER.createObjectNode();
    }
  }

  private static String safeIdentifier(JsonNode node) {
    if (!node.isTextual()) {
      return null;
    }
    String value = node.textValue();
    if (!SAFE_PROVIDER_IDENTIFIER.matcher(value).matches()) {
      return null;
    }
    return value;
  }

  private static Category category(int status, String code, String type) {
    if (code != null && SPEND_LIMIT_ERROR_CODES.contains(code)) {
      return Category.SPEND_LIMIT_EXCEEDED;
    }
    if (code != null && USAGE_LIMIT_ERROR_CODES.contains(code)) {
      return Category.USAGE_LIMIT_EXCEEDED;
    }
    if ((code != null && QUOTA_ERROR_CODES.contains(code)) || "insufficient_quota".equals(type)) {
      return Category.QUOTA_EXHAUSTED;
    }
    if ("server_is_overloaded".equals(code) || "service_unavailable_error".equals(type)) {
      return Category.SERVER_ERROR;
    }
    if ("rate_limit_error".equals(type) || "rate_limit_exceeded".equals(code)) {
      return Category.RATE_LIMITED;
    }
    return switch (status) {
      case 400, 422 -> Category.INVALID_REQUEST;
      case 401 -> Category.AUTHENTICATION_FAILED;
      case 403 -> Category.PERMISSION_DENIED;
      case 404 -> Category.NOT_FOUND;
      case 408 -> Category.REQUEST_TIMEOUT;
      case 409 -> Category.CONFLICT;
      case 429 -> Category.RATE_LIMITED;
      default -> status >= 500 ? Category.SERVER_ERROR : Category.UNKNOWN;
    };
  }

  private static boolean retryable(Category category, int status) {
    return switch (category) {
      case QUOTA_EXHAUSTED,
              SPEND_LIMIT_EXCEEDED,
              USAGE_LIMIT_EXCEEDED,
              AUTHENTICATION_FAILED,
              PERMISSION_DENIED,
              INVALID_REQUEST,
              NOT_FOUND ->
          false;
      case RATE_LIMITED, REQUEST_TIMEOUT, CONFLICT, SERVER_ERROR -> true;
      case UNKNOWN -> status >= 500;
    };
  }

  private static Long retryAfterSeconds(HttpHeaders headers, Clock clock) {
    if (headers == null) {
      return null;
    }
    String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      long seconds = Long.parseLong(value.trim());
      return seconds < 0 ? null : seconds;
    } catch (NumberFormatException ignored) {
      try {
        var retryAt = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
        long seconds = Duration.between(clock.instant(), retryAt.toInstant()).getSeconds();
        return Math.max(0, seconds);
      } catch (DateTimeParseException invalidDate) {
        return null;
      }
    }
  }

  public enum Category {
    QUOTA_EXHAUSTED,
    SPEND_LIMIT_EXCEEDED,
    USAGE_LIMIT_EXCEEDED,
    RATE_LIMITED,
    AUTHENTICATION_FAILED,
    PERMISSION_DENIED,
    INVALID_REQUEST,
    NOT_FOUND,
    REQUEST_TIMEOUT,
    CONFLICT,
    SERVER_ERROR,
    UNKNOWN
  }
}
