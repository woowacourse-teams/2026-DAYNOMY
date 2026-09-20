package org.grit.daynomy.news.ai;

import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSourceInfo;

public record GeneratedEconomicNews(
    String title, String content, Category category, List<NewsSourceInfo> sources) {

  public GeneratedEconomicNews {
    sources = List.copyOf(sources);
  }
}
