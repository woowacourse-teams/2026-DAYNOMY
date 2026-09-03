package org.grit.daynomy.external.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.config.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageTest {

  private static final String BUCKET = "test-bucket";
  private static final String REGION = "ap-northeast-2";

  @Mock private S3Client s3Client;

  private S3ImageStorage storage;

  @BeforeEach
  void setUp() {
    storage =
        new S3ImageStorage(
            s3Client,
            new S3Properties(
                REGION, BUCKET, "https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy"));
  }

  @Test
  void putSendsWebpMetadataToS3() {
    byte[] content = {1, 2, 3};

    storage.put("news-image.webp", content, "image/webp");

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
    verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

    PutObjectRequest request = requestCaptor.getValue();
    assertThat(request.bucket()).isEqualTo(BUCKET);
    assertThat(request.key()).isEqualTo("daynomy/news-image.webp");
    assertThat(request.contentType()).isEqualTo("image/webp");
    assertThat(request.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
    assertThat(request.contentLength()).isEqualTo(3L);
    assertThat(bodyCaptor.getValue().contentLength()).isEqualTo(3L);
  }

  @Test
  void deleteSendsObjectKeyToS3() {
    storage.delete("news-image.webp");

    ArgumentCaptor<DeleteObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3Client).deleteObject(requestCaptor.capture());

    DeleteObjectRequest request = requestCaptor.getValue();
    assertThat(request.bucket()).isEqualTo(BUCKET);
    assertThat(request.key()).isEqualTo("daynomy/news-image.webp");
  }

  @Test
  void deleteIfManagedDeletesImageFromConfiguredBaseUrl() {
    storage.deleteIfManaged(
        "https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/news-image.webp");

    ArgumentCaptor<DeleteObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(DeleteObjectRequest.class);
    verify(s3Client).deleteObject(requestCaptor.capture());

    assertThat(requestCaptor.getValue().key()).isEqualTo("daynomy/news-image.webp");
  }

  @Test
  void deleteIfManagedIgnoresExternalImageUrl() {
    storage.deleteIfManaged("https://example.com/news-image.webp");

    org.mockito.Mockito.verifyNoInteractions(s3Client);
  }

  @Test
  void publicUrlUsesConfiguredBaseUrl() {
    storage =
        new S3ImageStorage(
            s3Client, new S3Properties(REGION, BUCKET, "https://images.example.com/daynomy"));

    String publicUrl = storage.publicUrl("/news-image.webp");

    assertThat(publicUrl).isEqualTo("https://images.example.com/daynomy/news-image.webp");
  }

  @Test
  void publicUrlFailsWhenBaseUrlIsMissing() {
    storage = new S3ImageStorage(s3Client, new S3Properties(REGION, BUCKET, ""));

    assertThatThrownBy(() -> storage.publicUrl("news-image.webp"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void rejectsNestedObjectKey() {
    assertThatThrownBy(() -> storage.put("news/image.webp", new byte[] {1}, "image/webp"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void putConvertsS3FailureToBusinessException() {
    given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .willThrow(S3Exception.builder().message("access denied").build());

    assertThatThrownBy(() -> storage.put("news-image.webp", new byte[] {1}, "image/webp"))
        .isInstanceOf(BusinessException.class);
  }
}
