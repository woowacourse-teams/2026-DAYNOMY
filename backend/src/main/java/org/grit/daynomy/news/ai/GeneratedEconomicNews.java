package org.grit.daynomy.news.ai;

import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSourceInfo;

public record GeneratedEconomicNews(
    String topicKey,
    String title,
    String content,
    Category category,
    List<NewsSourceInfo> sources,
    List<String> industryCodes) {

  public GeneratedEconomicNews {
    sources = List.copyOf(sources);
    industryCodes = List.copyOf(industryCodes);
  }

  public GeneratedEconomicNews(
      String topicKey,
      String title,
      String content,
      Category category,
      List<NewsSourceInfo> sources) {
    this(topicKey, title, content, category, sources, List.of());
  }
}
