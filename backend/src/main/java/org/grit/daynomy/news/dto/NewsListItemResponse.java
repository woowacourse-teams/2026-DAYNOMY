package org.grit.daynomy.news.dto;

import java.time.LocalDateTime;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;

public record NewsListItemResponse(
    Long id,
    String title,
    String description,
    String imageUrl,
    Category category,
    LocalDateTime publishedAt) {

  public static NewsListItemResponse from(News news) {
    return new NewsListItemResponse(
        news.getId(),
        news.getTitle(),
        news.getDescription(),
        news.getImageUrl(),
        news.getCategory(),
        news.getPublishedAt());
  }
}
