package org.grit.daynomy.news.ai;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSourceInfo;

public record NewsGenerationTopicResearch(
    String topicKey,
    String title,
    LocalDate referenceDate,
    Category category,
    List<String> facts,
    List<NewsSourceInfo> sources) {

  public NewsGenerationTopicResearch {
    facts = List.copyOf(facts);
    sources = List.copyOf(sources);
  }
}
