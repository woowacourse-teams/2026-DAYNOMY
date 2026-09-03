package org.grit.daynomy.external.s3;

import java.util.UUID;
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
    try {
      s3Client.deleteObject(
              DeleteObjectRequest.builder().bucket(bucket()).key(fullObjectKey(relativeKey)).build());
    } catch (S3Exception | SdkClientException exception) {
      log.warn("S3 image deletion failed: relativeKey={}", relativeKey, exception);
      throw new BusinessException(ExternalErrorCode.S3_IMAGE_STORAGE_FAILED);
    }
  }

  private String publicUrl(String relativeKey) {
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

  public record StoredImage(String relativeKey, String publicUrl) {}
}
