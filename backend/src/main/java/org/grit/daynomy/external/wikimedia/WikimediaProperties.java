package org.grit.daynomy.external.wikimedia;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.wikimedia")
public record WikimediaProperties(
    String baseUrl, String userAgent, Duration connectTimeout, Duration readTimeout) {

  public WikimediaProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(3);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(15);
    }
  }
}
