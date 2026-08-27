package org.grit.daynomy.news.ai;

import java.time.Instant;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSource;

public record NewsPrompt(
    NewsSource source,
    String externalId,
    String sourceUrl,
    Category category,
    Instant publishedAt,
    String instruction,
    String sourceData) {

  public NewsPrompt(
      NewsSource source,
      String externalId,
      String sourceUrl,
      Category category,
      Instant publishedAt,
      String prompt) {
    this(source, externalId, sourceUrl, category, publishedAt, prompt, "");
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
}
