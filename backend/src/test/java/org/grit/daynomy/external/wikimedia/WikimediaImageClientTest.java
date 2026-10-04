package org.grit.daynomy.external.wikimedia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WikimediaImageClientTest {

  private MockWebServer server;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
  }

  @AfterEach
  void tearDown() throws Exception {
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
  void downloadRejectsImageLargerThan5MiB() {
    server.enqueue(
        new MockResponse().setHeader("Content-Type", "application/json").setBody(searchResponse()));
    server.enqueue(new MockResponse().setBody(new Buffer().write(new byte[5 * 1024 * 1024 + 1])));

    WikimediaImageClient client = newClient();

    assertThatThrownBy(() -> client.download("File:Seoul skyline.jpg"))
        .isInstanceOf(BusinessException.class)
        .hasMessage(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED.message());
  }

  private WikimediaImageClient newClient() {
    return new WikimediaImageClient(
        new WikimediaProperties(
            server.url("/").toString(),
            "daynomy-test/1.0",
            Duration.ofSeconds(1),
            Duration.ofSeconds(1)));
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
