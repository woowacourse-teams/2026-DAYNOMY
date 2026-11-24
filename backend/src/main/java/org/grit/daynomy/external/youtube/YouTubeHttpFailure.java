package org.grit.daynomy.external.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.web.client.HttpStatusCodeException;

record YouTubeHttpFailure(
    Category category,
    int httpStatus,
    String providerErrorReason,
    String providerErrorStatus,
    boolean retryable) {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Pattern SAFE_IDENTIFIER =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
  private static final Set<String> QUOTA_REASONS =
      Set.of("quotaExceeded", "dailyLimitExceeded", "dailyLimitExceededUnreg");
  private static final Set<String> RATE_LIMIT_REASONS =
      Set.of("rateLimitExceeded", "userRateLimitExceeded");

  static YouTubeHttpFailure from(HttpStatusCodeException exception) {
    int status = exception.getStatusCode().value();
    JsonNode error = errorNode(exception);
    String reason = safeIdentifier(error.path("errors").path(0).path("reason"));
    String providerStatus = safeIdentifier(error.path("status"));
    Category category = category(status, reason);
    return new YouTubeHttpFailure(
        category, status, reason, providerStatus, retryable(category, status));
  }

  void addTo(LoggingEventBuilder event) {
    event
        .addKeyValue("responseStatus", httpStatus)
        .addKeyValue("failureCategory", category.name())
        .addKeyValue("retryable", retryable);
    if (providerErrorReason != null) {
      event.addKeyValue("providerErrorReason", providerErrorReason);
    }
    if (providerErrorStatus != null) {
      event.addKeyValue("providerErrorStatus", providerErrorStatus);
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
    return SAFE_IDENTIFIER.matcher(value).matches() ? value : null;
  }

  private static Category category(int status, String reason) {
    if (reason != null && QUOTA_REASONS.contains(reason)) {
      return Category.QUOTA_EXHAUSTED;
    }
    if (reason != null && RATE_LIMIT_REASONS.contains(reason)) {
      return Category.RATE_LIMITED;
    }
    if ("keyInvalid".equals(reason)) {
      return Category.AUTHENTICATION_FAILED;
    }
    if ("accessNotConfigured".equals(reason) || "forbidden".equals(reason)) {
      return Category.PERMISSION_DENIED;
    }
    return switch (status) {
      case 400 -> Category.INVALID_REQUEST;
      case 401 -> Category.AUTHENTICATION_FAILED;
      case 403 -> Category.PERMISSION_DENIED;
      case 404 -> Category.NOT_FOUND;
      case 408 -> Category.REQUEST_TIMEOUT;
      case 429 -> Category.RATE_LIMITED;
      default -> status >= 500 ? Category.SERVER_ERROR : Category.UNKNOWN;
    };
  }

  private static boolean retryable(Category category, int status) {
    return switch (category) {
      case RATE_LIMITED, REQUEST_TIMEOUT, SERVER_ERROR -> true;
      case UNKNOWN -> status >= 500;
      default -> false;
    };
  }

  enum Category {
    QUOTA_EXHAUSTED,
    RATE_LIMITED,
    AUTHENTICATION_FAILED,
    PERMISSION_DENIED,
    INVALID_REQUEST,
    NOT_FOUND,
    REQUEST_TIMEOUT,
    SERVER_ERROR,
    UNKNOWN
  }
}
