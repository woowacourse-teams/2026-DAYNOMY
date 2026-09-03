package org.grit.daynomy.external.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.config.properties.S3Properties;
import org.grit.daynomy.external.ExternalErrorCode;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@RequiredArgsConstructor
@Component
public class S3ImageStorage {

  private static final String OBJECT_PREFIX = "daynomy";
  private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

  private final S3Client s3Client;
  private final S3Properties s3Properties;

  public void put(String relativeKey, byte[] content, String contentType) {
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
    } catch (S3Exception | SdkClientException exception) {
      log.warn("S3 image upload failed: relativeKey={}", relativeKey, exception);
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    }
  }

  public void delete(String relativeKey) {
    try {
      s3Client.deleteObject(
          DeleteObjectRequest.builder().bucket(bucket()).key(fullObjectKey(relativeKey)).build());
    } catch (S3Exception | SdkClientException exception) {
      log.warn("S3 image deletion failed: relativeKey={}", relativeKey, exception);
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    }
  }

  public String publicUrl(String relativeKey) {
    String baseUrl = s3Properties.publicBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    }

    return "%s/%s".formatted(removeTrailingSlash(baseUrl), relativeFileName(relativeKey));
  }

  private String fullObjectKey(String relativeKey) {
    return "%s/%s".formatted(OBJECT_PREFIX, relativeFileName(relativeKey));
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
}
