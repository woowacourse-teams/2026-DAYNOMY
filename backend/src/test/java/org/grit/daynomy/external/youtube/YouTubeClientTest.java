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
  void profileSearchResolvesHandleAndRestrictsVideosToChannel() throws Exception {
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("{\"items\":[{\"id\":\"channel-123\"}]}"));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "nextPageToken": "next-page",
                  "items": [
                    {
                      "id": {"videoId": "abcdefghijk"},
                      "snippet": {
                        "title": "공식 채널 영상",
                        "channelTitle": "Tesla",
                        "publishedAt": "2026-10-12T00:00:00Z",
                        "thumbnails": {}
                      }
                    },
                    {
                      "id": {"videoId": "zzzzzzzzzzz"},
                      "snippet": {
                        "title": "기간 밖 영상",
                        "channelTitle": "Tesla",
                        "publishedAt": "2026-10-10T23:59:59Z",
                        "thumbnails": {}
                      }
                    }
                  ]
                }
                """));
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "items": [
                    {
                      "id": {"videoId": "lmnopqrstuv"},
                      "snippet": {
                        "title": "다음 페이지 공식 영상",
                        "channelTitle": "Tesla",
                        "publishedAt": "2026-10-12T01:00:00Z",
                        "thumbnails": {}
                      }
                    }
                  ]
                }
                """));
    YouTubeClient client = newClient("test-api-key");

    var candidates =
        client.searchProfile(
            "https://www.youtube.com/@Tesla",
            java.time.Instant.parse("2026-10-11T00:00:00Z"),
            java.time.Instant.parse("2026-10-13T00:00:00Z"));

    assertThat(candidates)
        .extracting(YouTubeVideoCandidate::title)
        .containsExactly("공식 채널 영상", "다음 페이지 공식 영상");
    RecordedRequest channelRequest = server.takeRequest();
    assertThat(channelRequest.getRequestUrl().queryParameter("forHandle")).isEqualTo("Tesla");
    RecordedRequest videoRequest = server.takeRequest();
    assertThat(videoRequest.getRequestUrl().queryParameter("channelId")).isEqualTo("channel-123");
    assertThat(videoRequest.getRequestUrl().queryParameter("q")).isNull();
    assertThat(videoRequest.getRequestUrl().queryParameter("publishedAfter"))
        .isEqualTo("2026-10-11T00:00:00Z");
    assertThat(videoRequest.getRequestUrl().queryParameter("publishedBefore"))
        .isEqualTo("2026-10-13T00:00:00Z");
    assertThat(videoRequest.getRequestUrl().queryParameter("maxResults")).isEqualTo("50");
    RecordedRequest nextPageRequest = server.takeRequest();
    assertThat(nextPageRequest.getRequestUrl().queryParameter("pageToken")).isEqualTo("next-page");
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
