package org.grit.daynomy.news.ai;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSource;

public record NewsPrompt(
    NewsSource source,
    String externalId,
    String sourceUrl,
    Category category,
    Instant publishedAt,
    String prompt) {

  public NewsPrompt(
      NewsSource source,
      String externalId,
      String sourceUrl,
      Category category,
      LocalDateTime publishedAt,
      String prompt) {
    this(
        source,
        externalId,
        sourceUrl,
        category,
        publishedAt.atZone(ZoneId.systemDefault()).toInstant(),
        prompt);
  }
}
