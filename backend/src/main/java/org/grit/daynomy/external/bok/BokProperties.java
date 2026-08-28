package org.grit.daynomy.external.bok;

import java.time.Duration;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "external.bok")
public record BokProperties(
    String apiKey,
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    List<Indicator> indicators) {

  @ConstructorBinding
  public BokProperties {}

  public BokProperties(String apiKey, String baseUrl, List<Indicator> indicators) {
    this(apiKey, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(30), indicators);
  }

  public record Indicator(
      String key,
      String name,
      String statisticCode,
      String cycle,
      Category category,
      String sourceOrganization,
      String statisticName,
      String itemName,
      List<String> itemCodes) {}
}
