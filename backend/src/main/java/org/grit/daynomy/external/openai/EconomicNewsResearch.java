package org.grit.daynomy.external.openai;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSourceInfo;

public record EconomicNewsResearch(
    String title,
    LocalDate referenceDate,
    Category category,
    List<String> facts,
    List<NewsSourceInfo> sources) {

  public EconomicNewsResearch {
    facts = List.copyOf(facts);
    sources = List.copyOf(sources);
  }
}
