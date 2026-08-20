package org.grit.daynomy.news.ai;

import java.time.LocalDateTime;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsSource;

public record NewsPrompt(
    NewsSource source,
    String externalId,
    String sourceUrl,
    Category category,
    LocalDateTime publishedAt,
    String prompt) {}
