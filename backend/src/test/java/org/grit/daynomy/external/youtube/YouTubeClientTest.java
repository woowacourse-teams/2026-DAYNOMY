package org.grit.daynomy.external.youtube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YouTubeClientTest {

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
  void searchParsesVideoCandidates() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "items": [
                    {
                      "id": {"kind": "youtube#video", "videoId": "abc123"},
                      "snippet": {
                        "title": "삼성전자 분석",
                        "channelTitle": "DAYNOMY",
                        "publishedAt": "2026-10-04T00:00:00Z",
                        "thumbnails": {"high": {"url": "https://i.ytimg.com/high.jpg"}}
                      }
                    }
                  ]
                }
                """));

    YouTubeClient client =
        new YouTubeClient(
            new YouTubeProperties(
                "test-api-key",
                server.url("/").toString(),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)));

    var candidates = client.search("삼성전자 005930");

    assertThat(candidates)
        .singleElement()
        .satisfies(
            candidate -> {
              assertThat(candidate.title()).isEqualTo("삼성전자 분석");
              assertThat(candidate.url()).isEqualTo("https://www.youtube.com/watch?v=abc123");
              assertThat(candidate.channelTitle()).isEqualTo("DAYNOMY");
              assertThat(candidate.thumbnailUrl()).isEqualTo("https://i.ytimg.com/high.jpg");
            });

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestUrl().queryParameter("q")).isEqualTo("삼성전자 005930");
    assertThat(request.getRequestUrl().queryParameter("type")).isEqualTo("video");
    assertThat(request.getRequestUrl().queryParameter("order")).isEqualTo("date");
    assertThat(request.getRequestUrl().queryParameter("maxResults")).isEqualTo("10");
    assertThat(request.getRequestUrl().queryParameter("key")).isEqualTo("test-api-key");
  }

  @Test
  void blankSearchDoesNotCallYouTube() {
    YouTubeClient client = newClient("test-api-key");

    assertThat(client.search("  ")).isEmpty();
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void missingApiKeyIsReportedWithoutCallingYouTube() {
    YouTubeClient client = newClient("");

    assertThatThrownBy(() -> client.search("삼성전자"))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ExternalErrorCode.YOUTUBE_API_NOT_CONFIGURED);
    assertThat(server.getRequestCount()).isZero();
  }

  private YouTubeClient newClient(String apiKey) {
    return new YouTubeClient(
        new YouTubeProperties(
            apiKey, server.url("/").toString(), Duration.ofSeconds(1), Duration.ofSeconds(1)));
  }
}
