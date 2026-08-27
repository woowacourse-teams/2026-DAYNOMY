package org.grit.daynomy.external.dart;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "external.dart")
public record DartProperties(
    String apiKey, String baseUrl, Duration connectTimeout, Duration readTimeout) {

  @ConstructorBinding
  public DartProperties {}

  public DartProperties(String apiKey, String baseUrl) {
    this(apiKey, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(30));
  }
}
