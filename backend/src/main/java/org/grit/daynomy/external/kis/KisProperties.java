package org.grit.daynomy.external.kis;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.kis")
public record KisProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
  public KisProperties {
    if (baseUrl == null) baseUrl = "https://new.real.download.dws.co.kr/common/master";
    if (connectTimeout == null) connectTimeout = Duration.ofSeconds(3);
    if (readTimeout == null) readTimeout = Duration.ofSeconds(30);
  }
}
