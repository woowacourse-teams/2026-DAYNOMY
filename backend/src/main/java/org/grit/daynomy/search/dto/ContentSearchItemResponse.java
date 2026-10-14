package org.grit.daynomy.search.dto;

import java.time.Instant;
import java.time.LocalDate;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.search.domain.ContentSearchSource;

public record ContentSearchItemResponse(
    String kind,
    Long id,
    ContentSearchSource source,
    String title,
    Instant date,
    String url,
    Long personId,
    String personName,
    Long assetId,
    String imageUrl,
    Category category,
    LocalDate publishedDate) {}
