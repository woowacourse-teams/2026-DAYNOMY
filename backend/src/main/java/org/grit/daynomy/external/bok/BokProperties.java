package org.grit.daynomy.external.bok;

import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.bok")
public record BokProperties(String apiKey, String baseUrl, List<Indicator> indicators) {

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
