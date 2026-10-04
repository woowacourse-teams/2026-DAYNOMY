package org.grit.daynomy.external.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class YouTubeClient {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int SEARCH_LIMIT = 10;

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
                          .queryParam("order", "relevance")
                          .queryParam("maxResults", SEARCH_LIMIT)
                          .queryParam("safeSearch", "moderate")
                          .queryParam("key", properties.apiKey())
                          .build())
              .retrieve()
              .body(String.class);
      return parseCandidates(response);
    } catch (BusinessException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      log.warn("YouTube search failed: {}", exception.getClass().getSimpleName());
      throw new BusinessException(ExternalErrorCode.YOUTUBE_API_REQUEST_FAILED);
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
}
