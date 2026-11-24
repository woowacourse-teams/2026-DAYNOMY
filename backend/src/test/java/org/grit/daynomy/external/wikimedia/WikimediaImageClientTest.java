package org.grit.daynomy.external.wikimedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpResponse;

class WikimediaImageClientTest {

  private MockWebServer server;
  private final Logger logger = (Logger) LoggerFactory.getLogger(WikimediaImageClient.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private Level originalLevel;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() throws Exception {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
    server.shutdown();
  }

  @Test
  void searchReturnsOnlySupportedCommerciallyUsableImages() throws Exception {
    server.enqueue(
        new MockResponse().setHeader("Content-Type", "application/json").setBody(searchResponse()));

    WikimediaImageClient client = newClient();

    var candidates = client.search("Seoul skyline");

    assertThat(candidates)
        .extracting(WikimediaImageCandidate::title)
        .containsExactlyInAnyOrder("File:Seoul skyline.jpg", "File:CC0.jpg");
    assertThat(candidates)
        .anySatisfy(
            candidate -> {
              assertThat(candidate.title()).isEqualTo("File:Seoul skyline.jpg");
              assertThat(candidate.author()).isEqualTo("Jane Doe");
              assertThat(candidate.license()).isEqualTo("CC BY 4.0");
              assertThat(candidate.licenseUrl())
                  .isEqualTo("https://creativecommons.org/licenses/by/4.0/");
              assertThat(candidate.width()).isEqualTo(1_200);
              assertThat(candidate.height()).isEqualTo(800);
            });
    assertThat(candidates)
        .anySatisfy(
            candidate -> {
              assertThat(candidate.title()).isEqualTo("File:CC0.jpg");
              assertThat(candidate.license()).isEqualTo("CC0 1.0");
            });

    RecordedRequest request = server.takeRequest();
    assertThat(request.getHeader("User-Agent")).isEqualTo("daynomy-test/1.0");
    assertThat(request.getRequestUrl().queryParameter("gsrsearch")).isEqualTo("Seoul skyline");
    assertThat(request.getRequestUrl().queryParameter("gsrnamespace")).isEqualTo("6");
    assertThat(request.getRequestUrl().queryParameter("gsrlimit")).isEqualTo("50");
  }

  @Test
  void blankSearchDoesNotCallWikimedia() {
    WikimediaImageClient client = newClient();

    assertThat(client.search("  ")).isEmpty();
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void searchConvertsHttpError() {
    server.enqueue(new MockResponse().setResponseCode(503));

    assertWikimediaRequestFailed(() -> newClient().search("Seoul"));
  }

  @Test
  void searchConvertsTimeout() {
    server.enqueue(
        new MockResponse().setHeadersDelay(500, TimeUnit.MILLISECONDS).setBody(searchResponse()));

    assertWikimediaRequestFailed(() -> newClient(Duration.ofMillis(100)).search("Seoul"));
    assertThat(keyValues(logEvent(LogEvent.EXTERNAL_API_FAILED)))
        .containsEntry("failureCategory", "TIMEOUT")
        .containsEntry("retryable", true);
  }

  @Test
  void searchConvertsMalformedResponse() {
    server.enqueue(new MockResponse().setBody("not-json"));

    assertWikimediaRequestFailed(() -> newClient().search("Seoul"));
  }

  @Test
  void downloadRejectsImageLargerThan5MiB() {
    server.enqueue(
        new MockResponse().setHeader("Content-Type", "application/json").setBody(searchResponse()));
    server.enqueue(new MockResponse().setBody(new Buffer().write(new byte[5 * 1024 * 1024 + 1])));

    WikimediaImageClient client = newClient();

    assertThatThrownBy(() -> client.download("File:Seoul skyline.jpg"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED.message());
  }

  @Test
  void downloadLogsHttpStatusWhenCandidateLookupFails() {
    server.enqueue(new MockResponse().setResponseCode(503));

    assertWikimediaRequestFailed(() -> newClient().download("File:Seoul skyline.jpg"));

    assertThat(keyValues(logEvent(LogEvent.EXTERNAL_API_FAILED)))
        .containsEntry("operation", "downloadImage")
        .containsEntry("responseStatus", 503);
  }

  @Test
  void downloadHttpFailurePreservesResponseStatus() throws Exception {
    var response = new MockClientHttpResponse(new byte[0], HttpStatus.SERVICE_UNAVAILABLE);

    var exception = WikimediaImageClient.httpFailure(response);

    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @Test
  void resolveContentTypeUsesDownloadedBytesBeforeResponseHeader() {
    byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    assertThat(WikimediaImageClient.resolveContentType(MediaType.IMAGE_JPEG, png))
        .isEqualTo(MediaType.IMAGE_PNG_VALUE);
  }

  private WikimediaImageClient newClient() {
    return newClient(Duration.ofSeconds(1));
  }

  private WikimediaImageClient newClient(Duration readTimeout) {
    return new WikimediaImageClient(
        new WikimediaProperties(
            server.url("/").toString(), "daynomy-test/1.0", Duration.ofSeconds(1), readTimeout),
        org.springframework.web.client.RestClient.builder());
  }

  private void assertWikimediaRequestFailed(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED));
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }

  private ILoggingEvent logEvent(LogEvent event) {
    return appender.list.stream()
        .filter(loggingEvent -> event.code().equals(keyValues(loggingEvent).get("event")))
        .findFirst()
        .orElseThrow();
  }

  private String searchResponse() {
    return """
{
  "query": {
    "pages": [
      {
        "title": "File:Seoul skyline.jpg",
        "imageinfo": [{
          "url": "https://upload.wikimedia.org/wikipedia/commons/a/ab/Seoul_skyline.jpg",
          "thumburl": "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/Seoul_skyline.jpg/1200px-Seoul_skyline.jpg",
          "mime": "image/jpeg",
          "width": 1200,
          "height": 800,
          "extmetadata": {
            "Artist": {"value": "<b>Jane Doe</b>"},
            "LicenseShortName": {"value": "CC BY 4.0"},
            "LicenseUrl": {"value": "https://creativecommons.org/licenses/by/4.0/"}
          }
        }]
      },
      {
        "title": "File:CC0.jpg",
        "imageinfo": [{
          "thumburl": "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/CC0.jpg/1200px-CC0.jpg",
          "mime": "image/jpeg",
          "width": 1200,
          "height": 800,
          "extmetadata": {"LicenseShortName": {"value": "CC0 1.0"}}
        }]
      },
      {
        "title": "File:Share alike.jpg",
        "imageinfo": [{
          "thumburl": "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Share_alike.jpg/1200px-Share_alike.jpg",
          "mime": "image/jpeg",
          "width": 1200,
          "height": 800,
          "extmetadata": {"LicenseShortName": {"value": "CC BY-SA 4.0"}}
        }]
      },
      {
        "title": "File:Animated.gif",
        "imageinfo": [{
          "thumburl": "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Animated.gif/1200px-Animated.gif",
          "mime": "image/gif",
          "width": 1200,
          "height": 800,
          "extmetadata": {"LicenseShortName": {"value": "CC0"}}
        }]
      },
      {
        "title": "File:Noncommercial.jpg",
        "imageinfo": [{
          "thumburl": "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Noncommercial.jpg/1200px-Noncommercial.jpg",
          "mime": "image/jpeg",
          "width": 1200,
          "height": 800,
          "extmetadata": {"LicenseShortName": {"value": "CC BY-NC 4.0"}}
        }]
      },
      {
        "title": "File:Not public domain.jpg",
        "imageinfo": [{
          "thumburl": "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Not_public_domain.jpg/1200px-Not_public_domain.jpg",
          "mime": "image/jpeg",
          "width": 1200,
          "height": 800,
          "extmetadata": {"LicenseShortName": {"value": "Not public domain"}}
        }]
      },
      {
        "title": "File:Composite license.jpg",
        "imageinfo": [{
          "thumburl": "https://upload.wikimedia.org/wikipedia/commons/thumb/a/ab/Composite_license.jpg/1200px-Composite_license.jpg",
          "mime": "image/jpeg",
          "width": 1200,
          "height": 800,
          "extmetadata": {"LicenseShortName": {"value": "CC BY-SA 3.0 + GFDL"}}
        }]
      }
    ]
  }
}
""";
  }
}
