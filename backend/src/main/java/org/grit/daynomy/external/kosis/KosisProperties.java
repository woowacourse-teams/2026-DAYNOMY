package org.grit.daynomy.external.kosis;

import java.time.Duration;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "external.kosis")
public record KosisProperties(
    String apiKey,
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    List<Indicator> indicators) {

  @ConstructorBinding
  public KosisProperties {}

  public KosisProperties(String apiKey, String baseUrl, List<Indicator> indicators) {
    this(apiKey, baseUrl, Duration.ofSeconds(3), Duration.ofSeconds(30), indicators);
  }

  public record Indicator(
      String key,
      String name,
      String viewCode,
      String userStatsId,
      String period,
      Category category,
      String sourceOrganization,
      String surveyName,
      String tableName) {}
}
