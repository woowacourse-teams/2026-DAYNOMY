package org.grit.daynomy.news.ai;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSourceInfo;

public record NewsPrompt(
    List<NewsSourceInfo> sources,
    Category category,
    Instant publishedAt,
    String instruction,
    String sourceData) {

  public NewsPrompt(
      List<NewsSourceInfo> sources, Category category, Instant publishedAt, String prompt) {
    this(sources, category, publishedAt, prompt, "");
  }

  public String prompt() {
    if (sourceData == null || sourceData.isBlank()) {
      return instruction;
    }
    if (instruction == null || instruction.isBlank()) {
      return sourceData;
    }
    return instruction + "\n\n" + sourceData;
  }

  public boolean hasStructuredInput() {
    return sourceData != null && !sourceData.isBlank();
  }

  public List<String> sourceNames() {
    return sources.stream().map(NewsSourceInfo::name).toList();
  }
}
