package org.grit.daynomy.external.finlife;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.finlife")
public record FinlifeProperties(
    String apiKey,
    String baseUrl,
    String bankGroupCode,
    Duration connectTimeout,
    Duration readTimeout) {

  public FinlifeProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://finlife.fss.or.kr/finlifeapi";
    }
    if (bankGroupCode == null || bankGroupCode.isBlank()) {
      bankGroupCode = "020000";
    }
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(3);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(10);
    }
  }
}
