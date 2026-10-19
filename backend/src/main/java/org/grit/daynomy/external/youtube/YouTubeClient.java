package org.grit.daynomy.external.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class YouTubeClient {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int SEARCH_LIMIT = 10;
  private static final int PROFILE_SEARCH_LIMIT = 50;
  private static final int MAX_PROFILE_SEARCH_PAGES = 10;

  private final YouTubeProperties properties;
  private final RestClient restClient;

  public YouTubeClient(YouTubeProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(toMillis(properties.connectTimeout()));
    requestFactory.setReadTimeout(toMillis(properties.readTimeout()));
    this.restClient =
        RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
  }

  public List<YouTubeVideoCandidate> search(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return List.of();
    }
    if (properties.apiKey() == null || properties.apiKey().isBlank()) {
      throw new BusinessException(ExternalErrorCode.YOUTUBE_API_NOT_CONFIGURED);
    }

    long startedAt = System.nanoTime();
    logRequest("searchVideos");
    try {
      String response =
          restClient
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/search")
                          .queryParam("part", "snippet")
                          .queryParam("q", keyword.strip())
                          .queryParam("type", "video")
                          .queryParam("order", "date")
                          .queryParam("maxResults", SEARCH_LIMIT)
                          .queryParam("safeSearch", "moderate")
                          .queryParam("key", properties.apiKey())
                          .build())
              .retrieve()
              .body(String.class);
      List<YouTubeVideoCandidate> candidates = parseCandidates(response);
      logCompleted("searchVideos", candidates.size(), startedAt);
      return candidates;
    } catch (HttpStatusCodeException exception) {
      logFailed("searchVideos", exception.getStatusCode().value(), exception, startedAt);
      throw new BusinessException(ExternalErrorCode.YOUTUBE_API_REQUEST_FAILED);
    } catch (BusinessException exception) {
      logFailed("searchVideos", null, exception, startedAt);
      throw exception;
    } catch (RuntimeException exception) {
      logFailed("searchVideos", null, exception, startedAt);
      throw new BusinessException(ExternalErrorCode.YOUTUBE_API_REQUEST_FAILED);
    }
  }

  public List<YouTubeVideoCandidate> searchProfile(
      String profileUrl, Instant fromInclusive, Instant toExclusive) {
    if (profileUrl == null || profileUrl.isBlank()) {
      return List.of();
    }
    requireApiKey();
    long startedAt = System.nanoTime();
    logRequest("searchProfileVideos");
    try {
      String channelId = resolveChannelId(profileUrl);
      if (channelId == null) {
        log.atWarn()
            .addKeyValue("event", LogEvent.EXTERNAL_API_FAILED.code())
            .addKeyValue("api", "YOUTUBE")
            .addKeyValue("operation", "searchProfileVideos")
            .addKeyValue("reasonCode", "CHANNEL_NOT_FOUND")
            .addKeyValue("durationMs", elapsedMillis(startedAt))
            .log(LogEvent.EXTERNAL_API_FAILED.message());
        return List.of();
      }
      List<YouTubeVideoCandidate> candidates = new ArrayList<>();
      String pageToken = null;
      int pageCount = 0;
      for (int page = 0; page < MAX_PROFILE_SEARCH_PAGES; page++) {
        String currentPageToken = pageToken;
        String response =
            restClient
                .get()
                .uri(
                    builder -> {
                      builder
                          .path("/search")
                          .queryParam("part", "snippet")
                          .queryParam("channelId", channelId)
                          .queryParam("type", "video")
                          .queryParam("order", "date")
                          .queryParam("publishedAfter", fromInclusive.toString())
                          .queryParam("publishedBefore", toExclusive.toString())
                          .queryParam("maxResults", PROFILE_SEARCH_LIMIT)
                          .queryParam("safeSearch", "moderate")
                          .queryParam("key", properties.apiKey());
                      if (currentPageToken != null) {
                        builder.queryParam("pageToken", currentPageToken);
                      }
                      return builder.build();
                    })
                .retrieve()
                .body(String.class);
        pageCount++;
        candidates.addAll(parseCandidates(response));
        pageToken = readTree(response).path("nextPageToken").asText();
        if (pageToken.isBlank()) {
          break;
        }
      }
      List<YouTubeVideoCandidate> filteredCandidates =
          candidates.stream()
              .filter(
                  candidate -> isWithinPeriod(candidate.publishedAt(), fromInclusive, toExclusive))
              .toList();
      log.atInfo()
          .addKeyValue("event", LogEvent.EXTERNAL_API_COMPLETED.code())
          .addKeyValue("api", "YOUTUBE")
          .addKeyValue("operation", "searchProfileVideos")
          .addKeyValue("pageCount", pageCount)
          .addKeyValue("responseCount", filteredCandidates.size())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.EXTERNAL_API_COMPLETED.message());
      return filteredCandidates;
    } catch (HttpStatusCodeException exception) {
      logFailed("searchProfileVideos", exception.getStatusCode().value(), exception, startedAt);
      throw new BusinessException(ExternalErrorCode.YOUTUBE_API_REQUEST_FAILED);
    } catch (BusinessException exception) {
      logFailed("searchProfileVideos", null, exception, startedAt);
      throw exception;
    } catch (RuntimeException exception) {
      logFailed("searchProfileVideos", null, exception, startedAt);
      throw new BusinessException(ExternalErrorCode.YOUTUBE_API_REQUEST_FAILED);
    }
  }

  private boolean isWithinPeriod(String value, Instant fromInclusive, Instant toExclusive) {
    try {
      Instant publishedAt = Instant.parse(value);
      return !publishedAt.isBefore(fromInclusive) && publishedAt.isBefore(toExclusive);
    } catch (DateTimeParseException exception) {
      return false;
    }
  }

  private List<YouTubeVideoCandidate> parseCandidates(String response) {
    try {
      JsonNode items = OBJECT_MAPPER.readTree(response).path("items");
      List<YouTubeVideoCandidate> candidates = new ArrayList<>();
      for (JsonNode item : items) {
        String videoId = item.path("id").path("videoId").asText();
        JsonNode snippet = item.path("snippet");
        String title = snippet.path("title").asText();
        if (videoId.isBlank() || title.isBlank()) {
          continue;
        }
        candidates.add(
            new YouTubeVideoCandidate(
                title,
                "https://www.youtube.com/watch?v=" + videoId,
                snippet.path("channelTitle").asText(""),
                snippet.path("publishedAt").asText(""),
                thumbnailUrl(snippet.path("thumbnails"))));
      }
      return List.copyOf(candidates);
    } catch (Exception exception) {
      throw new BusinessException(ExternalErrorCode.YOUTUBE_API_REQUEST_FAILED);
    }
  }

  private String resolveChannelId(String profileUrl) {
    URI uri = URI.create(profileUrl);
    String[] segments = uri.getPath().split("/");
    if (segments.length >= 3 && "channel".equals(segments[1])) {
      return segments[2];
    }
    if (segments.length < 2) {
      return null;
    }
    String parameter;
    String value;
    if (segments[1].startsWith("@")) {
      parameter = "forHandle";
      value = segments[1].substring(1);
    } else if (segments.length >= 3 && "user".equals(segments[1])) {
      parameter = "forUsername";
      value = segments[2];
    } else {
      return null;
    }
    String response =
        restClient
            .get()
            .uri(
                builder ->
                    builder
                        .path("/channels")
                        .queryParam("part", "id")
                        .queryParam(parameter, value)
                        .queryParam("key", properties.apiKey())
                        .build())
            .retrieve()
            .body(String.class);
    JsonNode items = readTree(response).path("items");
    if (!items.isArray() || items.isEmpty()) {
      return null;
    }
    String channelId = items.get(0).path("id").asText();
    return channelId.isBlank() ? null : channelId;
  }

  private JsonNode readTree(String response) {
    try {
      return OBJECT_MAPPER.readTree(response);
    } catch (Exception exception) {
      throw new BusinessException(ExternalErrorCode.YOUTUBE_API_REQUEST_FAILED);
    }
  }

  private void requireApiKey() {
    if (properties.apiKey() == null || properties.apiKey().isBlank()) {
      throw new BusinessException(ExternalErrorCode.YOUTUBE_API_NOT_CONFIGURED);
    }
  }

  private String thumbnailUrl(JsonNode thumbnails) {
    for (String quality : List.of("high", "medium", "default")) {
      String url = thumbnails.path(quality).path("url").asText();
      if (!url.isBlank()) {
        return url;
      }
    }
    return "";
  }

  private static int toMillis(Duration duration) {
    return Math.toIntExact(duration.toMillis());
  }

  private void logRequest(String operation) {
    log.atDebug()
        .addKeyValue("event", LogEvent.EXTERNAL_API_REQUESTED.code())
        .addKeyValue("api", "YOUTUBE")
        .addKeyValue("operation", operation)
        .log(LogEvent.EXTERNAL_API_REQUESTED.message());
  }

  private void logCompleted(String operation, int responseCount, long startedAt) {
    log.atInfo()
        .addKeyValue("event", LogEvent.EXTERNAL_API_COMPLETED.code())
        .addKeyValue("api", "YOUTUBE")
        .addKeyValue("operation", operation)
        .addKeyValue("responseCount", responseCount)
        .addKeyValue("durationMs", elapsedMillis(startedAt))
        .log(LogEvent.EXTERNAL_API_COMPLETED.message());
  }

  private void logFailed(
      String operation, Integer responseStatus, RuntimeException exception, long startedAt) {
    var event =
        log.atError()
            .addKeyValue("event", LogEvent.EXTERNAL_API_FAILED.code())
            .addKeyValue("api", "YOUTUBE")
            .addKeyValue("operation", operation)
            .addKeyValue("durationMs", elapsedMillis(startedAt))
            .addKeyValue("errorCode", ExternalErrorCode.YOUTUBE_API_REQUEST_FAILED.code())
            .addKeyValue("exceptionType", exception.getClass().getSimpleName());
    if (responseStatus != null) {
      event.addKeyValue("responseStatus", responseStatus);
    }
    event.log(LogEvent.EXTERNAL_API_FAILED.message());
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }
}
