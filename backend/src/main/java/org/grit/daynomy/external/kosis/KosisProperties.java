package org.grit.daynomy.external.kosis;

import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.kosis")
public record KosisProperties(String apiKey, String baseUrl, List<Indicator> indicators) {

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
