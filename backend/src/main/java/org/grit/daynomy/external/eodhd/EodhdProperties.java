package org.grit.daynomy.external.eodhd;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.eodhd")
public record EodhdProperties(
    String apiToken, String baseUrl, Duration connectTimeout, Duration readTimeout) {
  public EodhdProperties {
    if (baseUrl == null) baseUrl = "https://eodhd.com/api";
    if (connectTimeout == null) connectTimeout = Duration.ofSeconds(3);
    if (readTimeout == null) readTimeout = Duration.ofSeconds(60);
  }

  @Override
  public String toString() {
    return "EodhdProperties[apiToken=REDACTED]";
  }
}
