package org.grit.daynomy.news.dto;

import java.time.Instant;
import java.time.LocalDate;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsFeedSource;
import org.grit.daynomy.search.dto.ContentSearchItemResponse;

public record NewsFeedItemResponse(
    String kind,
    Long id,
    NewsFeedSource source,
    String title,
    Instant date,
    String url,
    Long personId,
    String personName,
    Long assetId,
    String imageUrl,
    Category category,
    LocalDate publishedDate) {

  public static NewsFeedItemResponse from(ContentSearchItemResponse item) {
    return new NewsFeedItemResponse(
        item.kind(),
        item.id(),
        NewsFeedSource.valueOf(item.source().name()),
        item.title(),
        item.date(),
        item.url(),
        item.personId(),
        item.personName(),
        item.assetId(),
        item.imageUrl(),
        item.category(),
        item.publishedDate());
  }
}
