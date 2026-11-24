package org.grit.daynomy.external.s3;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.ExternalTransportFailure;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.common.logging.SanitizedLogException;
import org.grit.daynomy.config.properties.S3Properties;
import org.grit.daynomy.external.ExternalErrorCode;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@RequiredArgsConstructor
@Component
public class S3ImageStorage {

  private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";
  private static final Pattern SAFE_PROVIDER_IDENTIFIER =
      Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

  private final S3Client s3Client;
  private final S3Properties s3Properties;

  public StoredImage upload(byte[] content, String extension, String contentType) {
    if (content == null || content.length == 0 || extension == null || extension.isBlank()) {
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    }

    String relativeKey = "%s.%s".formatted(UUID.randomUUID(), extension);
    String publicUrl = publicUrl(relativeKey);
    put(relativeKey, content, contentType);
    return new StoredImage(relativeKey, publicUrl);
  }

  private void put(String relativeKey, byte[] content, String contentType) {
    long startedAt = System.nanoTime();
    log.atDebug()
        .addKeyValue("event", LogEvent.EXTERNAL_API_REQUESTED.code())
        .addKeyValue("api", "S3")
        .addKeyValue("operation", "uploadImage")
        .addKeyValue("contentLength", content.length)
        .log(LogEvent.EXTERNAL_API_REQUESTED.message());
    try {
      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(bucket())
              .key(fullObjectKey(relativeKey))
              .contentType(contentType)
              .cacheControl(CACHE_CONTROL)
              .contentLength((long) content.length)
              .build(),
          RequestBody.fromBytes(content));
      log.atInfo()
          .addKeyValue("event", LogEvent.EXTERNAL_UPLOAD_COMPLETED.code())
          .addKeyValue("provider", "s3")
          .addKeyValue("relativeKey", relativeKey)
          .addKeyValue("contentLength", content.length)
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.EXTERNAL_UPLOAD_COMPLETED.message());
    } catch (S3Exception | SdkClientException exception) {
      var event =
          log.atWarn()
              .addKeyValue("event", LogEvent.EXTERNAL_UPLOAD_FAILED.code())
              .addKeyValue("provider", "s3")
              .addKeyValue("relativeKey", relativeKey)
              .addKeyValue("durationMs", elapsedMillis(startedAt))
              .addKeyValue("exceptionType", exception.getClass().getSimpleName())
              .setCause(SanitizedLogException.from(exception));
      addFailureDetails(event, exception);
      event.log(LogEvent.EXTERNAL_UPLOAD_FAILED.message());
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    } catch (BusinessException exception) {
      logConfigurationFailure(
          LogEvent.EXTERNAL_UPLOAD_FAILED,
          "uploadImage",
          "INVALID_STORAGE_CONFIGURATION",
          relativeKey);
      throw exception;
    }
  }

  public void delete(StoredImage storedImage) {
    if (storedImage == null) {
      return;
    }

    deleteByRelativeKey(storedImage.relativeKey());
  }

  public void deleteIfManaged(String publicUrl) {
    if (publicUrl == null || publicUrl.isBlank()) {
      return;
    }

    String baseUrl = s3Properties.publicBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      logConfigurationFailure(
          LogEvent.EXTERNAL_DELETE_FAILED, "deleteImage", "PUBLIC_BASE_URL_NOT_CONFIGURED", null);
      return;
    }

    String normalizedBaseUrl = removeTrailingSlash(baseUrl);
    String prefix = "%s/".formatted(normalizedBaseUrl);
    if (!publicUrl.startsWith(prefix)) {
      return;
    }

    deleteByRelativeKey(publicUrl.substring(prefix.length()));
  }

  private void deleteByRelativeKey(String relativeKey) {
    long startedAt = System.nanoTime();
    log.atDebug()
        .addKeyValue("event", LogEvent.EXTERNAL_API_REQUESTED.code())
        .addKeyValue("api", "S3")
        .addKeyValue("operation", "deleteImage")
        .log(LogEvent.EXTERNAL_API_REQUESTED.message());
    try {
      s3Client.deleteObject(
          DeleteObjectRequest.builder().bucket(bucket()).key(fullObjectKey(relativeKey)).build());
      log.atInfo()
          .addKeyValue("event", LogEvent.EXTERNAL_DELETE_COMPLETED.code())
          .addKeyValue("provider", "s3")
          .addKeyValue("relativeKey", relativeKey)
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.EXTERNAL_DELETE_COMPLETED.message());
    } catch (S3Exception | SdkClientException exception) {
      var event =
          log.atWarn()
              .addKeyValue("event", LogEvent.EXTERNAL_DELETE_FAILED.code())
              .addKeyValue("provider", "s3")
              .addKeyValue("relativeKey", relativeKey)
              .addKeyValue("durationMs", elapsedMillis(startedAt))
              .addKeyValue("exceptionType", exception.getClass().getSimpleName())
              .setCause(SanitizedLogException.from(exception));
      addFailureDetails(event, exception);
      event.log(LogEvent.EXTERNAL_DELETE_FAILED.message());
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    } catch (BusinessException exception) {
      logConfigurationFailure(
          LogEvent.EXTERNAL_DELETE_FAILED,
          "deleteImage",
          "INVALID_STORAGE_CONFIGURATION",
          relativeKey);
      throw exception;
    }
  }

  private String publicUrl(String relativeKey) {
    String baseUrl = s3Properties.publicBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      logConfigurationFailure(
          LogEvent.EXTERNAL_UPLOAD_FAILED,
          "uploadImage",
          "PUBLIC_BASE_URL_NOT_CONFIGURED",
          relativeKey);
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    }

    return "%s/%s".formatted(removeTrailingSlash(baseUrl), relativeFileName(relativeKey));
  }

  private String fullObjectKey(String relativeKey) {
    return "%s/%s".formatted(objectPrefix(), relativeFileName(relativeKey));
  }

  private String objectPrefix() {
    String objectPrefix = s3Properties.objectPrefix();
    if (objectPrefix == null
        || objectPrefix.isBlank()
        || objectPrefix.startsWith("/")
        || objectPrefix.endsWith("/")
        || objectPrefix.contains("\\")
        || objectPrefix.contains("//")) {
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    }
    return objectPrefix;
  }

  private String relativeFileName(String relativeKey) {
    if (relativeKey == null || relativeKey.isBlank()) {
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    }

    String fileName = removeLeadingSlash(relativeKey);
    if (fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    }

    return fileName;
  }

  private void logConfigurationFailure(
      LogEvent event, String operation, String reasonCode, String relativeKey) {
    var logEvent =
        log.atWarn()
            .addKeyValue("event", event.code())
            .addKeyValue("provider", "s3")
            .addKeyValue("operation", operation)
            .addKeyValue("reasonCode", reasonCode)
            .addKeyValue("retryable", false);
    if (relativeKey != null) {
      logEvent.addKeyValue("relativeKey", relativeKey);
    }
    logEvent.log(event.message());
  }

  private String bucket() {
    if (s3Properties.bucket() == null || s3Properties.bucket().isBlank()) {
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    }
    return s3Properties.bucket();
  }

  private String removeTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private String removeLeadingSlash(String value) {
    if (value == null) {
      return null;
    }
    return value.startsWith("/") ? value.substring(1) : value;
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  private void addFailureDetails(LoggingEventBuilder event, SdkException exception) {
    if (exception instanceof S3Exception s3Exception) {
      String errorCode =
          s3Exception.awsErrorDetails() == null
              ? null
              : safeIdentifier(s3Exception.awsErrorDetails().errorCode());
      event
          .addKeyValue("responseStatus", s3Exception.statusCode())
          .addKeyValue("failureCategory", s3FailureCategory(s3Exception.statusCode(), errorCode))
          .addKeyValue("retryable", s3Exception.retryable());
      if (errorCode != null) {
        event.addKeyValue("providerErrorCode", errorCode);
      }
      String requestId = safeIdentifier(s3Exception.requestId());
      if (requestId != null) {
        event.addKeyValue("providerRequestId", requestId);
      }
      return;
    }
    ExternalTransportFailure.from(exception).addTo(event);
  }

  private String s3FailureCategory(int status, String errorCode) {
    if ("SlowDown".equals(errorCode)) {
      return "RATE_LIMITED";
    }
    return switch (status) {
      case 401 -> "AUTHENTICATION_FAILED";
      case 403 -> "PERMISSION_DENIED";
      case 404 -> "NOT_FOUND";
      case 408 -> "REQUEST_TIMEOUT";
      case 429 -> "RATE_LIMITED";
      default -> status >= 500 ? "SERVER_ERROR" : "PROVIDER_ERROR";
    };
  }

  private String safeIdentifier(String value) {
    return value != null && SAFE_PROVIDER_IDENTIFIER.matcher(value).matches() ? value : null;
  }

  public record StoredImage(String relativeKey, String publicUrl) {}
}
