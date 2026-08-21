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
    String prompt) {}
