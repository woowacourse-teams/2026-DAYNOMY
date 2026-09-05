package org.grit.daynomy.external.publicdata;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.public-data")
public record PublicDataProperties(
    String serviceKey, String stockPriceUrl, Duration connectTimeout, Duration readTimeout) {

  public PublicDataProperties {
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(3);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(10);
    }
  }
}
