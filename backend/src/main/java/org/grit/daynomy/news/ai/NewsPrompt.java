package org.grit.daynomy.news.ai;

import java.time.Instant;
import org.grit.daynomy.news.domain.Category;

public record NewsPrompt(
    String sourceName,
    String externalId,
    String sourceUrl,
    Category category,
    Instant publishedAt,
    String instruction,
    String sourceData) {

  public NewsPrompt(
      String sourceName,
      String externalId,
      String sourceUrl,
      Category category,
      Instant publishedAt,
      String prompt) {
    this(sourceName, externalId, sourceUrl, category, publishedAt, prompt, "");
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
