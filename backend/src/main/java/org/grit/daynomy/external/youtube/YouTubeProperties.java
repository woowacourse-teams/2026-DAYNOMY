package org.grit.daynomy.external.youtube;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.youtube")
public record YouTubeProperties(
    String apiKey, String baseUrl, Duration connectTimeout, Duration readTimeout) {

  public YouTubeProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://www.googleapis.com/youtube/v3";
    }
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(3);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(10);
    }
  }
}
