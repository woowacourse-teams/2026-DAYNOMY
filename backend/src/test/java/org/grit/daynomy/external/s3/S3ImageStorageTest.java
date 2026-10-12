package org.grit.daynomy.external.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.HashMap;
import java.util.Map;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.config.properties.S3Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
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
  private final Logger logger = (Logger) LoggerFactory.getLogger(S3ImageStorage.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private Level originalLevel;

  @BeforeEach
  void setUp() {
    storage =
        new S3ImageStorage(
            s3Client,
            new S3Properties(
                REGION,
                BUCKET,
                "daynomy",
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy"));
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
  }

  @Test
  void uploadSendsWebpMetadataToS3() {
    byte[] content = {1, 2, 3};

    S3ImageStorage.StoredImage storedImage = storage.upload(content, "webp", "image/webp");

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
    verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());

    PutObjectRequest request = requestCaptor.getValue();
    assertThat(request.bucket()).isEqualTo(BUCKET);
    assertThat(request.key()).startsWith("daynomy/").endsWith(".webp");
    assertThat(request.contentType()).isEqualTo("image/webp");
    assertThat(request.cacheControl()).isEqualTo("public, max-age=31536000, immutable");
    assertThat(request.contentLength()).isEqualTo(3L);
    assertThat(bodyCaptor.getValue().contentLength()).isEqualTo(3L);
    assertThat(storedImage.relativeKey()).endsWith(".webp");
    assertThat(storedImage.publicUrl())
        .startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy/");
  }

  @Test
  void uploadUsesConfiguredObjectPrefix() {
    storage =
        new S3ImageStorage(
            s3Client,
            new S3Properties(
                REGION,
                BUCKET,
                "daynomy-dev",
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/daynomy-dev"));

    storage.upload(new byte[] {1}, "webp", "image/webp");

    ArgumentCaptor<PutObjectRequest> requestCaptor =
        ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

    assertThat(requestCaptor.getValue().key()).startsWith("daynomy-dev/");
  }

  @Test
  void deleteSendsObjectKeyToS3() {
    storage.delete(
        new S3ImageStorage.StoredImage(
            "news-image.webp", "https://cdn.example.com/news-image.webp"));

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
            s3Client,
            new S3Properties(REGION, BUCKET, "daynomy", "https://images.example.com/daynomy"));

    S3ImageStorage.StoredImage storedImage = storage.upload(new byte[] {1}, "webp", "image/webp");

    assertThat(storedImage.publicUrl()).startsWith("https://images.example.com/daynomy/");
  }

  @Test
  void publicUrlFailsWhenBaseUrlIsMissing() {
    storage = new S3ImageStorage(s3Client, new S3Properties(REGION, BUCKET, "daynomy", ""));

    assertThatThrownBy(() -> storage.upload(new byte[] {1}, "webp", "image/webp"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void rejectsNestedObjectKey() {
    assertThatThrownBy(() -> storage.upload(new byte[] {1}, "news/image.webp", "image/webp"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void uploadConvertsS3FailureToBusinessException() {
    given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .willThrow(S3Exception.builder().message("access denied").build());

    assertThatThrownBy(() -> storage.upload(new byte[] {1}, "webp", "image/webp"))
        .isInstanceOf(BusinessException.class);

    ILoggingEvent log = appender.list.getFirst();
    assertThat(log.getLevel()).isEqualTo(Level.WARN);
    assertThat(log.getFormattedMessage()).isEqualTo(LogEvent.EXTERNAL_UPLOAD_FAILED.message());
    assertThat(keyValues(log))
        .containsEntry("event", LogEvent.EXTERNAL_UPLOAD_FAILED.code())
        .containsEntry("provider", "s3")
        .containsKey("relativeKey");
    assertThat(keyValues(log).get("relativeKey").toString()).endsWith(".webp");
    assertThat(log.getThrowableProxy()).isNotNull();
  }

  @Test
  void deleteConvertsS3FailureToBusinessExceptionAndLogsStructuredFailure() {
    given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
        .willThrow(S3Exception.builder().message("access denied").build());

    assertThatThrownBy(
            () ->
                storage.delete(
                    new S3ImageStorage.StoredImage(
                        "news-image.webp", "https://cdn.example.com/news-image.webp")))
        .isInstanceOf(BusinessException.class);

    ILoggingEvent log = appender.list.getFirst();
    assertThat(log.getLevel()).isEqualTo(Level.WARN);
    assertThat(log.getFormattedMessage()).isEqualTo(LogEvent.EXTERNAL_DELETE_FAILED.message());
    assertThat(keyValues(log))
        .containsEntry("event", LogEvent.EXTERNAL_DELETE_FAILED.code())
        .containsEntry("provider", "s3")
        .containsEntry("relativeKey", "news-image.webp");
    assertThat(log.getThrowableProxy()).isNotNull();
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }
}
